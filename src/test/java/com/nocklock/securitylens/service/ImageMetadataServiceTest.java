package com.nocklock.securitylens.service;

import com.nocklock.securitylens.dto.MetadataResult;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class ImageMetadataServiceTest {

    private static final String MINIMAL_PNG_WITHOUT_METADATA_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";

    private final ImageMetadataService imageMetadataService = new ImageMetadataService();

    @Test
    void returnsAllFalse_whenImageHasNoMetadata() {
        byte[] pngBytes =
                Base64.getDecoder().decode(MINIMAL_PNG_WITHOUT_METADATA_BASE64);

        MockMultipartFile image =
                new MockMultipartFile(
                        "image",
                        "no-exif.png",
                        "image/png",
                        pngBytes
                );

        MetadataResult result = imageMetadataService.extract(image);

        assertFalse(result.gps().detected());
        assertNull(result.gps().latitude());
        assertNull(result.gps().longitude());

        assertNull(result.capturedAt());

        assertFalse(result.device().detected());
        assertNull(result.device().make());
        assertNull(result.device().model());
    }
}
