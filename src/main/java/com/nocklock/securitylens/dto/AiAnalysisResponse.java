package com.nocklock.securitylens.dto;

import java.util.List;

public record AiAnalysisResponse(
        List<AiDetectedItem> detectedItems
) {
}