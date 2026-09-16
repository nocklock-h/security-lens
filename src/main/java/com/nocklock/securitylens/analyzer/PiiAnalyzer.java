package com.nocklock.securitylens.analyzer;

import com.nocklock.securitylens.domain.PiiType;
import com.nocklock.securitylens.dto.DetectedItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PiiAnalyzer {

    /*
     * 대한민국 휴대전화 번호 패턴.
     * 현재 MVP에서는 국내 전화번호부터 안정적으로 탐지한다.
     */
    private static final Pattern KOREAN_PHONE_PATTERN = Pattern.compile(
            "(?<!\\d)(01[016789])[-\\s]?(\\d{3,4})[-\\s]?(\\d{4})(?!\\d)"
    );

    /*
     * 이메일 주소는 특정 언어에 종속되지 않으므로
     * 한국어/영어 OCR 결과 모두 동일하게 탐지할 수 있다.
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "([A-Z0-9._%+-]+)@([A-Z0-9.-]+\\.[A-Z]{2,})",
            Pattern.CASE_INSENSITIVE
    );

    /*
     * 대한민국 행정구역 형태를 이용한 주소 후보 탐지.
     * 실제 존재하는 주소인지 검증하는 것이 아니라
     * 주소처럼 보이는 문자열을 탐지하는 MVP 규칙이다.
     */
    private static final Pattern KOREAN_ADDRESS_PATTERN = Pattern.compile(
            "([가-힣]{2,}(?:특별시|광역시|특별자치시|특별자치도|도))"
                    + "\\s+"
                    + "([가-힣]{1,10}(?:시|군|구))"
                    + "\\s+"
                    + "([가-힣0-9]{1,15}(?:동|읍|면|리|가))"
    );

    /*
     * 대한민국 주민등록번호
     */
    private static final Pattern RRN_PATTERN =
            Pattern.compile(
                    "(?<!\\d)\\d{6}-?[1-4]\\d{6}(?!\\d)"
            );


    /*
     * 대한민국 운전면허번호
     * NN-NN-NNNNNN-NN
     */
    private static final Pattern DRIVER_LICENSE_PATTERN =
            Pattern.compile(
                    "(?<!\\d)\\d{2}-\\d{2}-\\d{6}-\\d{2}(?!\\d)"
            );


    /*
     * 운전면허증 문서 자체 탐지
     */
    private static final Pattern DRIVER_LICENSE_DOCUMENT_PATTERN =
            Pattern.compile(
                    "(driver\\s*['’]?s?\\s*license|운전\\s*면허)",
                    Pattern.CASE_INSENSITIVE
            );


    /*
     * 대한민국 도로명 주소 후보
     * 예: 강남구 학동로97길
     */
    private static final Pattern KOREAN_ROAD_ADDRESS_PATTERN =
            Pattern.compile(
                    "([가-힣]{1,10}(?:시|군|구))"
                            + "\\s+"
                            + "([가-힣]{1,20}(?:로|길)\\d+(?:길)?)"
            );

    /*
     * 현재 OCR에서는 문자별 실제 confidence를 전달받지 않으므로
     * MVP에서는 탐지 방식에 따른 규칙 기반 confidence를 사용한다.
     */
    private static final double STRUCTURED_PATTERN_CONFIDENCE = 0.90;
    private static final double ADDRESS_PATTERN_CONFIDENCE = 0.85;

    public List<DetectedItem> analyze(String extractedText) {
        List<DetectedItem> detectedItems = new ArrayList<>();

        if (extractedText == null || extractedText.isBlank()) {
            return detectedItems;
        }

        detectRrn(extractedText, detectedItems);

        detectDriverLicenseNumber(
                extractedText,
                detectedItems
        );

        detectSensitiveDocument(
                extractedText,
                detectedItems
        );

        detectPhones(
                extractedText,
                detectedItems
        );

        detectEmails(
                extractedText,
                detectedItems
        );

        detectKoreanAddresses(
                extractedText,
                detectedItems
        );

        detectKoreanRoadAddresses(
                extractedText,
                detectedItems
        );

        return detectedItems;
    }

    private void detectPhones(
            String text,
            List<DetectedItem> detectedItems
    ) {
        Matcher matcher = KOREAN_PHONE_PATTERN.matcher(text);

        while (matcher.find()) {
            String maskedValue = maskPhone(
                    matcher.group(1),
                    matcher.group(2),
                    matcher.group(3)
            );

            detectedItems.add(
                    createDetectedItem(
                            PiiType.PHONE,
                            maskedValue,
                            STRUCTURED_PATTERN_CONFIDENCE,
                            detectedItems.size() + 1
                    )
            );
        }
    }

    private void detectEmails(
            String text,
            List<DetectedItem> detectedItems
    ) {
        Matcher matcher = EMAIL_PATTERN.matcher(text);

        while (matcher.find()) {
            String maskedValue = maskEmail(
                    matcher.group(1),
                    matcher.group(2)
            );

            detectedItems.add(
                    createDetectedItem(
                            PiiType.EMAIL,
                            maskedValue,
                            STRUCTURED_PATTERN_CONFIDENCE,
                            detectedItems.size() + 1
                    )
            );
        }
    }

    private void detectKoreanAddresses(
            String text,
            List<DetectedItem> detectedItems
    ) {
        Matcher matcher = KOREAN_ADDRESS_PATTERN.matcher(text);

        while (matcher.find()) {
            String maskedValue =
                    matcher.group(1)
                            + " "
                            + matcher.group(2)
                            + " ***";

            detectedItems.add(
                    createDetectedItem(
                            PiiType.ADDRESS,
                            maskedValue,
                            ADDRESS_PATTERN_CONFIDENCE,
                            detectedItems.size() + 1
                    )
            );
        }
    }

    private void detectRrn(
            String text,
            List<DetectedItem> detectedItems
    ) {

        Matcher matcher =
                RRN_PATTERN.matcher(text);

        while (matcher.find()) {

            detectedItems.add(
                    createDetectedItem(
                            PiiType.RRN,
                            "******-*******",
                            STRUCTURED_PATTERN_CONFIDENCE,
                            detectedItems.size() + 1
                    )
            );
        }
    }


    private void detectDriverLicenseNumber(
            String text,
            List<DetectedItem> detectedItems
    ) {

        Matcher matcher =
                DRIVER_LICENSE_PATTERN.matcher(text);

        while (matcher.find()) {

            detectedItems.add(
                    createDetectedItem(
                            PiiType.DRIVER_LICENSE_NUMBER,
                            "**-**-******-**",
                            STRUCTURED_PATTERN_CONFIDENCE,
                            detectedItems.size() + 1
                    )
            );
        }
    }


    private void detectSensitiveDocument(
            String text,
            List<DetectedItem> detectedItems
    ) {

        Matcher matcher =
                DRIVER_LICENSE_DOCUMENT_PATTERN.matcher(text);

        if (matcher.find()) {

            detectedItems.add(
                    createDetectedItem(
                            PiiType.SENSITIVE_DOCUMENT,
                            "DRIVER_LICENSE",
                            STRUCTURED_PATTERN_CONFIDENCE,
                            detectedItems.size() + 1
                    )
            );
        }
    }


    private void detectKoreanRoadAddresses(
            String text,
            List<DetectedItem> detectedItems
    ) {

        boolean alreadyDetectedAddress =
                detectedItems.stream()
                        .anyMatch(item ->
                                item.type() == PiiType.ADDRESS
                        );

        if (alreadyDetectedAddress) {
            return;
        }


        Matcher matcher =
                KOREAN_ROAD_ADDRESS_PATTERN.matcher(text);


        while (matcher.find()) {

            String maskedValue =
                    matcher.group(1)
                            + " "
                            + "***";


            detectedItems.add(
                    createDetectedItem(
                            PiiType.ADDRESS,
                            maskedValue,
                            ADDRESS_PATTERN_CONFIDENCE,
                            detectedItems.size() + 1
                    )
            );
        }
    }

    private DetectedItem createDetectedItem(
            PiiType type,
            String maskedValue,
            double confidence,
            int sequence
    ) {
        return new DetectedItem(
                "pii-" + sequence,
                type,
                maskedValue,
                confidence,
                null
        );
    }

    private String maskPhone(
            String prefix,
            String middle,
            String suffix
    ) {
        return prefix
                + "-"
                + "*".repeat(middle.length())
                + "-"
                + suffix;
    }

    private String maskEmail(
            String localPart,
            String domain
    ) {
        String maskedLocalPart;

        if (localPart.length() <= 1) {
            maskedLocalPart = "*";
        } else {
            maskedLocalPart =
                    localPart.charAt(0)
                            + "*".repeat(localPart.length() - 1);
        }

        return maskedLocalPart + "@" + domain;
    }
}