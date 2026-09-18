package com.nocklock.securitylens.exception;

import com.nocklock.securitylens.dto.ApiErrorResponse;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Locale;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MessageSource messageSource;


    public GlobalExceptionHandler(
            MessageSource messageSource
    ) {
        this.messageSource =
                messageSource;
    }


    @ExceptionHandler(OcrException.class)
    public ResponseEntity<ApiErrorResponse> handleOcrException(
            OcrException exception,
            Locale locale
    ) {

        String message =
                getMessage(
                        exception.getMessageKey(),
                        locale
                );


        return ResponseEntity
                .status(exception.getStatus())
                .body(
                        new ApiErrorResponse(
                                exception.getCode(),
                                message
                        )
                );
    }


    @ExceptionHandler(InvalidImageException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidImage(
            InvalidImageException exception,
            Locale locale
    ) {

        String message =
                getMessage(
                        exception.getMessageKey(),
                        locale
                );


        return ResponseEntity
                .badRequest()
                .body(
                        new ApiErrorResponse(
                                "INVALID_IMAGE",
                                message
                        )
                );
    }


    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(
            MaxUploadSizeExceededException exception,
            Locale locale
    ) {

        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(
                        new ApiErrorResponse(
                                "IMAGE_TOO_LARGE",
                                getMessage(
                                        "upload.image.tooLarge",
                                        locale
                                )
                        )
                );
    }


    @ExceptionHandler(ImageMetadataAnalysisException.class)
    public ResponseEntity<ApiErrorResponse> handleMetadataAnalysis(
            ImageMetadataAnalysisException exception,
            Locale locale
    ) {

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        new ApiErrorResponse(
                                "IMAGE_METADATA_ANALYSIS_FAILED",
                                getMessage(
                                        exception.getMessageKey(),
                                        locale
                                )
                        )
                );
    }


    private String getMessage(
            String key,
            Locale locale
    ) {

        return messageSource.getMessage(
                key,
                null,
                locale
        );
    }
}