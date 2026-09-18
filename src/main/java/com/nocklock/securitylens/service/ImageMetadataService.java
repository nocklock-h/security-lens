package com.nocklock.securitylens.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.lang.GeoLocation;
import com.drew.lang.Rational;
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
        } catch (
                ImageProcessingException |
                IOException e
        ) {

            throw new ImageMetadataAnalysisException(
                    e
            );
        }
    }

    private MetadataResult.GpsInfo extractGpsInfo(Metadata metadata) {
        GpsDirectory gpsDirectory =
                metadata.getFirstDirectoryOfType(GpsDirectory.class);

        if (gpsDirectory == null) {
            return new MetadataResult.GpsInfo(false, null, null);
        }

        String latitudeRef =
                gpsDirectory.getString(GpsDirectory.TAG_LATITUDE_REF);

        String longitudeRef =
                gpsDirectory.getString(GpsDirectory.TAG_LONGITUDE_REF);

        Rational[] latitudeValues =
                gpsDirectory.getRationalArray(GpsDirectory.TAG_LATITUDE);

        Rational[] longitudeValues =
                gpsDirectory.getRationalArray(GpsDirectory.TAG_LONGITUDE);

        if (latitudeRef == null
                || latitudeRef.isBlank()
                || longitudeRef == null
                || longitudeRef.isBlank()
                || !isValidGpsValues(latitudeValues)
                || !isValidGpsValues(longitudeValues)) {

            return new MetadataResult.GpsInfo(false, null, null);
        }

        GeoLocation geoLocation = gpsDirectory.getGeoLocation();

        if (geoLocation == null) {
            return new MetadataResult.GpsInfo(false, null, null);
        }

        return new MetadataResult.GpsInfo(
                true,
                geoLocation.getLatitude(),
                geoLocation.getLongitude()
        );
    }

    private boolean isValidGpsValues(Rational[] values) {
        if (values == null || values.length != 3) {
            return false;
        }

        for (Rational value : values) {
            if (value == null || value.getDenominator() == 0) {
                return false;
            }
        }

        return true;
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
