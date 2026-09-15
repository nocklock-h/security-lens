package com.nocklock.securitylens.controller;

import com.nocklock.securitylens.dto.AnalysisResponse;
import com.nocklock.securitylens.dto.ApiErrorResponse;
import com.nocklock.securitylens.service.ImageAnalysisService;
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

    public ImageController(ImageAnalysisService imageAnalysisService) {
        this.imageAnalysisService = imageAnalysisService;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> analyze(@RequestPart("image") MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ApiErrorResponse("EMPTY_IMAGE", "이미지가 첨부되지 않았습니다."));
        }

        AnalysisResponse response = imageAnalysisService.analyze(image);
        return ResponseEntity.ok(response);
    }
}
