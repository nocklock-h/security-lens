package com.nocklock.securitylens.service;

import com.nocklock.securitylens.domain.InferenceLevel;
import com.nocklock.securitylens.domain.PiiType;
import com.nocklock.securitylens.domain.RiskLevel;
import com.nocklock.securitylens.dto.AnalysisResponse;
import com.nocklock.securitylens.dto.BoundingBox;
import com.nocklock.securitylens.dto.DetectedItem;
import com.nocklock.securitylens.dto.ImageInfo;
import com.nocklock.securitylens.dto.InferenceResult;
import com.nocklock.securitylens.dto.MetadataResult;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class ImageAnalysisService {

    private final ImageMetadataService imageMetadataService;

    public ImageAnalysisService(ImageMetadataService imageMetadataService) {
        this.imageMetadataService = imageMetadataService;
    }

    public AnalysisResponse analyze(MultipartFile image) {
        MetadataResult metadataResult = imageMetadataService.extract(image);

        return new AnalysisResponse(
                82,
                RiskLevel.CRITICAL,
                new ImageInfo(1920, 1080),
                List.of(
                        new DetectedItem(
                                "pii-1",
                                PiiType.PHONE,
                                "010-****-1234",
                                0.98,
                                new BoundingBox(0.10, 0.22, 0.25, 0.04)
                        ),
                        new DetectedItem(
                                "pii-2",
                                PiiType.ADDRESS,
                                "서울특별시 서초구 ***",
                                0.93,
                                new BoundingBox(0.08, 0.34, 0.42, 0.05)
                        )
                ),
                metadataResult,
                List.of(
                        new InferenceResult(
                                "RESIDENCE_LOCATION",
                                InferenceLevel.HIGH,
                                "주소와 위치정보가 함께 포함되어 있어 거주지역이 추정될 가능성이 높습니다."
                        )
                ),
                "이 이미지는 개인정보 노출 위험이 높습니다."
        );
    }
}
