package com.nocklock.securitylens.dto;

import com.nocklock.securitylens.domain.InferenceLevel;

public record InferenceResult(
        String type,
        InferenceLevel level,
        String description
) {
}
