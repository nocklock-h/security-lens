package com.nocklock.securitylens.analyzer;

import com.nocklock.securitylens.config.AiProperties;
import com.nocklock.securitylens.dto.BoundingBox;
import com.nocklock.securitylens.dto.DocumentVisionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.nocklock.securitylens.dto.DocumentFieldResult;

@Component
public class DocumentVisionAnalyzer {

    private static final Logger log =
            LoggerFactory.getLogger(
                    DocumentVisionAnalyzer.class
            );

    private static final int MAX_VISION_DIMENSION =
            1280;

    private final AiProperties aiProperties;
    private final JsonMapper jsonMapper;
    private final RestClient restClient;


    public DocumentVisionAnalyzer(
            AiProperties aiProperties,
            JsonMapper jsonMapper
    ) {

        this.aiProperties =
                aiProperties;

        this.jsonMapper =
                jsonMapper;


        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(
                2_000
        );

        requestFactory.setReadTimeout(
                15_000
        );


        this.restClient =
                RestClient.builder()
                        .baseUrl(
                                aiProperties.getBaseUrl()
                        )
                        .defaultHeader(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer "
                                        + aiProperties.getApiKey()
                        )
                        .requestFactory(
                                requestFactory
                        )
                        .build();
    }


    public DocumentVisionResult analyze(
            MultipartFile image
    ) {

        try {

            BufferedImage original =
                    ImageIO.read(
                            image.getInputStream()
                    );


            if (original == null) {
                return DocumentVisionResult.empty();
            }


            BufferedImage visionImage =
                    resizeForVision(
                            original
                    );


            String dataUrl =
                    toDataUrl(
                            visionImage
                    );


            String visionModel =
                    aiProperties.getVisionModel();

            if (
                    visionModel == null
                            ||
                            visionModel.isBlank()
            ) {
                visionModel =
                        aiProperties.getModel();
            }

            Map<String, Object> requestBody =
                    Map.of(
                            "model",
                            visionModel,

                            "store",
                            false,

                            "input",
                            List.of(
                                    Map.of(
                                            "role",
                                            "user",

                                            "content",
                                            List.of(

                                                    Map.of(
                                                            "type",
                                                            "input_text",
                                                            "text",
                                                            createPrompt()
                                                    ),

                                                    Map.of(
                                                            "type",
                                                            "input_image",
                                                            "image_url",
                                                            dataUrl,
                                                            "detail",
                                                            "auto"
                                                    )
                                            )
                                    )
                            ),

                            "text",
                            Map.of(
                                    "format",
                                    createResponseFormat()
                            )
                    );


            log.info(
                    "Document vision request started. model={}",
                    visionModel
            );


            OpenAiResponse response =
                    restClient.post()
                            .uri("/v1/responses")
                            .contentType(
                                    MediaType.APPLICATION_JSON
                            )
                            .body(requestBody)
                            .retrieve()
                            .body(OpenAiResponse.class);


            String responseText =
                    extractOutputText(
                            response
                    );


            if (
                    responseText == null
                            ||
                            responseText.isBlank()
            ) {

                return DocumentVisionResult.empty();
            }


            VisionResponse vision =
                    jsonMapper.readValue(
                            responseText,
                            VisionResponse.class
                    );


            if (
                    !vision.detected()
                            ||
                            "NONE".equals(
                                    vision.documentType()
                            )
            ) {

                return DocumentVisionResult.empty();
            }


            BoundingBox originalBox =
                    toOriginalBoundingBox(
                            vision.boundingBox(),
                            original.getWidth(),
                            original.getHeight()
                    );
            List<DocumentFieldResult> fields =
                    vision.fields() == null
                            ? List.of()
                            : vision.fields()
                            .stream()

                            .filter(
                                    field ->
                                            field.boundingBox() != null
                                                    &&
                                                    field.confidence() >= 0.70
                            )

                            .map(
                                    field ->
                                            new DocumentFieldResult(
                                                    field.type(),
                                                    field.confidence(),
                                                    toOriginalBoundingBox(
                                                            field.boundingBox(),
                                                            original.getWidth(),
                                                            original.getHeight()
                                                    )
                                            )
                            )

                            .toList();

            log.info(
                    "Sensitive document detected. type={}, confidence={}, fieldCount={}",
                    vision.documentType(),
                    vision.confidence(),
                    fields.size()
            );

            return new DocumentVisionResult(
                    true,
                    vision.documentType(),
                    vision.confidence(),
                    originalBox,
                    fields
            );


        } catch (Exception e) {

            log.warn(
                    "Document vision analysis failed",
                    e
            );

            return DocumentVisionResult.empty();
        }
    }

