package com.nocklock.securitylens.dto;

public record AiDetectedItem(
        String type,
        String sourceText,
        double confidence
) {
}