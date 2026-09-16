package com.nocklock.securitylens.dto;

public record OcrWord(
        String text,
        double confidence,
        int x,
        int y,
        int width,
        int height
) {
}