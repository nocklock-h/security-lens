package com.nocklock.securitylens.dto;

import java.util.List;

public record OcrResult(
        String text,
        List<OcrWord> words
) {
}