    private String createPrompt() {

        return """
            너는 이미지 속 민감 신원 문서와 개인정보 필드의 위치를 탐지하는 시스템이다.

            절대로 개인정보의 실제 값을 반환하지 않는다.
            이름, 주민등록번호, 운전면허번호, 주소 등의 문자열을 복사하거나 출력하지 않는다.

            먼저 다음 문서 종류를 탐지한다.

            - DRIVER_LICENSE
            - PASSPORT
            - NATIONAL_ID
            - OTHER_SENSITIVE_DOCUMENT

            문서가 발견되면 문서 전체 boundingBox와
            다음 개인정보 필드의 위치를 탐지한다.

            지원 필드:
            - PERSON_NAME
            - RRN
            - DRIVER_LICENSE_NUMBER
            - ADDRESS

            규칙:

            1. 실제 값은 절대 출력하지 않는다.

            2. fields에는 이미지에서 실제로 확인 가능한 개인정보 필드만 반환한다.

            3. 각 field의 boundingBox는 해당 개인정보 값 전체를 가려야 한다.
               라벨 글자보다 실제 값 영역을 우선한다.

            4. PERSON_NAME은 성을 포함한 이름의 모든 글자를 포함한다.
               특히 가장 왼쪽 첫 글자가 boundingBox 밖으로 잘리지 않도록 한다.
               단, 주변 라벨이나 사진 영역 또는 다른 개인정보 필드는 포함하지 않는다.
               운전면허증(DRIVER_LICENSE)을 분석할 때는 각 필드를 서로 혼동하지 않는다.
                
               대한민국 운전면허증의 경우 일반적으로 다음과 같은 상대적 배치를 가진다.
            
               - DRIVER_LICENSE_NUMBER:
                 문서 상단의 긴 식별번호 영역이다.
                 PERSON_NAME보다 위에 있으며 짧은 이름 영역으로 지정하지 않는다.
            
               - PERSON_NAME:
                 운전면허번호 아래에 위치하는 비교적 짧은 이름 문자열 영역이다.
                 성을 포함한 이름 전체만 포함하며 주민등록번호 영역과 혼동하지 않는다.
            
               - RRN:
                 이름 아래쪽의 긴 숫자 식별번호 영역이다.
                 PERSON_NAME과 동일한 boundingBox를 사용하지 않는다.
            
               - ADDRESS:
                 주민등록번호보다 아래에 위치하며 여러 글자 또는 여러 줄일 수 있다.
            
               각 개인정보 field는 서로 다른 실제 영역이어야 한다.
               서로 크게 겹치는 boundingBox를 여러 field에 중복 지정하지 않는다.
            
               DRIVER_LICENSE_NUMBER와 RRN은 일반적으로
               PERSON_NAME보다 가로로 긴 영역이다.

            5. 주민등록번호나 운전면허번호가 여러 OCR 조각으로 보이더라도
               번호 전체가 포함되도록 하나의 boundingBox를 반환한다.

            6. 주소가 여러 줄이면 주소 전체가 포함되는 하나의 boundingBox를 반환한다.

            7. 확실하지 않은 필드는 반환하지 않는다.

            8. 모든 좌표는 이미지 전체를 기준으로
               0부터 1000 사이의 정규화 좌표를 사용한다.

            9. 문서가 없으면 detected=false,
               documentType=NONE,
               confidence=0,
               문서 boundingBox는 모두 0,
               fields는 빈 배열로 반환한다.
            """;
    }


