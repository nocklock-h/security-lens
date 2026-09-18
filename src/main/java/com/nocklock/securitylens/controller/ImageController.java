package com.nocklock.securitylens.controller;

import com.nocklock.securitylens.dto.AnalysisResponse;
import com.nocklock.securitylens.service.ImageAnalysisService;
import com.nocklock.securitylens.validator.ImageUploadValidator;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/images")
public class ImageController {

    private final ImageAnalysisService imageAnalysisService;
    private final ImageUploadValidator imageUploadValidator;


    public ImageController(
            ImageAnalysisService imageAnalysisService,
            ImageUploadValidator imageUploadValidator
    ) {

        this.imageAnalysisService =
                imageAnalysisService;

        this.imageUploadValidator =
                imageUploadValidator;
    }


    @PostMapping(
            value = "/analyze",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<AnalysisResponse> analyze(
            @RequestPart("image")
            MultipartFile image
    ) {

        imageUploadValidator.validate(
                image
        );


        return ResponseEntity.ok(
                imageAnalysisService.analyze(
                        image
                )
        );
    }


    @PostMapping(
            value = "/analyze/fast",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<AnalysisResponse> analyzeFast(
            @RequestPart("image")
            MultipartFile image
    ) {

        imageUploadValidator.validate(
                image
        );


        return ResponseEntity.ok(
                imageAnalysisService.analyzeFast(
                        image
                )
        );
    }


    @PostMapping(
            value = "/analyze/deep",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<AnalysisResponse> analyzeDeep(
            @RequestPart("image")
            MultipartFile image
    ) {

        imageUploadValidator.validate(
                image
        );


        return ResponseEntity.ok(
                imageAnalysisService.analyze(
                        image
                )
        );
    }
}