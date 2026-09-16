package com.nocklock.securitylens.exception;

import org.springframework.http.HttpStatus;

public enum OcrErrorCode {

    UNSUPPORTED_IMAGE(
            "OCR_UNSUPPORTED_IMAGE",
            "ocr.unsupported-image",
            HttpStatus.BAD_REQUEST
    ),

    ANALYSIS_FAILED(
            "OCR_ANALYSIS_FAILED",
            "ocr.analysis-failed",
            HttpStatus.INTERNAL_SERVER_ERROR
    );

    private final String code;
    private final String messageKey;
    private final HttpStatus status;

    OcrErrorCode(
            String code,
            String messageKey,
            HttpStatus status
    ) {
        this.code = code;
        this.messageKey = messageKey;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public HttpStatus getStatus() {
        return status;
    }
}