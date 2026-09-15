package com.nocklock.securitylens.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.nocklock.securitylens.dto.MetadataResult;
import com.nocklock.securitylens.exception.ImageMetadataAnalysisException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Service
public class ImageMetadataService {

    public MetadataResult extract(MultipartFile image) {
        Metadata metadata = readMetadata(image);

        MetadataResult.GpsInfo gps = extractGpsInfo(metadata);
        String capturedAt = extractCapturedAt(metadata);
        MetadataResult.DeviceInfo device = extractDeviceInfo(metadata);

        return new MetadataResult(gps, capturedAt, device);
    }

    private Metadata readMetadata(MultipartFile image) {
        try (InputStream inputStream = image.getInputStream()) {
            return ImageMetadataReader.readMetadata(inputStream);
        } catch (ImageProcessingException | IOException e) {
            throw new ImageMetadataAnalysisException("이미지 메타데이터 분석에 실패했습니다.", e);
        }
    }

    private MetadataResult.GpsInfo extractGpsInfo(Metadata metadata) {
        GpsDirectory gpsDirectory =
                metadata.getFirstDirectoryOfType(GpsDirectory.class);

        if (gpsDirectory == null || gpsDirectory.getGeoLocation() == null) {
            return new MetadataResult.GpsInfo(false, null, null);
        }

        double latitude = gpsDirectory.getGeoLocation().getLatitude();
        double longitude = gpsDirectory.getGeoLocation().getLongitude();

        return new MetadataResult.GpsInfo(true, latitude, longitude);
    }

    private String extractCapturedAt(Metadata metadata) {
        ExifSubIFDDirectory exifSubIFDDirectory =
                metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);

        if (exifSubIFDDirectory != null) {
            String dateOriginal =
                    exifSubIFDDirectory.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);

            if (dateOriginal != null) {
                return dateOriginal;
            }
        }

        ExifIFD0Directory exifIFD0Directory =
                metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);

        if (exifIFD0Directory != null) {
            return exifIFD0Directory.getString(ExifIFD0Directory.TAG_DATETIME);
        }

        return null;
    }

    private MetadataResult.DeviceInfo extractDeviceInfo(Metadata metadata) {
        ExifIFD0Directory exifIFD0Directory =
                metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);

        if (exifIFD0Directory == null) {
            return new MetadataResult.DeviceInfo(false, null, null);
        }

        String make =
                exifIFD0Directory.getString(ExifIFD0Directory.TAG_MAKE);

        String model =
                exifIFD0Directory.getString(ExifIFD0Directory.TAG_MODEL);

        boolean detected = make != null || model != null;

        return new MetadataResult.DeviceInfo(detected, make, model);
    }
}
