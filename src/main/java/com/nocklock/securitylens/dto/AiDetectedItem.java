package com.nocklock.securitylens.dto;

import java.util.List;

public record AiDetectedItem(
        String type,
        List<Integer> wordIds,
        double confidence
) {
}