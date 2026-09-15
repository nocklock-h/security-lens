package com.nocklock.securitylens.dto;

public record ApiErrorResponse(
        String code,
        String message
) {
}
