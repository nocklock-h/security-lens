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


    public List<DetectedItem> attach(
            List<DetectedItem> detectedItems,
            List<OcrWord> words
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
                    boxes.get(item.type());


            BoundingBox boundingBox =
                    candidates == null
                            ? null
                            : candidates.poll();


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


                if (!sameLine(group)) {
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

        List<OcrWord> line =
                new ArrayList<>();


        for (OcrWord word : words) {

            if (
                    line.isEmpty() ||
                            sameLine(
                                    List.of(
                                            line.get(
                                                    line.size() - 1
                                            ),
                                            word
                                    )
                            )
            ) {

                line.add(word);

            } else {

                addAddressLineIfNeeded(
                        line,
                        result
                );

                line =
                        new ArrayList<>();

                line.add(word);
            }
        }


        addAddressLineIfNeeded(
                line,
                result
        );


        return result;
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


                if (!sameLine(group)) {
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