package com.nocklock.securitylens.service;

import com.nocklock.securitylens.analyzer.PiiAnalyzer;
import com.nocklock.securitylens.domain.RiskLevel;
import com.nocklock.securitylens.dto.AnalysisResponse;
import com.nocklock.securitylens.dto.DetectedItem;
import com.nocklock.securitylens.dto.ImageInfo;
import com.nocklock.securitylens.dto.MetadataResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import com.nocklock.securitylens.analyzer.AiPiiAnalyzer;
import java.util.ArrayList;
import java.util.List;

import com.nocklock.securitylens.dto.OcrResult;
import com.nocklock.securitylens.dto.AiPiiDetectionResult;
import com.nocklock.securitylens.domain.PiiType;

import com.nocklock.securitylens.analyzer.DocumentVisionAnalyzer;
import com.nocklock.securitylens.dto.DocumentVisionResult;

import com.nocklock.securitylens.dto.DocumentFieldResult;

@Service
public class ImageAnalysisService {

    private static final Logger log =
            LoggerFactory.getLogger(ImageAnalysisService.class);

    private final MessageSource messageSource;

    private final AiPiiAnalyzer aiPiiAnalyzer;

    private final ImageMetadataService imageMetadataService;
    private final OcrService ocrService;
    private final PiiAnalyzer piiAnalyzer;
    private final RiskScoreCalculator riskScoreCalculator;

    private final BoundingBoxResolver boundingBoxResolver;
    private final DocumentVisionAnalyzer documentVisionAnalyzer;
    private final ImageOrientationNormalizer imageOrientationNormalizer;

    public ImageAnalysisService(
            ImageMetadataService imageMetadataService,
            OcrService ocrService,
            PiiAnalyzer piiAnalyzer,
            AiPiiAnalyzer aiPiiAnalyzer,
            DocumentVisionAnalyzer documentVisionAnalyzer,
            RiskScoreCalculator riskScoreCalculator,
            BoundingBoxResolver boundingBoxResolver,
            ImageOrientationNormalizer imageOrientationNormalizer,
            MessageSource messageSource
    ) {
        this.imageMetadataService = imageMetadataService;
        this.ocrService = ocrService;
        this.piiAnalyzer = piiAnalyzer;
        this.aiPiiAnalyzer = aiPiiAnalyzer;
        this.documentVisionAnalyzer = documentVisionAnalyzer;
        this.riskScoreCalculator = riskScoreCalculator;
        this.boundingBoxResolver = boundingBoxResolver;
        this.imageOrientationNormalizer = imageOrientationNormalizer;
        this.messageSource = messageSource;
    }

    public AnalysisResponse analyzeFast(
            MultipartFile image
    ) {

        long analysisStartTime =
                System.nanoTime();


        log.info(
                "FAST STEP 1 - analysis started"
        );


        ImageInfo imageInfo =
                extractImageInfo(
                        image
                );


        MetadataResult metadataResult =
                imageMetadataService.extract(
                        image
                );


        OcrResult ocrResult =
                ocrService.analyze(
                        image
                );


        String normalizedText =
                normalizeOcrText(
                        ocrResult.text()
                );


        /*
         * FAST 분석에서는 외부 AI를 호출하지 않는다.
         *
         * Local OCR + Rule 기반 개인정보만 사용한다.
         */
        List<DetectedItem> detectedItems =
                new ArrayList<>(
                        piiAnalyzer.analyze(
                                normalizedText
                        )
                );


        /*
         * Rule 기반으로 발견한 개인정보를
         * OCR 좌표와 연결한다.
         */
        detectedItems =
                boundingBoxResolver.attach(
                        detectedItems,
                        ocrResult.words()
                );


        int riskScore =
                riskScoreCalculator.calculate(
                        detectedItems,
                        metadataResult
                );


        RiskLevel riskLevel =
                riskScoreCalculator.determineLevel(
                        riskScore
                );


        long totalDurationMs =
                (System.nanoTime()
                        - analysisStartTime)
                        / 1_000_000;


        log.info(
                "FAST analysis completed. detectedCount={}, riskScore={}, riskLevel={}, durationMs={}",
                detectedItems.size(),
                riskScore,
                riskLevel,
                totalDurationMs
        );


        return new AnalysisResponse(
                riskScore,
                riskLevel,
                imageInfo,
                detectedItems,
                metadataResult,
                List.of(),
                createSummary(
                        riskLevel
                )
        );
    }

