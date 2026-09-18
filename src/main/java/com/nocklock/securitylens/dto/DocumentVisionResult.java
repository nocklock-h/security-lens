package com.nocklock.securitylens.dto;

import java.util.List;

public record DocumentVisionResult(
        boolean detected,
        String documentType,
        double confidence,
        BoundingBox boundingBox,
        List<DocumentFieldResult> fields
) {

    public static DocumentVisionResult empty() {

        return new DocumentVisionResult(
                false,
                "NONE",
                0.0,
                null,
                List.of()
        );
    }
}