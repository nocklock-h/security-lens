package com.nocklock.securitylens.dto;

public record MetadataResult(
        boolean gpsDetected,
        boolean capturedAtDetected,
        boolean deviceInfoDetected
) {
}