    /*
     * DEEP 분석
     *
     * FAST 이후 실행되는 정밀 분석.
     * Text AI / Document Vision 포함.
     */
    public AnalysisResponse analyze(MultipartFile image) {

        //전체시간측정
        long analysisStartTime = System.nanoTime();

        log.info("STEP 1 - analysis started");

        ImageInfo imageInfo =
                extractImageInfo(image);

        log.info("STEP 2 - image info completed");

        MetadataResult metadataResult =
                imageMetadataService.extract(image);

        log.info("STEP 3 - metadata completed");

        OcrResult ocrResult =
                ocrService.analyze(image);

        log.info("STEP 4 - OCR completed");

        String extractedText =
                ocrResult.text();

        log.info(
                "OCR word count={}",
                ocrResult.words().size()
        );

        String normalizedText =
                normalizeOcrText(extractedText);

        long digitCount =
                normalizedText.chars()
                        .filter(Character::isDigit)
                        .count();

        String compactText =
                normalizedText.replaceAll(
                        "\\s+",
                        ""
                );

        boolean hasDriverLicenseKeyword =
                compactText.contains("운전면허");

        boolean hasLooseDriverLicenseNumber =
                java.util.regex.Pattern.compile(
                                "(?<!\\d)\\d{2}[-\\s.:·]*\\d{2}[-\\s.:·]*\\d{6}[-\\s.:·]*\\d{2}(?!\\d)"
                        )
                        .matcher(normalizedText)
                        .find();

        List<DetectedItem> detectedItems =
                new ArrayList<>(
                        piiAnalyzer.analyze(normalizedText)
                );

        log.info("STEP 5 - rule based PII completed");

        boolean lowQualityOcr =
                ocrService.isLowQuality(
                        ocrResult
                );


        log.info(
                "OCR quality evaluated. lowQuality={}, wordCount={}",
                lowQualityOcr,
                ocrResult.words().size()
        );

        long aiStartTime =
                System.nanoTime();


        AiPiiDetectionResult aiResult =
                AiPiiDetectionResult.empty();


        if (!lowQualityOcr) {

            log.info(
                    "STEP 6 - AI text analysis started"
            );


            aiResult =
                    aiPiiAnalyzer.analyze(
                            normalizedText,
                            ocrResult.words(),
                            detectedItems.size() + 1
                    );


            log.info(
                    "STEP 7 - AI text analysis finished"
            );

        } else {

            log.info(
                    "STEP 6 - AI text analysis skipped. reason=LOW_OCR_QUALITY"
            );
        }


        boolean aiDetectedAddress =
                aiResult.detectedItems()
                        .stream()
                        .anyMatch(
                                item ->
                                        item.type()
                                                == PiiType.ADDRESS
                        );


        /*
         * AI가 문맥 기반 주소를 찾았다면
         * 기존 한국 주소 Regex 결과 대신 AI 주소를 사용.
         *
         * AI가 실패하면 기존 Regex 주소가 그대로 fallback.
         */
        if (aiDetectedAddress) {

            detectedItems.removeIf(
                    item ->
                            item.type()
                                    == PiiType.ADDRESS
            );
        }


        detectedItems.addAll(
                aiResult.detectedItems()
        );

        //AI 검증 필요여부
        boolean needsDocumentVision =
                lowQualityOcr
                        ||
                        detectedItems.isEmpty();

        //시간측정
        long aiDurationMs =
                (System.nanoTime() - aiStartTime)
                        / 1_000_000;


        log.info(
                "AI text analysis phase completed. skipped={}, durationMs={}",
                lowQualityOcr,
                aiDurationMs
        );

        detectedItems =
                boundingBoxResolver.attach(
                        detectedItems,
                        ocrResult.words(),
                        aiResult.wordIdsById()
                );
        if (needsDocumentVision) {

            long documentVisionStart =
                    System.nanoTime();


            log.info(
                    "STEP 8 - document vision fallback started"
            );


            DocumentVisionResult documentResult =
                    documentVisionAnalyzer.analyze(
                            image
                    );


            if (
                    documentResult.detected()
                            &&
                            documentResult.boundingBox() != null
            ) {

                int documentFieldIndex =
                        1;


                for (
                        DocumentFieldResult field
                        : documentResult.fields()
                ) {

                    PiiType piiType;


                    try {

                        piiType =
                                PiiType.valueOf(
                                        field.type()
                                );

                    } catch (IllegalArgumentException e) {

                        continue;
                    }


                    detectedItems.add(
                            new DetectedItem(
                                    "document-field-"
                                            + documentFieldIndex++,

                                    piiType,

                                    createDocumentFieldMaskedValue(
                                            piiType
                                    ),

                                    field.confidence(),

                                    field.boundingBox()
                            )
                    );
                }


                log.info(
                        "Document field detection completed. type={}, fieldCount={}",
                        documentResult.documentType(),
                        documentResult.fields().size()
                );


                /*
                 * 문서 자체도 별도 항목으로 유지한다.
                 * 세부 field가 실패해도 문서 전체 보호 가능.
                 */
                detectedItems.add(
                        new DetectedItem(
                                "document-vision-1",
                                PiiType.SENSITIVE_DOCUMENT,
                                documentResult.documentType(),
                                documentResult.confidence(),
                                documentResult.boundingBox()
                        )
                );
            }


            log.info(
                    "Document vision analysis completed. durationMs={}",
                    (System.nanoTime() - documentVisionStart)
                            / 1_000_000
            );
        }

        detectedItems.forEach(item ->
                log.debug(
                        "Resolved bounding box. type={}, box={}",
                        item.type(),
                        item.boundingBox()
                )
        );

        int riskScore =
                riskScoreCalculator.calculate(
                        detectedItems,
                        metadataResult
                );

        RiskLevel riskLevel =
                riskScoreCalculator.determineLevel(riskScore);

        log.debug(
                "OCR analysis completed. textLength={}",
                extractedText.length()
        );

        long totalDurationMs =
                (System.nanoTime() - analysisStartTime) / 1_000_000;

        log.info(
                "Image analysis completed. detectedCount={}, riskScore={}, riskLevel={}, durationMs={}",
                detectedItems.size(),
                riskScore,
                riskLevel,
                totalDurationMs
        );

        return new AnalysisResponse(
                riskScore,
                riskLevel,
                imageInfo,
                detectedItems,
                metadataResult,
                List.of(),
                createSummary(riskLevel)
        );
    }

