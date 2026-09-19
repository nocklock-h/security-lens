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

    private final FastAnalysisCache fastAnalysisCache;

    public ImageAnalysisService(
            ImageMetadataService imageMetadataService,
            OcrService ocrService,
            PiiAnalyzer piiAnalyzer,
            AiPiiAnalyzer aiPiiAnalyzer,
            DocumentVisionAnalyzer documentVisionAnalyzer,
            RiskScoreCalculator riskScoreCalculator,
            BoundingBoxResolver boundingBoxResolver,
            ImageOrientationNormalizer imageOrientationNormalizer,
            MessageSource messageSource,
            FastAnalysisCache fastAnalysisCache
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
        this.fastAnalysisCache = fastAnalysisCache;
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

        String analysisCacheKey =
                fastAnalysisCache.createKey(
                        image
                );


        fastAnalysisCache.put(
                analysisCacheKey,
                imageInfo,
                metadataResult,
                ocrResult
        );


        log.info(
                "FAST analysis context cached."
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
                        riskLevel,
                        detectedItems,
                        metadataResult
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

        String analysisCacheKey =
                fastAnalysisCache.createKey(
                        image
                );


        FastAnalysisCache.CachedAnalysis cached =
                fastAnalysisCache.take(
                        analysisCacheKey
                );


        ImageInfo imageInfo;

        MetadataResult metadataResult;

        OcrResult ocrResult;


        if (cached != null) {

            imageInfo =
                    cached.imageInfo();

            metadataResult =
                    cached.metadata();

            ocrResult =
                    cached.ocrResult();


            log.info(
                    "STEP 2-4 - FAST analysis context reused. OCR skipped."
            );


        } else {

            /*
             * FAST 없이 /analyze/deep만 직접 호출했거나
             * 캐시 TTL이 지난 경우에도 기존처럼 정상 동작한다.
             */
            log.info(
                    "FAST analysis cache miss. Running full analysis."
            );


            imageInfo =
                    extractImageInfo(
                            image
                    );

            log.info(
                    "STEP 2 - image info completed"
            );


            metadataResult =
                    imageMetadataService.extract(
                            image
                    );

            log.info(
                    "STEP 3 - metadata completed"
            );


            ocrResult =
                    ocrService.analyze(
                            image
                    );

            log.info(
                    "STEP 4 - OCR completed"
            );
        }

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

        // Document Vision 기본 fallback 조건
        boolean baseDocumentVisionNeeded =
                lowQualityOcr
                        ||
                        detectedItems.isEmpty();


// 이미 이름이 탐지됐는지 확인
        boolean hasPersonName =
                detectedItems.stream()
                        .anyMatch(
                                item ->
                                        item.type()
                                                == PiiType.PERSON_NAME
                        );


// OCR 텍스트에 "이름:", "성명:" 같은 명확한 라벨이 있는지 확인
        boolean hasExplicitNameLabel =
                java.util.regex.Pattern.compile(
                                "(?m)(?:^|\\s)(?:이름|성명)\\s*[:：]?"
                        )
                        .matcher(normalizedText)
                        .find();

// 이름이 누락되었고 명시적 이름 필드 또는
// 복수의 개인정보 신호가 있는 경우 Vision으로 보완
        boolean hasPhone =
                detectedItems.stream()
                        .anyMatch(
                                item ->
                                        item.type()
                                                == PiiType.PHONE
                        );


        boolean hasEmail =
                detectedItems.stream()
                        .anyMatch(
                                item ->
                                        item.type()
                                                == PiiType.EMAIL
                        );


        boolean hasAddress =
                detectedItems.stream()
                        .anyMatch(
                                item ->
                                        item.type()
                                                == PiiType.ADDRESS
                        );


        boolean likelyPersonalDocument =
                (hasPhone && hasEmail)
                        ||
                        (hasPhone && hasAddress)
                        ||
                        (hasEmail && hasAddress);


        boolean nameVisionNeeded =
                !hasPersonName
                        &&
                        (
                                hasExplicitNameLabel
                                        ||
                                        likelyPersonalDocument
                        );


        boolean needsDocumentVision =
                baseDocumentVisionNeeded
                        ||
                        nameVisionNeeded;

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


                    boolean alreadyDetected =
                            detectedItems.stream()
                                    .anyMatch(
                                            item ->
                                                    item.type()
                                                            == piiType
                                    );


                    if (alreadyDetected) {

                        log.debug(
                                "Document vision field skipped because type already exists. type={}",
                                piiType
                        );

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
                 * 기존 fallback 조건으로 Vision을 실행한 경우에만
                 * 문서 자체도 별도 항목으로 유지한다.
                 *
                 * 이름 누락 보완용 Vision에서는
                 * SENSITIVE_DOCUMENT 전체 항목을 추가하지 않는다.
                 */
                if (baseDocumentVisionNeeded) {

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
                createSummary(
                        riskLevel,
                        detectedItems,
                        metadataResult
                )
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

    private String createSummary(
            RiskLevel riskLevel,
            List<DetectedItem> detectedItems,
            MetadataResult metadataResult
    ) {

        List<String> summaries =
                new ArrayList<>();


        /*
         * 1. 민감 문서 맥락
         */
        DetectedItem documentItem =
                detectedItems
                        .stream()
                        .filter(
                                item ->
                                        item.type()
                                                == PiiType.SENSITIVE_DOCUMENT
                        )
                        .findFirst()
                        .orElse(null);


        if (documentItem != null) {

            String documentType =
                    documentItem.maskedValue() == null
                            ? ""
                            : documentItem.maskedValue()
                            .toUpperCase();


            switch (documentType) {

                case "DRIVER_LICENSE" ->
                        summaries.add(
                                getMessage(
                                        "analysis.summary.document.driverLicense"
                                )
                        );

                case "PASSPORT" ->
                        summaries.add(
                                getMessage(
                                        "analysis.summary.document.passport"
                                )
                        );

                default ->
                        summaries.add(
                                getMessage(
                                        "analysis.summary.document.sensitive"
                                )
                        );
            }
        }


        /*
         * 2. 화면에서 발견된 개인정보 종류
         */
        List<String> piiLabels =
                detectedItems
                        .stream()

                        .filter(
                                item ->
                                        item.type()
                                                != PiiType.SENSITIVE_DOCUMENT
                        )

                        .map(
                                DetectedItem::type
                        )

                        .distinct()

                        .map(
                                this::getPiiSummaryLabel
                        )

                        .filter(
                                label ->
                                        label != null
                                                && !label.isBlank()
                        )

                        .toList();


        if (!piiLabels.isEmpty()) {

            summaries.add(
                    getMessage(
                            "analysis.summary.detectedItems",
                            String.join(
                                    " · ",
                                    piiLabels
                            )
                    )
            );
        }


        /*
         * 3. 숨겨진 메타데이터
         */
        List<String> metadataLabels =
                new ArrayList<>();


        if (
                metadataResult != null
                        &&
                        metadataResult.gps() != null
                        &&
                        metadataResult.gps().detected()
        ) {

            metadataLabels.add(
                    getMessage(
                            "ui.metadata.location"
                    )
            );
        }


        if (
                metadataResult != null
                        &&
                        metadataResult.capturedAt() != null
                        &&
                        !metadataResult.capturedAt().isBlank()
        ) {

            metadataLabels.add(
                    getMessage(
                            "ui.metadata.capturedAt"
                    )
            );
        }


        if (
                metadataResult != null
                        &&
                        metadataResult.device() != null
                        &&
                        metadataResult.device().detected()
        ) {

            metadataLabels.add(
                    getMessage(
                            "ui.metadata.device"
                    )
            );
        }


        if (!metadataLabels.isEmpty()) {

            summaries.add(
                    getMessage(
                            "analysis.summary.metadataDetected",
                            String.join(
                                    " · ",
                                    metadataLabels
                            )
                    )
            );
        }


        /*
         * 구체적인 노출 요소가 발견됐다면
         * 사용자 행동까지 안내한다.
         */
        if (!summaries.isEmpty()) {

            summaries.add(
                    getMessage(
                            "analysis.summary.protectRecommended"
                    )
            );

            return String.join(
                    " ",
                    summaries
            );
        }


        /*
         * 구체적인 개인정보가 없을 경우
         * 기존 RiskLevel 기반 설명을 fallback으로 사용한다.
         */
        return createRiskSummary(
                riskLevel
        );
    }


    private String getPiiSummaryLabel(
            PiiType type
    ) {

        if (type == null) {
            return null;
        }


        return switch (type) {

            case PHONE ->
                    getMessage(
                            "ui.pii.phone"
                    );

            case EMAIL ->
                    getMessage(
                            "ui.pii.email"
                    );

            case PERSON_NAME ->
                    getMessage(
                            "ui.pii.personName"
                    );

            case ADDRESS ->
                    getMessage(
                            "ui.pii.address"
                    );

            case RRN ->
                    getMessage(
                            "ui.pii.rrn"
                    );

            case DRIVER_LICENSE_NUMBER ->
                    getMessage(
                            "ui.pii.driverLicenseNumber"
                    );

            case PASSPORT_NUMBER ->
                    getMessage(
                            "ui.pii.passportNumber"
                    );

            case DATE_OF_BIRTH ->
                    getMessage(
                            "ui.pii.dateOfBirth"
                    );

            default ->
                    null;
        };
    }


    private String createRiskSummary(
            RiskLevel riskLevel
    ) {

        String messageKey =
                switch (riskLevel) {

                    case LOW ->
                            "analysis.summary.low";

                    case MEDIUM ->
                            "analysis.summary.medium";

                    case HIGH ->
                            "analysis.summary.high";

                    case CRITICAL ->
                            "analysis.summary.critical";
                };


        return getMessage(
                messageKey
        );
    }


    private String getMessage(
            String messageKey,
            Object... arguments
    ) {

        return messageSource.getMessage(
                messageKey,
                arguments,
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