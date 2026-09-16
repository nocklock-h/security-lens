package com.nocklock.securitylens.exception;

import com.nocklock.securitylens.dto.ApiErrorResponse;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource){
        this.messageSource = messageSource;
    }

    @ExceptionHandler(OcrException.class)
    public ResponseEntity<ApiErrorResponse> handleOcrException(
            OcrException e,
            Locale locale
    ) {
        String message = messageSource.getMessage(
                e.getMessageKey(),
                null,
                locale
        );

        return ResponseEntity
                .status(e.getStatus())
                .body(
                        new ApiErrorResponse(
                                e.getCode(),
                                message
                        )
                );
    }

}
