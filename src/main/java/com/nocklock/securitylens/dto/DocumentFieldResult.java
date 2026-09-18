package com.nocklock.securitylens.dto;

public record DocumentFieldResult(
        String type,
        double confidence,
        BoundingBox boundingBox
) {
}