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

import com.nocklock.securitylens.dto.AiPiiDetectionResult;

import java.util.HashMap;
import java.util.Map;

import com.nocklock.securitylens.dto.OcrWord;

@Component
public class AiPiiAnalyzer {

    private static final Logger log =
            LoggerFactory.getLogger(AiPiiAnalyzer.class);

    private static final double MIN_CONFIDENCE = 0.70;

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

    public AiPiiDetectionResult  analyze(
            String extractedText,
            List<OcrWord> words,
            int startSequence
    ) {
        if (
                words == null
                        ||
                        words.isEmpty()
        ) {

            return AiPiiDetectionResult.empty();
        }

        if (extractedText == null || extractedText.isBlank()) {
            return AiPiiDetectionResult.empty();
        }

        try {
            OllamaGenerateResponse ollamaResponse =
                    restClient.post()
                            .uri("/api/generate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(
                                    new OllamaGenerateRequest(
                                            aiProperties.getModel(),
                                            createPrompt(words),
                                            false,
                                            "json"
                                    )
                            )
                            .retrieve()
                            .body(OllamaGenerateResponse.class);

            if (ollamaResponse == null
                    || ollamaResponse.response() == null
                    || ollamaResponse.response().isBlank()) {
                return AiPiiDetectionResult.empty();
            }

            AiAnalysisResponse aiAnalysisResponse =
                    jsonMapper.readValue(
                            ollamaResponse.response(),
                            AiAnalysisResponse.class
                    );

            List<AiDetectedItem> validatedItems =
                    aiResultValidator.validate(
                            aiAnalysisResponse,
                            words
                    );

            log.info(
                    "AI PII validated. count={}, types={}, wordIds={}",
                    validatedItems.size(),
                    validatedItems.stream()
                            .map(AiDetectedItem::type)
                            .toList(),
                    validatedItems.stream()
                            .map(AiDetectedItem::wordIds)
                            .toList()
            );

            return toDetectionResult(
                    validatedItems,
                    words,
                    startSequence
            );

        } catch (Exception e) {
            log.warn(
                    "AI PII analysis failed. fallbackToRuleBasedOnly=true",
                    e
            );
            return AiPiiDetectionResult.empty();
        }
    }

    private AiPiiDetectionResult toDetectionResult(
            List<AiDetectedItem> validatedItems,
            List<OcrWord> words,
            int startSequence
    ) {

        List<DetectedItem> detectedItems =
                new ArrayList<>();

        Map<String, List<Integer>> wordIdsById =
                new HashMap<>();


        int sequence =
                startSequence;


        for (AiDetectedItem item : validatedItems) {

            if (item.confidence() < MIN_CONFIDENCE) {
                continue;
            }


            PiiType type;

            try {

                type =
                        PiiType.valueOf(
                                String.valueOf(
                                                item.type()
                                        )
                                        .trim()
                                        .toUpperCase()
                        );

            } catch (Exception e) {

                continue;
            }


            /*
             * MVP에서 AI에게 맡기는 의미 기반 PII만 허용.
             */
            if (
                    type != PiiType.PERSON_NAME
                            &&
                            type != PiiType.ADDRESS
            ) {

                continue;
            }


            List<Integer> wordIds =
                    item.wordIds()
                            .stream()
                            .distinct()
                            .sorted()
                            .toList();


            if (wordIds.isEmpty()) {
                continue;
            }

            String id =
                    "pii-" + sequence;


            String maskedValue =
                    "***";


            detectedItems.add(
                    new DetectedItem(
                            id,
                            type,
                            maskedValue,
                            item.confidence(),
                            null
                    )
            );


            wordIdsById.put(
                    id,
                    wordIds
            );


            sequence++;
        }


        return new AiPiiDetectionResult(
                detectedItems,
                wordIdsById
        );
    }

    private String maskName(String sourceText) {
        String normalizedText = sourceText.trim();

        if (normalizedText.length() <= 1) {
            return "*";
        }

        return normalizedText.charAt(0)
                + "*".repeat(normalizedText.length() - 1);
    }

    private String createPrompt(
            List<OcrWord> words
    ) {

        StringBuilder wordList =
                new StringBuilder();


        for (
                int i = 0;
                i < words.size();
                i++
        ) {

            OcrWord word =
                    words.get(i);


            wordList.append("[")
                    .append(i)
                    .append("] ")
                    .append(word.text())
                    .append("\n");
        }


        return """
            너는 OCR 결과에서 개인정보 영역을 선택하는 분류기다.

            너는 새로운 문자열이나 좌표를 생성하지 않는다.
            아래에 제공된 OCR Word ID 중에서
            개인정보에 해당하는 Word ID만 선택한다.

            반환 가능한 타입은 두 개뿐이다.

            - PERSON_NAME
            - ADDRESS

            규칙:

            1. PERSON_NAME은 실제 사람 이름일 때만 반환한다.

            2. 문서 제목, 기관명, 지역명, 직함 등을
               사람 이름으로 오인하지 않는다.

            3. ADDRESS는 현재 이미지 문맥에서
               개인과 연결되는 구체적인 주소일 때만 반환한다.

            4. 주소가 여러 줄에 걸쳐 있다면
               상세주소를 구성하는 모든 Word ID를 포함한다.

            5. 도시명이나 지역명만 있는 경우에는
               ADDRESS로 반환하지 않는다.

            6. PERSON_NAME과 ADDRESS가 모두 존재하면
               둘 다 반환한다.

            7. 반드시 아래 목록에 존재하는 Word ID만 사용한다.

            8. 확신할 수 없는 개인정보는 반환하지 않는다.

            9. 결과는 JSON만 반환한다.


            OCR WORDS:

            %s


            반환 형식:

            {
              "detectedItems": [
                {
                  "type": "PERSON_NAME",
                  "wordIds": [12],
                  "confidence": 0.0
                },
                {
                  "type": "ADDRESS",
                  "wordIds": [20, 21, 22, 23],
                  "confidence": 0.0
                }
              ]
            }
            """.formatted(
                wordList
        );
    }
}