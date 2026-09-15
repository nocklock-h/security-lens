package com.nocklock.securitylens.dto;

import com.nocklock.securitylens.domain.PiiType;

public record DetectedItem(
        String id,
        PiiType type,
        String maskedValue,
        double confidence,
        BoundingBox boundingBox
) {
}
