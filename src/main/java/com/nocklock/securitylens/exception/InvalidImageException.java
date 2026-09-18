package com.nocklock.securitylens.exception;

public class InvalidImageException extends RuntimeException {

    private final String messageKey;


    public InvalidImageException(
            String messageKey
    ) {

        super(messageKey);

        this.messageKey =
                messageKey;
    }


    public String getMessageKey() {

        return messageKey;
    }
}