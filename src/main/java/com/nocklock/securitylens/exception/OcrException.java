package com.nocklock.securitylens.exception;

import org.springframework.http.HttpStatus;

public class OcrException extends RuntimeException {

    private final String code;
    private final String messageKey;
    private final HttpStatus status;

    public OcrException(OcrErrorCode errorCode) {
        super(errorCode.getMessageKey());

        this.code = errorCode.getCode();
        this.messageKey = errorCode.getMessageKey();
        this.status = errorCode.getStatus();
    }

    public OcrException(
            OcrErrorCode errorCode,
            Throwable cause
    ) {
        super(errorCode.getMessageKey(), cause);

        this.code = errorCode.getCode();
        this.messageKey = errorCode.getMessageKey();
        this.status = errorCode.getStatus();
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