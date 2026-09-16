package com.nocklock.securitylens.service;

import com.nocklock.securitylens.dto.AiAnalysisResponse;
import com.nocklock.securitylens.dto.AiDetectedItem;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class AiResultValidator {

    private static final String PERSON_NAME_TYPE = "PERSON_NAME";
    private static final int MAX_SOURCE_TEXT_LENGTH = 100;

    public List<AiDetectedItem> validate(
            AiAnalysisResponse response,
            String extractedText
    ) {
        if (response == null
                || response.detectedItems() == null
                || extractedText == null
                || extractedText.isBlank()) {

            return Collections.emptyList();
        }

        return response.detectedItems().stream()
                .filter(item -> isValid(item, extractedText))
                .toList();
    }

    private boolean isValid(
            AiDetectedItem item,
            String extractedText
    ) {
        if (item == null) {
            return false;
        }

        if (!PERSON_NAME_TYPE.equals(item.type())) {
            return false;
        }

        if (item.sourceText() == null
                || item.sourceText().isBlank()) {
            return false;
        }

        if (item.sourceText().length() > MAX_SOURCE_TEXT_LENGTH) {
            return false;
        }

        if (!extractedText.contains(item.sourceText())) {
            return false;
        }

        return item.confidence() >= 0.0
                && item.confidence() <= 1.0;
    }
}