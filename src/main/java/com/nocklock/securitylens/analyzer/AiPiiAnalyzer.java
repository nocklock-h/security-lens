package com.nocklock.securitylens.analyzer;

import tools.jackson.databind.json.JsonMapper;
import com.nocklock.securitylens.config.AiProperties;
import com.nocklock.securitylens.domain.PiiType;
import com.nocklock.securitylens.dto.AiAnalysisResponse;
import com.nocklock.securitylens.dto.AiDetectedItem;
import com.nocklock.securitylens.dto.DetectedItem;
import com.nocklock.securitylens.dto.OllamaGenerateRequest;
import com.nocklock.securitylens.dto.OllamaGenerateResponse;
import com.nocklock.securitylens.service.AiResultValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Component
public class AiPiiAnalyzer {

    private static final Logger log =
            LoggerFactory.getLogger(AiPiiAnalyzer.class);

    private static final double MIN_CONFIDENCE = 0.50;

    private final AiProperties aiProperties;
    private final AiResultValidator aiResultValidator;
    private final JsonMapper jsonMapper;
    private final RestClient restClient;

    public AiPiiAnalyzer(
            AiProperties aiProperties,
            AiResultValidator aiResultValidator,
            JsonMapper jsonMapper
    ) {
        this.aiProperties = aiProperties;
        this.aiResultValidator = aiResultValidator;
        this.jsonMapper = jsonMapper;

        this.restClient = RestClient.builder()
                .baseUrl(aiProperties.getBaseUrl())
                .build();
    }

    public List<DetectedItem> analyze(
            String extractedText,
            int startSequence
    ) {
        if (extractedText == null || extractedText.isBlank()) {
            return List.of();
        }

        try {
            OllamaGenerateResponse ollamaResponse =
                    restClient.post()
                            .uri("/api/generate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(
                                    new OllamaGenerateRequest(
                                            aiProperties.getModel(),
                                            createPrompt(extractedText),
                                            false,
                                            "json"
                                    )
                            )
                            .retrieve()
                            .body(OllamaGenerateResponse.class);

            if (ollamaResponse == null
                    || ollamaResponse.response() == null
                    || ollamaResponse.response().isBlank()) {
                return List.of();
            }

            AiAnalysisResponse aiAnalysisResponse =
                    jsonMapper.readValue(
                            ollamaResponse.response(),
                            AiAnalysisResponse.class
                    );

            List<AiDetectedItem> validatedItems =
                    aiResultValidator.validate(
                            aiAnalysisResponse,
                            extractedText
                    );

            return toDetectedItems(validatedItems, startSequence);

        } catch (Exception e) {
            log.warn(
                    "AI PII analysis failed. fallbackToRuleBasedOnly=true",
                    e
            );
            return List.of();
        }
    }

    private List<DetectedItem> toDetectedItems(
            List<AiDetectedItem> validatedItems,
            int startSequence
    ) {
        List<DetectedItem> detectedItems = new ArrayList<>();
        int sequence = startSequence;

        for (AiDetectedItem item : validatedItems) {
            if (item.confidence() < MIN_CONFIDENCE) {
                continue;
            }

            detectedItems.add(
                    new DetectedItem(
                            "pii-" + sequence,
                            PiiType.PERSON_NAME,
                            maskName(item.sourceText()),
                            item.confidence(),
                            null
                    )
            );

            sequence++;
        }

        return detectedItems;
    }

    private String maskName(String sourceText) {
        String normalizedText = sourceText.trim();

        if (normalizedText.length() <= 1) {
            return "*";
        }

        return normalizedText.charAt(0)
                + "*".repeat(normalizedText.length() - 1);
    }

    private String createPrompt(String extractedText) {
        return """
                너는 OCR 개인정보 분류기다.

                규칙:
                1. 입력에 실제 존재하는 문자열만 반환한다.
                2. OCR 오타를 임의로 교정하지 않는다.
                3. 사람 이름으로 판단되는 문자열만 PERSON_NAME으로 분류한다.
                4. 입력에 없는 문자열을 새로 만들어서는 안 된다.
                5. 결과만 JSON으로 반환한다.
                6. PERSON_NAME 외의 다른 타입은 반환하지 않는다.

                OCR 결과:

                %s

                반환 형식:

                {
                  "detectedItems": [
                    {
                      "type": "PERSON_NAME",
                      "sourceText": "입력에 실제 존재하는 문자열",
                      "confidence": 0.0
                    }
                  ]
                }
                """.formatted(extractedText);
    }
}