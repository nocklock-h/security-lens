package com.nocklock.securitylens.exception;

public class ImageMetadataAnalysisException extends RuntimeException {

    private static final String MESSAGE_KEY =
            "image.metadata.analysisFailed";


    public ImageMetadataAnalysisException(
            Throwable cause
    ) {

        super(
                MESSAGE_KEY,
                cause
        );
    }


    public String getMessageKey() {

        return MESSAGE_KEY;
    }
}