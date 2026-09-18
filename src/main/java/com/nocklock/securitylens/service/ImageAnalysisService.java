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

    public ImageAnalysisService(
            ImageMetadataService imageMetadataService,
            OcrService ocrService,
            PiiAnalyzer piiAnalyzer,
            AiPiiAnalyzer aiPiiAnalyzer,
            RiskScoreCalculator riskScoreCalculator,
            BoundingBoxResolver boundingBoxResolver,
            MessageSource messageSource
    ) {
        this.imageMetadataService = imageMetadataService;
        this.ocrService = ocrService;
        this.piiAnalyzer = piiAnalyzer;
        this.aiPiiAnalyzer = aiPiiAnalyzer;
        this.riskScoreCalculator = riskScoreCalculator;
        this.boundingBoxResolver = boundingBoxResolver;
        this.messageSource = messageSource;
    }

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

        long aiStartTime =
                System.nanoTime();

        log.info("STEP 6 - AI analysis started");

        AiPiiDetectionResult aiResult =
                aiPiiAnalyzer.analyze(
                        normalizedText,
                        ocrResult.words(),
                        detectedItems.size() + 1
                );

        log.info("STEP 7 - AI analysis finished");


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

        //시간측정
        long aiDurationMs =
                (System.nanoTime() - aiStartTime) / 1_000_000;

        log.info(
                "AI PII analysis completed. durationMs={}",
                aiDurationMs
        );

        detectedItems =
                boundingBoxResolver.attach(
                        detectedItems,
                        ocrResult.words(),
                        aiResult.wordIdsById()
                );

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

    private ImageInfo extractImageInfo(MultipartFile image) {
        try {
            BufferedImage bufferedImage =
                    ImageIO.read(image.getInputStream());

            if (bufferedImage == null) {
                throw new IllegalArgumentException(
                        getMessage("image.info.unavailable")
                );
            }

            return new ImageInfo(
                    bufferedImage.getWidth(),
                    bufferedImage.getHeight()
            );

        } catch (IOException e) {
            throw new IllegalStateException(
                    getMessage("image.info.read-failed"),
                    e
            );
        }
    }
}