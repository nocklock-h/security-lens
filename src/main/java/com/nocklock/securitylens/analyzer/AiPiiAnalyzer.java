package com.nocklock.securitylens.analyzer;

import tools.jackson.databind.json.JsonMapper;
import com.nocklock.securitylens.config.AiProperties;
import com.nocklock.securitylens.domain.PiiType;
import com.nocklock.securitylens.dto.AiAnalysisResponse;
import com.nocklock.securitylens.dto.AiDetectedItem;
import com.nocklock.securitylens.dto.DetectedItem;

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
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import org.springframework.http.HttpHeaders;

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

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(2_000);
        requestFactory.setReadTimeout(15_000);

        this.restClient = RestClient.builder()
                .baseUrl(aiProperties.getBaseUrl())
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + aiProperties.getApiKey()
                )
                .requestFactory(requestFactory)
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

            log.info(
                    "OpenAI PII request started. wordCount={}",
                    words.size()
            );

            Map<String, Object> requestBody =
                    Map.of(
                            "model",
                            aiProperties.getModel(),

                            "input",
                            createPrompt(words),

                            "store",
                            false,

                            "text",
                            Map.of(
                                    "format",
                                    createResponseFormat()
                            )
                    );


            OpenAiResponse openAiResponse =
                    restClient.post()
                            .uri("/v1/responses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(requestBody)
                            .retrieve()
                            .body(OpenAiResponse.class);

            log.info(
                    "OpenAI PII response received"
            );

            String responseText =
                    extractOutputText(
                            openAiResponse
                    );


            if (
                    responseText == null
                            ||
                            responseText.isBlank()
            ) {

                log.warn(
                        "OpenAI returned empty response. fallbackToRuleBasedOnly=true"
                );

                return AiPiiDetectionResult.empty();
            }


            AiAnalysisResponse aiAnalysisResponse =
                    jsonMapper.readValue(
                            responseText,
                            AiAnalysisResponse.class
                    );

            log.info(
                    "AI PII raw items={}",
                    aiAnalysisResponse.detectedItems() == null
                            ? List.of()
                            : aiAnalysisResponse.detectedItems()
                            .stream()
                            .map(
                                    item ->
                                            item.type()
                                                    + "(confidence="
                                                    + item.confidence()
                                                    + ", wordIds="
                                                    + item.wordIds()
                                                    + ")"
                            )
                            .toList()
            );

            List<AiDetectedItem> validatedItems =
                    aiResultValidator.validate(
                            aiAnalysisResponse,
                            words
                    );


            log.info(
                    "AI PII validated. items={}",
                    validatedItems.stream()
                            .map(
                                    item ->
                                            item.type()
                                                    + "(confidence="
                                                    + item.confidence()
                                                    + ", wordIds="
                                                    + item.wordIds()
                                                    + ")"
                            )
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

    private Map<String, Object> createResponseFormat() {

        Map<String, Object> detectedItemSchema =
                Map.of(
                        "type",
                        "object",

                        "additionalProperties",
                        false,

                        "properties",
                        Map.of(
                                "type",
                                Map.of(
                                        "type",
                                        "string",
                                        "enum",
                                        List.of(
                                                "PERSON_NAME",
                                                "ADDRESS"
                                        )
                                ),

                                "wordIds",
                                Map.of(
                                        "type",
                                        "array",
                                        "items",
                                        Map.of(
                                                "type",
                                                "integer"
                                        )
                                ),

                                "confidence",
                                Map.of(
                                        "type",
                                        "number",
                                        "minimum",
                                        0,
                                        "maximum",
                                        1
                                )
                        ),

                        "required",
                        List.of(
                                "type",
                                "wordIds",
                                "confidence"
                        )
                );


        Map<String, Object> schema =
                Map.of(
                        "type",
                        "object",

                        "additionalProperties",
                        false,

                        "properties",
                        Map.of(
                                "detectedItems",
                                Map.of(
                                        "type",
                                        "array",
                                        "items",
                                        detectedItemSchema
                                )
                        ),

                        "required",
                        List.of(
                                "detectedItems"
                        )
                );


        return Map.of(
                "type",
                "json_schema",

                "name",
                "security_lens_pii",

                "strict",
                true,

                "schema",
                schema
        );
    }


    private String extractOutputText(
            OpenAiResponse response
    ) {

        if (
                response == null
                        ||
                        response.output() == null
        ) {
            return null;
        }


        for (OpenAiOutput output : response.output()) {

            if (
                    output.content() == null
            ) {
                continue;
            }


            for (OpenAiContent content : output.content()) {

                if (
                        "output_text".equals(
                                content.type()
                        )
                ) {

                    return content.text();
                }
            }
        }


        return null;
    }


    private record OpenAiResponse(
            List<OpenAiOutput> output
    ) {
    }


    private record OpenAiOutput(
            String type,
            List<OpenAiContent> content
    ) {
    }


    private record OpenAiContent(
            String type,
            String text
    ) {
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
                    switch (type) {

                        case PERSON_NAME ->
                                "NAME_DETECTED";

                        case ADDRESS ->
                                "ADDRESS_DETECTED";

                        default ->
                                "PII_DETECTED";
                    };

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

            1. PERSON_NAME은 현재 이미지의 문맥에서
            실제 사람의 이름으로 판단되는 경우에만 반환한다.
                
            2. 문서 제목, 기관명, 회사명, 브랜드명, 지역명, 직함, 일반 명사 등을
            사람 이름으로 오인하지 않는다.
            
            3. PERSON_NAME은 언어와 국가에 따라 표기 방식이 다를 수 있다.
            이름의 길이, 단어 수, 문자 종류, 성/이름 순서에 의존하지 않는다.
            
                4. 여러 단어로 구성된 하나의 이름이라면
                   그 사람의 이름을 구성하는 모든 Word ID를 포함한다.

                4-1. 이미지에 PERSON_NAME이 여러 개 존재하면
                     각 이름을 서로 다른 PERSON_NAME 항목으로 모두 반환한다.

                4-2. 서로 다른 위치에 있는 이름들을
                     하나의 PERSON_NAME 항목으로 합치지 않는다.

                4-3. 동일한 이름이 이미지의 서로 다른 위치에 반복되어 있어도
                     각 위치를 별도의 PERSON_NAME 항목으로 반환한다.

                4-4. 하나의 PERSON_NAME 항목의 wordIds에는
                     반드시 한 위치의 한 사람 이름에 해당하는 Word ID만 포함한다.
            
            5. ADDRESS는 현재 이미지의 문맥에서
            개인 또는 특정 대상의 위치를 구체적으로 식별할 수 있는 주소일 때만 반환한다.
            
            6. ADDRESS는 국가와 언어에 따라 형식이 다를 수 있다.
            특정 국가의 행정구역명, 주소 순서, 도로명/지번 형식에 의존하지 않는다.
            
            7. 도로명, 지번, 건물명, 호수, 우편번호 등
            여러 요소가 주소를 구성하는 경우 관련 Word ID를 모두 포함한다.
            
            8. 도시명, 국가명, 광역 지역명처럼
            위치 범위가 너무 넓어 개인의 구체적 위치를 식별하기 어려운 경우에는
            ADDRESS로 반환하지 않는다.
            
            9. PERSON_NAME과 ADDRESS가 모두 존재하면
            둘 다 반환한다.
            
            10. 반드시 아래 OCR WORDS 목록에 존재하는 Word ID만 사용한다.
            새로운 문자열이나 존재하지 않는 Word ID를 생성하지 않는다.
            
            11. 개인정보 여부가 불확실한 경우에는 반환하지 않는다.
            
            12. 결과는 지정된 JSON 형식으로만 반환한다.


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
                      "type": "PERSON_NAME",
                      "wordIds": [30, 31],
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