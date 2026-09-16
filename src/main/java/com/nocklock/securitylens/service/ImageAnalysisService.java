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

    public ImageAnalysisService(
            ImageMetadataService imageMetadataService,
            OcrService ocrService,
            PiiAnalyzer piiAnalyzer,
            AiPiiAnalyzer aiPiiAnalyzer,
            RiskScoreCalculator riskScoreCalculator,
            MessageSource messageSource
    ) {
        this.imageMetadataService = imageMetadataService;
        this.ocrService = ocrService;
        this.piiAnalyzer = piiAnalyzer;
        this.aiPiiAnalyzer = aiPiiAnalyzer;
        this.riskScoreCalculator = riskScoreCalculator;
        this.messageSource = messageSource;
    }

    public AnalysisResponse analyze(MultipartFile image) {

        //전체시간측정
        long analysisStartTime = System.nanoTime();

        ImageInfo imageInfo =
                extractImageInfo(image);

        MetadataResult metadataResult =
                imageMetadataService.extract(image);

        String extractedText =
                ocrService.extractText(image);

        List<DetectedItem> detectedItems =
                new ArrayList<>(piiAnalyzer.analyze(extractedText));

        //시간측정
        long aiStartTime = System.nanoTime();


        detectedItems.addAll(
                aiPiiAnalyzer.analyze(
                        extractedText,
                        detectedItems.size() + 1
                )
        );

        //시간층정
        long aiDurationMs =
                (System.nanoTime() - aiStartTime) / 1_000_000;

        log.info(
                "AI PII analysis completed. durationMs={}",
                aiDurationMs
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