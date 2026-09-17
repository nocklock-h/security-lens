package com.nocklock.securitylens.dto;

import java.util.List;
import java.util.Map;

public record AiPiiDetectionResult(
        List<DetectedItem> detectedItems,
        Map<String, List<Integer>> wordIdsById
) {

    public static AiPiiDetectionResult empty() {

        return new AiPiiDetectionResult(
                List.of(),
                Map.of()
        );
    }
}