    private String createDocumentFieldMaskedValue(
            PiiType type
    ) {

        return switch (type) {

            case PERSON_NAME ->
                    "NAME_DETECTED";

            case RRN ->
                    "RRN_DETECTED";

            case DRIVER_LICENSE_NUMBER ->
                    "DRIVER_LICENSE_NUMBER_DETECTED";

            case ADDRESS ->
                    "ADDRESS_DETECTED";

            case PASSPORT_NUMBER ->
                    "PASSPORT_NUMBER_DETECTED";

            case DATE_OF_BIRTH ->
                    "DATE_OF_BIRTH_DETECTED";

            default ->
                    "DETECTED";
        };
    }

    private String normalizeOcrText(String text) {

        if (text == null || text.isBlank()) {
            return "";
        }

        return text

                // OCR에서 발생할 수 있는 다양한 dash를 일반 '-'로 통일
                .replace('–', '-')
                .replace('—', '-')

                // 숫자 사이의 가로 공백 제거
                // "1  1-09" -> "11-09"
                .replaceAll(
                        "(?<=\\d)[ \\t]+(?=\\d)",
                        ""
                )

                // 하이픈 주변 가로 공백 제거
                .replaceAll(
                        "[ \\t]*-[ \\t]*",
                        "-"
                )

                // 여러 개의 가로 공백 정리
                .replaceAll(
                        "[ \\t]{2,}",
                        " "
                )

                .trim();
    }

    private String createSummary(RiskLevel riskLevel) {
        String messageKey = switch (riskLevel) {
            case LOW -> "analysis.summary.low";
            case MEDIUM -> "analysis.summary.medium";
            case HIGH -> "analysis.summary.high";
            case CRITICAL -> "analysis.summary.critical";
        };

        return getMessage(messageKey);
    }

    private String getMessage(String messageKey) {
        return messageSource.getMessage(
                messageKey,
                null,
                LocaleContextHolder.getLocale()
        );
    }

    private ImageInfo extractImageInfo(
            MultipartFile image
    ) {

        try {

            BufferedImage bufferedImage =
                    imageOrientationNormalizer
                            .readNormalized(
                                    image
                            );


            if (bufferedImage == null) {

                throw new IllegalArgumentException(
                        getMessage(
                                "image.info.unavailable"
                        )
                );
            }


            return new ImageInfo(
                    bufferedImage.getWidth(),
                    bufferedImage.getHeight()
            );


        } catch (IOException e) {

            throw new IllegalStateException(
                    getMessage(
                            "image.info.read-failed"
                    ),
                    e
            );
        }
    }
}