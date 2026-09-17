package com.nocklock.securitylens.service;

import com.nocklock.securitylens.domain.PiiType;
import com.nocklock.securitylens.dto.BoundingBox;
import com.nocklock.securitylens.dto.DetectedItem;
import com.nocklock.securitylens.dto.OcrWord;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.regex.Pattern;
import java.util.Collections;

@Component
public class BoundingBoxResolver {

    private static final Pattern PHONE_PATTERN =
            Pattern.compile(
                    "01[016789]-?\\d{3,4}-?\\d{4}"
            );

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile(
                    "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern RRN_PATTERN =
            Pattern.compile(
                    "\\d{6}-?[1-4]\\d{6}"
            );

    private static final Pattern DRIVER_LICENSE_PATTERN =
            Pattern.compile(
                    "\\d{2}-\\d{2}-\\d{6}-\\d{2}"
            );

    private static final Pattern DATE_LIKE_PATTERN =
            Pattern.compile(
                    ".*\\d{4}[./-]\\d{1,2}[./-]\\d{1,2}.*"
            );

    public List<DetectedItem> attach(
            List<DetectedItem> detectedItems,
            List<OcrWord> words
    ) {

        return attach(
                detectedItems,
                words,
                Collections.emptyMap()
        );
    }

    public List<DetectedItem> attach(
            List<DetectedItem> detectedItems,
            List<OcrWord> words,
            Map<String, List<Integer>> wordIdsById
    ) {

        Map<PiiType, Queue<BoundingBox>> boxes =
                new EnumMap<>(PiiType.class);


        boxes.put(
                PiiType.PHONE,
                new ArrayDeque<>(
                        findStructuredBoxes(
                                words,
                                PHONE_PATTERN,
                                3
                        )
                )
        );

        boxes.put(
                PiiType.EMAIL,
                new ArrayDeque<>(
                        findStructuredBoxes(
                                words,
                                EMAIL_PATTERN,
                                4
                        )
                )
        );

        boxes.put(
                PiiType.RRN,
                new ArrayDeque<>(
                        findStructuredBoxes(
                                words,
                                RRN_PATTERN,
                                3
                        )
                )
        );

        boxes.put(
                PiiType.DRIVER_LICENSE_NUMBER,
                new ArrayDeque<>(
                        findStructuredBoxes(
                                words,
                                DRIVER_LICENSE_PATTERN,
                                4
                        )
                )
        );

        boxes.put(
                PiiType.ADDRESS,
                new ArrayDeque<>(
                        findAddressBoxes(words)
                )
        );

        boxes.put(
                PiiType.SENSITIVE_DOCUMENT,
                new ArrayDeque<>(
                        findSensitiveDocumentBoxes(words)
                )
        );


        List<DetectedItem> result =
                new ArrayList<>();


        for (DetectedItem item : detectedItems) {

            Queue<BoundingBox> candidates =
                    boxes.get(
                            item.type()
                    );


            BoundingBox boundingBox =
                    null;


            /*
             * AI가 실제 OCR 원문을 반환한 항목이라면
             * 문자열 기반 Grounding을 가장 먼저 시도한다.
             */
            List<Integer> wordIds =
                    wordIdsById.get(
                            item.id()
                    );


            if (
                    wordIds != null
                            &&
                            !wordIds.isEmpty()
            ) {

                boundingBox =
                        findWordIdBox(
                                wordIds,
                                words
                        );
            }


            /*
             * 문자열 Grounding이 실패했거나
             * Rule 기반 탐지 결과라면 기존 방식 fallback.
             */
            if (
                    boundingBox == null
                            &&
                            candidates != null
            ) {

                boundingBox =
                        candidates.poll();
            }

            result.add(
                    new DetectedItem(
                            item.id(),
                            item.type(),
                            item.maskedValue(),
                            item.confidence(),
                            boundingBox
                    )
            );
        }


        return result;
    }


    private List<BoundingBox> findStructuredBoxes(
            List<OcrWord> words,
            Pattern pattern,
            int maxWords
    ) {

        List<BoundingBox> result =
                new ArrayList<>();


        for (int start = 0;
             start < words.size();
             start++) {


            for (int length = 1;
                 length <= maxWords;
                 length++) {


                int end =
                        start + length;


                if (end > words.size()) {
                    break;
                }


                List<OcrWord> group =
                        words.subList(
                                start,
                                end
                        );


                if (!sameVisualLine(group)) {
                    break;
                }


                String candidate =
                        normalize(
                                group.stream()
                                        .map(OcrWord::text)
                                        .reduce(
                                                "",
                                                String::concat
                                        )
                        );


                if (
                        pattern.matcher(candidate)
                                .matches()
                ) {

                    result.add(
                            union(group)
                    );

                    start =
                            end - 1;

                    break;
                }
            }
        }


        return result;
    }


    private List<BoundingBox> findAddressBoxes(
            List<OcrWord> words
    ) {

        List<BoundingBox> result =
                new ArrayList<>();


        /*
         * 먼저 OCR Word들을
         * 시각적인 행 단위로 묶는다.
         */
        List<List<OcrWord>> lines =
                new ArrayList<>();


        List<OcrWord> currentLine =
                new ArrayList<>();


        for (OcrWord word : words) {

            if (
                    currentLine.isEmpty()
                            ||
                            sameVisualLine(
                                    currentLine.get(
                                            currentLine.size() - 1
                                    ),
                                    word
                            )
            ) {

                currentLine.add(word);

            } else {

                lines.add(
                        currentLine
                );


                currentLine =
                        new ArrayList<>();


                currentLine.add(word);
            }
        }


        if (!currentLine.isEmpty()) {

            lines.add(
                    currentLine
            );
        }


        /*
         * 주소 시작 행을 찾는다.
         */
        for (
                int i = 0;
                i < lines.size();
                i++
        ) {

            List<OcrWord> line =
                    lines.get(i);


            String text =
                    joinWords(line);


            if (!looksLikeAddressStart(text)) {
                continue;
            }


            List<OcrWord> addressWords =
                    new ArrayList<>(
                            line
                    );


            /*
             * 운전면허증 등의 주소는
             * 다음 행에 번지/동/호 등이 이어질 수 있다.
             *
             * 바로 다음 최대 2개 행까지만 검사하여
             * 무작정 아래 문서 전체를 합치지 않는다.
             */
            for (
                    int offset = 1;
                    offset <= 2;
                    offset++
            ) {

                int nextIndex =
                        i + offset;


                if (nextIndex >= lines.size()) {
                    break;
                }


                List<OcrWord> nextLine =
                        lines.get(nextIndex);


                List<OcrWord> previousLine =
                        offset == 1
                                ? line
                                : lines.get(
                                nextIndex - 1
                        );


                boolean semanticContinuation =
                        looksLikeAddressContinuation(
                                nextLine
                        );


                boolean spatialContinuation =
                        isSpatialAddressContinuation(
                                previousLine,
                                nextLine
                        );


                if (
                        !semanticContinuation
                                &&
                                !spatialContinuation
                ) {

                    break;
                }


                List<OcrWord> continuationWords =
                        filterAddressColumnWords(
                                line,
                                nextLine
                        );


                if (continuationWords.isEmpty()) {
                    break;
                }


                addressWords.addAll(
                        continuationWords
                );
            }


            result.add(
                    union(
                            addressWords
                    )
            );
        }


        return result;
    }

    private boolean isSpatialAddressContinuation(
            List<OcrWord> previousLine,
            List<OcrWord> nextLine
    ) {

        if (
                previousLine == null
                        ||
                        previousLine.isEmpty()
                        ||
                        nextLine == null
                        ||
                        nextLine.isEmpty()
        ) {

            return false;
        }


        String nextText =
                joinWords(
                        nextLine
                );


        /*
         * 바로 아래에 날짜 행이 있다면
         * 주소 영역으로 확장하지 않는다.
         */
        if (
                DATE_LIKE_PATTERN
                        .matcher(nextText)
                        .matches()
        ) {

            return false;
        }


        BoundingBox previousBox =
                union(
                        previousLine
                );


        BoundingBox nextBox =
                union(
                        nextLine
                );


        double previousBottom =
                previousBox.y()
                        +
                        previousBox.height();


        double verticalGap =
                nextBox.y()
                        -
                        previousBottom;


        double referenceHeight =
                Math.max(
                        previousBox.height(),
                        nextBox.height()
                );


        /*
         * 너무 멀리 떨어진 행은
         * 같은 주소 블록이 아니다.
         */
        if (
                verticalGap < -referenceHeight * 0.30
                        ||
                        verticalGap > referenceHeight * 1.50
        ) {

            return false;
        }


        double leftDifference =
                Math.abs(
                        previousBox.x()
                                -
                                nextBox.x()
                );


        boolean similarLeftEdge =
                leftDifference
                        <=
                        Math.max(
                                40.0,
                                referenceHeight * 2.0
                        );


        double horizontalOverlap =
                Math.min(
                        previousBox.x()
                                +
                                previousBox.width(),
                        nextBox.x()
                                +
                                nextBox.width()
                )
                        -
                        Math.max(
                                previousBox.x(),
                                nextBox.x()
                        );


        double smallerWidth =
                Math.min(
                        previousBox.width(),
                        nextBox.width()
                );


        boolean enoughOverlap =
                smallerWidth > 0
                        &&
                        horizontalOverlap > 0
                        &&
                        horizontalOverlap
                                /
                                smallerWidth
                                >= 0.25;


        return similarLeftEdge
                ||
                enoughOverlap;
    }

    private boolean looksLikeAddressStart(
            String text
    ) {

        boolean hasRegion =
                text.contains("시")
                        ||
                        text.contains("군")
                        ||
                        text.contains("구");


        boolean hasAddressToken =
                text.contains("로")
                        ||
                        text.contains("길")
                        ||
                        text.contains("동")
                        ||
                        text.contains("읍")
                        ||
                        text.contains("면");


        return hasRegion
                &&
                hasAddressToken;
    }


    private boolean looksLikeAddressContinuation(
            List<OcrWord> line
    ) {

        String text =
                joinWords(line)
                        .toLowerCase();


        if (text.isBlank()) {
            return false;
        }


        boolean koreanAddressToken =
                text.contains("동")
                        ||
                        text.contains("호")
                        ||
                        text.contains("번지")
                        ||
                        text.contains("아파트")
                        ||
                        text.contains("빌라")
                        ||
                        text.contains("오피스텔");


        boolean englishAddressToken =
                text.contains("apt")
                        ||
                        text.contains("apartment")
                        ||
                        text.contains("unit")
                        ||
                        text.contains("suite");


        return koreanAddressToken
                ||
                englishAddressToken;
    }

    private List<OcrWord> filterAddressColumnWords(
            List<OcrWord> anchorLine,
            List<OcrWord> candidateLine
    ) {

        if (
                anchorLine == null
                        ||
                        anchorLine.isEmpty()
                        ||
                        candidateLine == null
                        ||
                        candidateLine.isEmpty()
        ) {

            return List.of();
        }


        BoundingBox anchorBox =
                union(
                        anchorLine
                );


        double anchorLeft =
                anchorBox.x();


        double anchorRight =
                anchorBox.x()
                        +
                        anchorBox.width();


        /*
         * OCR 행이 조금 들여쓰기되거나
         * 괄호로 시작하는 경우까지 허용하기 위한 여유값.
         *
         * 고정 픽셀만 쓰지 않고
         * 실제 텍스트 높이에 따라 같이 증가시킨다.
         */
        double tolerance =
                Math.max(
                        30.0,
                        anchorBox.height() * 2.0
                );


        return candidateLine.stream()

                .filter(
                        word -> {

                            double wordCenterX =
                                    word.x()
                                            +
                                            word.width() / 2.0;


                            double wordRight =
                                    word.x()
                                            +
                                            word.width();


                            /*
                             * 주소 시작점보다 지나치게 왼쪽에 있는
                             * 얼굴/사진 주변 OCR noise는 제거.
                             */
                            boolean notTooFarLeft =
                                    wordRight
                                            >=
                                            anchorLeft - tolerance;


                            /*
                             * 주소 영역보다 지나치게 오른쪽에 있는
                             * 다른 필드도 제거.
                             */
                            boolean notTooFarRight =
                                    wordCenterX
                                            <=
                                            anchorRight + tolerance;


                            return notTooFarLeft
                                    &&
                                    notTooFarRight;
                        }
                )

                .toList();
    }

    private String joinWords(
            List<OcrWord> words
    ) {

        return words.stream()
                .map(OcrWord::text)
                .reduce(
                        "",
                        String::concat
                );
    }

    private boolean sameVisualLine(
            OcrWord previous,
            OcrWord current
    ) {

        int previousTop =
                previous.y();

        int previousBottom =
                previous.y()
                        + previous.height();


        int currentTop =
                current.y();

        int currentBottom =
                current.y()
                        + current.height();


        /*
         * 두 OCR 단어가 세로 방향으로
         * 실제 얼마나 겹치는지 계산한다.
         */
        int overlap =
                Math.min(
                        previousBottom,
                        currentBottom
                )
                        -
                        Math.max(
                                previousTop,
                                currentTop
                        );


        if (overlap <= 0) {
            return false;
        }


        int minHeight =
                Math.min(
                        previous.height(),
                        current.height()
                );


        if (minHeight <= 0) {
            return false;
        }


        double overlapRatio =
                overlap /
                        (double) minHeight;


        /*
         * 높이의 절반 이상 겹치면
         * 같은 텍스트 행으로 판단.
         */
        return overlapRatio >= 0.5;
    }

    private boolean sameVisualLine(
            List<OcrWord> words
    ) {

        if (
                words == null
                        ||
                        words.size() <= 1
        ) {

            return true;
        }


        for (
                int i = 1;
                i < words.size();
                i++
        ) {

            OcrWord previous =
                    words.get(
                            i - 1
                    );


            OcrWord current =
                    words.get(i);


            if (
                    !sameVisualLine(
                            previous,
                            current
                    )
            ) {

                return false;
            }
        }


        return true;
    }

    private void addAddressLineIfNeeded(
            List<OcrWord> line,
            List<BoundingBox> result
    ) {

        if (line.isEmpty()) {
            return;
        }


        String text =
                line.stream()
                        .map(OcrWord::text)
                        .reduce(
                                "",
                                String::concat
                        );


        boolean hasRegion =
                text.contains("시")
                        || text.contains("군")
                        || text.contains("구");


        boolean hasRoad =
                text.contains("로")
                        || text.contains("길")
                        || text.contains("동");


        if (hasRegion && hasRoad) {

            result.add(
                    union(line)
            );
        }
    }


    private List<BoundingBox>
    findSensitiveDocumentBoxes(
            List<OcrWord> words
    ) {

        List<BoundingBox> result =
                new ArrayList<>();


        for (int start = 0;
             start < words.size();
             start++) {


            for (int length = 1;
                 length <= 6;
                 length++) {


                int end =
                        start + length;


                if (end > words.size()) {
                    break;
                }


                List<OcrWord> group =
                        words.subList(
                                start,
                                end
                        );


                if (!sameVisualLine(group)) {
                    break;
                }

                String text =
                        group.stream()
                                .map(OcrWord::text)
                                .reduce(
                                        "",
                                        String::concat
                                );


                String normalized =
                        text
                                .toLowerCase()
                                .replaceAll(
                                        "[^a-z가-힣]",
                                        ""
                                );


                if (
                        normalized.contains(
                                "driverslicense"
                        )
                                ||
                                normalized.contains(
                                        "운전면허"
                                )
                ) {

                    result.add(
                            union(group)
                    );

                    return result;
                }
            }
        }


        return result;
    }


    private boolean sameLine(
            List<OcrWord> words
    ) {

        if (words.size() <= 1) {
            return true;
        }


        double firstCenter =
                words.get(0).y()
                        + words.get(0).height() / 2.0;


        for (OcrWord word : words) {

            double center =
                    word.y()
                            + word.height() / 2.0;


            if (
                    Math.abs(
                            center - firstCenter
                    ) > 35
            ) {

                return false;
            }
        }


        return true;
    }

    private BoundingBox findSourceTextBox(
            String sourceText,
            List<OcrWord> words,
            boolean expandToFullLine
    ) {

        String target =
                normalizeGroundingText(
                        sourceText
                );


        if (target.isBlank()) {
            return null;
        }


        /*
         * 주소가 여러 줄일 수 있으므로
         * 기존보다 넉넉하게 탐색한다.
         */
        final int maxWords =
                24;


        for (
                int start = 0;
                start < words.size();
                start++
        ) {

            List<OcrWord> group =
                    new ArrayList<>();

            StringBuilder candidateBuilder =
                    new StringBuilder();


            for (
                    int length = 1;
                    length <= maxWords;
                    length++
            ) {

                int index =
                        start + length - 1;


                if (index >= words.size()) {
                    break;
                }


                OcrWord word =
                        words.get(index);


                group.add(word);

                candidateBuilder.append(
                        word.text()
                );


                String candidate =
                        normalizeGroundingText(
                                candidateBuilder.toString()
                        );


                if (candidate.isBlank()) {
                    continue;
                }


                /*
                 * OCR Word들을 순서대로 이어 붙인 결과가
                 * AI가 지정한 sourceText와 일치하면,
                 * 여러 줄에 걸쳐 있어도 전체를 union한다.
                 */
                if (
                        candidate.equals(
                                target
                        )
                ) {

                    List<OcrWord> resolvedGroup =
                            expandToFullLine
                                    ? expandToVisualLine(
                                    group,
                                    words
                            )
                                    : group;


                    return union(
                            resolvedGroup
                    );
                }


                /*
                 * 아직 target의 시작 부분이면
                 * 다음 OCR Word를 계속 붙여 본다.
                 */
                if (
                        target.startsWith(
                                candidate
                        )
                ) {

                    continue;
                }


                /*
                 * target보다 이미 지나치게 길어진 경우만
                 * 현재 시작점 탐색을 종료한다.
                 */
                if (
                        candidate.length()
                                > target.length() + 8
                ) {

                    break;
                }
            }
        }


        return null;
    }

    private List<OcrWord> expandToVisualLine(
            List<OcrWord> matchedGroup,
            List<OcrWord> words
    ) {

        if (
                matchedGroup == null
                        ||
                        matchedGroup.isEmpty()
        ) {

            return matchedGroup;
        }


        List<OcrWord> expanded =
                new ArrayList<>();


        for (OcrWord word : words) {

            boolean sameRow =
                    matchedGroup.stream()
                            .anyMatch(
                                    matchedWord ->
                                            sameVisualLine(
                                                    matchedWord,
                                                    word
                                            )
                            );


            if (sameRow) {

                expanded.add(
                        word
                );
            }
        }


        /*
         * 혹시 확장에 실패하더라도
         * 이미 Grounding에 성공한 원래 그룹은 유지한다.
         */
        if (expanded.isEmpty()) {
            return matchedGroup;
        }


        return expanded;
    }

    private String normalizeGroundingText(
            String text
    ) {

        if (text == null) {
            return "";
        }


        return text
                .toLowerCase()

                /*
                 * Grounding 비교에서는
                 * 공백, 쉼표, 하이픈, 괄호 등의
                 * OCR 분리 차이를 무시한다.
                 *
                 * Unicode 문자/숫자는 유지하므로
                 * 한국어, 영어뿐 아니라 다른 언어 주소에도 대응 가능.
                 */
                .replaceAll(
                        "[^\\p{L}\\p{N}]",
                        ""
                )

                .trim();
    }
    private BoundingBox addSafetyPadding(
            BoundingBox box,
            PiiType type
    ) {

        double horizontalPadding =
                switch (type) {

                    case ADDRESS ->
                            10.0;

                    case PERSON_NAME ->
                            8.0;

                    default ->
                            4.0;
                };


        double verticalPadding =
                switch (type) {

                    case ADDRESS ->
                            6.0;

                    case PERSON_NAME ->
                            5.0;

                    default ->
                            3.0;
                };


        double x =
                Math.max(
                        0,
                        box.x()
                                - horizontalPadding
                );


        double y =
                Math.max(
                        0,
                        box.y()
                                - verticalPadding
                );


        return new BoundingBox(
                x,
                y,
                box.width()
                        + horizontalPadding * 2,
                box.height()
                        + verticalPadding * 2
        );
    }

    private BoundingBox findWordIdBox(
            List<Integer> wordIds,
            List<OcrWord> words
    ) {

        List<OcrWord> selectedWords =
                wordIds.stream()

                        /*
                         * AI가 같은 ID를 두 번 반환해도
                         * 한 번만 사용한다.
                         */
                        .distinct()

                        /*
                         * OCR 원래 읽기 순서로 정렬한다.
                         */
                        .sorted()

                        /*
                         * Validator가 이미 검사하지만
                         * Resolver에서도 한 번 더 방어한다.
                         */
                        .filter(
                                wordId ->
                                        wordId >= 0
                                                &&
                                                wordId < words.size()
                        )

                        /*
                         * ID → 실제 OcrWord 변환.
                         */
                        .map(
                                words::get
                        )

                        .toList();


        if (selectedWords.isEmpty()) {
            return null;
        }


        return union(
                selectedWords
        );
    }

    private BoundingBox union(
            List<OcrWord> words
    ) {

        double minX =
                Double.MAX_VALUE;

        double minY =
                Double.MAX_VALUE;

        double maxX =
                Double.MIN_VALUE;

        double maxY =
                Double.MIN_VALUE;


        for (OcrWord word : words) {

            minX =
                    Math.min(
                            minX,
                            word.x()
                    );

            minY =
                    Math.min(
                            minY,
                            word.y()
                    );

            maxX =
                    Math.max(
                            maxX,
                            word.x()
                                    + word.width()
                    );

            maxY =
                    Math.max(
                            maxY,
                            word.y()
                                    + word.height()
                    );
        }


        return new BoundingBox(
                minX,
                minY,
                maxX - minX,
                maxY - minY
        );
    }


    private String normalize(
            String text
    ) {

        return text
                .replace('–', '-')
                .replace('—', '-')
                .replaceAll(
                        "\\s+",
                        ""
                )
                .trim();
    }
}