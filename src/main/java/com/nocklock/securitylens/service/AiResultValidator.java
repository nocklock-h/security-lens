package com.nocklock.securitylens.service;

import com.nocklock.securitylens.dto.AiAnalysisResponse;
import com.nocklock.securitylens.dto.AiDetectedItem;
import com.nocklock.securitylens.dto.OcrWord;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Component
public class AiResultValidator {

    private static final Set<String> ALLOWED_TYPES =
            Set.of(
                    "PERSON_NAME",
                    "ADDRESS"
            );

    private static final int MAX_WORD_IDS =
            24;


    public List<AiDetectedItem> validate(
            AiAnalysisResponse response,
            List<OcrWord> words
    ) {

        if (
                response == null
                        ||
                        response.detectedItems() == null
                        ||
                        words == null
                        ||
                        words.isEmpty()
        ) {

            return Collections.emptyList();
        }


        return response.detectedItems()
                .stream()
                .filter(
                        item ->
                                isValid(
                                        item,
                                        words
                                )
                )
                .toList();
    }


    private boolean isValid(
            AiDetectedItem item,
            List<OcrWord> words
    ) {

        if (item == null) {
            return false;
        }


        if (
                item.type() == null
                        ||
                        !ALLOWED_TYPES.contains(
                                item.type()
                                        .trim()
                                        .toUpperCase()
                        )
        ) {

            return false;
        }


        if (
                item.wordIds() == null
                        ||
                        item.wordIds().isEmpty()
                        ||
                        item.wordIds().size()
                                > MAX_WORD_IDS
        ) {

            return false;
        }


        /*
         * AI가 반환한 모든 Word ID가
         * 실제 OCR Word 목록 범위 안에 있어야 한다.
         */
        boolean validWordIds =
                item.wordIds()
                        .stream()
                        .allMatch(
                                wordId ->
                                        wordId != null
                                                &&
                                                wordId >= 0
                                                &&
                                                wordId < words.size()
                        );


        if (!validWordIds) {
            return false;
        }


        return item.confidence() >= 0.0
                &&
                item.confidence() <= 1.0;
    }
}