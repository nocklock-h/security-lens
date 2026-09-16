package com.nocklock.securitylens.dto;

public record OllamaGenerateRequest(
        String model,
        String prompt,
        boolean stream,
        String format
) {
}