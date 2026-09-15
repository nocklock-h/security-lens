package com.nocklock.securitylens.dto;

import com.nocklock.securitylens.domain.RiskLevel;

import java.util.List;

public record AnalysisResponse(
        int riskScore,
        RiskLevel riskLevel,
        ImageInfo image,
        List<DetectedItem> detectedItems,
        MetadataResult metadata,
        List<InferenceResult> inferences,
        String summary
) {
}