    private Map<String, Object> createResponseFormat() {

        Map<String, Object> boxSchema =
                Map.of(
                        "type",
                        "object",

                        "additionalProperties",
                        false,

                        "properties",
                        Map.of(
                                "x",
                                Map.of(
                                        "type", "integer",
                                        "minimum", 0,
                                        "maximum", 1000
                                ),

                                "y",
                                Map.of(
                                        "type", "integer",
                                        "minimum", 0,
                                        "maximum", 1000
                                ),

                                "width",
                                Map.of(
                                        "type", "integer",
                                        "minimum", 0,
                                        "maximum", 1000
                                ),

                                "height",
                                Map.of(
                                        "type", "integer",
                                        "minimum", 0,
                                        "maximum", 1000
                                )
                        ),

                        "required",
                        List.of(
                                "x",
                                "y",
                                "width",
                                "height"
                        )
                );


        Map<String, Object> fieldSchema =
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
                                                "RRN",
                                                "DRIVER_LICENSE_NUMBER",
                                                "ADDRESS"
                                        )
                                ),

                                "confidence",
                                Map.of(
                                        "type", "number",
                                        "minimum", 0,
                                        "maximum", 1
                                ),

                                "boundingBox",
                                boxSchema
                        ),

                        "required",
                        List.of(
                                "type",
                                "confidence",
                                "boundingBox"
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
                                "detected",
                                Map.of(
                                        "type",
                                        "boolean"
                                ),

                                "documentType",
                                Map.of(
                                        "type",
                                        "string",
                                        "enum",
                                        List.of(
                                                "DRIVER_LICENSE",
                                                "PASSPORT",
                                                "NATIONAL_ID",
                                                "OTHER_SENSITIVE_DOCUMENT",
                                                "NONE"
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
                                ),

                                "boundingBox",
                                boxSchema,

                                "fields",
                                Map.of(
                                        "type",
                                        "array",
                                        "items",
                                        fieldSchema
                                )
                        ),

                        "required",
                        List.of(
                                "detected",
                                "documentType",
                                "confidence",
                                "boundingBox",
                                "fields"
                        )
                );


        return Map.of(
                "type",
                "json_schema",
                "name",
                "document_detection",
                "strict",
                true,
                "schema",
                schema
        );
    }

    private BufferedImage resizeForVision(
            BufferedImage original
    ) {

        int width =
                original.getWidth();

        int height =
                original.getHeight();


        int max =
                Math.max(
                        width,
                        height
                );


        if (max <= MAX_VISION_DIMENSION) {
            return original;
        }


        double ratio =
                (double) MAX_VISION_DIMENSION
                        / max;


        int targetWidth =
                (int) Math.round(
                        width * ratio
                );

        int targetHeight =
                (int) Math.round(
                        height * ratio
                );


        BufferedImage resized =
                new BufferedImage(
                        targetWidth,
                        targetHeight,
                        BufferedImage.TYPE_INT_RGB
                );


        Graphics2D graphics =
                resized.createGraphics();


        try {

            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC
            );

            graphics.drawImage(
                    original,
                    0,
                    0,
                    targetWidth,
                    targetHeight,
                    null
            );

        } finally {

            graphics.dispose();
        }


        return resized;
    }


    private String toDataUrl(
            BufferedImage image
    ) throws Exception {

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();


        ImageIO.write(
                image,
                "jpg",
                output
        );


        String base64 =
                Base64.getEncoder()
                        .encodeToString(
                                output.toByteArray()
                        );


        return "data:image/jpeg;base64,"
                + base64;
    }

    private BoundingBox toOriginalBoundingBox(
            VisionBoundingBox box,
            int imageWidth,
            int imageHeight
    ) {

        double x =
                box.x() / 1000.0
                        * imageWidth;

        double y =
                box.y() / 1000.0
                        * imageHeight;

        double width =
                box.width() / 1000.0
                        * imageWidth;

        double height =
                box.height() / 1000.0
                        * imageHeight;


        double paddingX =
                width * 0.05;

        double paddingY =
                height * 0.05;


        double safeX =
                Math.max(
                        0,
                        x - paddingX
                );

        double safeY =
                Math.max(
                        0,
                        y - paddingY
                );


        double right =
                Math.min(
                        imageWidth,
                        x + width + paddingX
                );

        double bottom =
                Math.min(
                        imageHeight,
                        y + height + paddingY
                );


        return new BoundingBox(
                safeX,
                safeY,
                right - safeX,
                bottom - safeY
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

            if (output.content() == null) {
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


    private record VisionResponse(
            boolean detected,
            String documentType,
            double confidence,
            VisionBoundingBox boundingBox,
            List<VisionField> fields
    ) {
    }

    private record VisionField(
            String type,
            double confidence,
            VisionBoundingBox boundingBox
    ) {
    }

    private record VisionBoundingBox(
            int x,
            int y,
            int width,
            int height
    ) {
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
}