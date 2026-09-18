package com.nocklock.securitylens.service;

import com.nocklock.securitylens.domain.PiiType;
import com.nocklock.securitylens.domain.RiskLevel;
import com.nocklock.securitylens.dto.DetectedItem;
import com.nocklock.securitylens.dto.MetadataResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RiskScoreCalculator {

    private static final int MAX_SCORE = 100;

    private static final int PHONE_SCORE = 20;
    private static final int EMAIL_SCORE = 10;
    private static final int ADDRESS_SCORE = 25;
    private static final int PERSON_NAME_SCORE = 10;
    private static final int RRN_SCORE = 75;
    private static final int DRIVER_LICENSE_NUMBER_SCORE = 60;
    private static final int SENSITIVE_DOCUMENT_SCORE = 50;
    private static final int ACCOUNT_NUMBER_SCORE = 50;
    private static final int LICENSE_PLATE_SCORE = 15;
    private static final int QR_CODE_SCORE = 10;
    private static final int BARCODE_SCORE = 5;

    private static final int GPS_SCORE = 30;
    private static final int CAPTURED_AT_SCORE = 5;
    private static final int DEVICE_SCORE = 5;

    private static final int PHONE_ADDRESS_COMBINATION_SCORE = 10;
    private static final int NAME_ADDRESS_COMBINATION_SCORE = 10;
    private static final int NAME_PHONE_COMBINATION_SCORE = 10;
    private static final int ADDRESS_GPS_COMBINATION_SCORE = 15;

    private static final int PASSPORT_NUMBER_SCORE = 35;
    private static final int DATE_OF_BIRTH_SCORE = 20;

    public int calculate(
            List<DetectedItem> detectedItems,
            MetadataResult metadata
    ) {
        Set<PiiType> detectedTypes = detectedItems.stream()
                .map(DetectedItem::type)
                .collect(Collectors.toSet());

        int score = calculatePiiScore(detectedTypes);
        score += calculateMetadataScore(metadata);
        score += calculateCombinationScore(detectedTypes, metadata);

        return Math.min(score, MAX_SCORE);
    }

    public RiskLevel determineLevel(int score) {
        if (score >= 75) {
            return RiskLevel.CRITICAL;
        }

        if (score >= 50) {
            return RiskLevel.HIGH;
        }

        if (score >= 25) {
            return RiskLevel.MEDIUM;
        }

        return RiskLevel.LOW;
    }

    private int calculatePiiScore(Set<PiiType> detectedTypes) {
        int score = 0;

        if (detectedTypes.contains(PiiType.PHONE)) {
            score += PHONE_SCORE;
        }

        if (detectedTypes.contains(PiiType.EMAIL)) {
            score += EMAIL_SCORE;
        }

        if (detectedTypes.contains(PiiType.ADDRESS)) {
            score += ADDRESS_SCORE;
        }

        if (detectedTypes.contains(PiiType.PERSON_NAME)) {
            score += PERSON_NAME_SCORE;
        }

        if (detectedTypes.contains(PiiType.RRN)) {
            score += RRN_SCORE;
        }

        if (
                detectedTypes.contains(
                        PiiType.DRIVER_LICENSE_NUMBER
                )
        ) {
            score += DRIVER_LICENSE_NUMBER_SCORE;
        }


        if (
                detectedTypes.contains(
                        PiiType.SENSITIVE_DOCUMENT
                )
        ) {
            score += SENSITIVE_DOCUMENT_SCORE;
        }

        if (
                detectedTypes.contains(
                        PiiType.PASSPORT_NUMBER
                )
        ) {
            score +=
                    PASSPORT_NUMBER_SCORE;
        }


        if (
                detectedTypes.contains(
                        PiiType.DATE_OF_BIRTH
                )
        ) {
            score +=
                    DATE_OF_BIRTH_SCORE;
        }

        if (detectedTypes.contains(PiiType.ACCOUNT_NUMBER)) {
            score += ACCOUNT_NUMBER_SCORE;
        }

        if (detectedTypes.contains(PiiType.LICENSE_PLATE)) {
            score += LICENSE_PLATE_SCORE;
        }

        if (detectedTypes.contains(PiiType.QR_CODE)) {
            score += QR_CODE_SCORE;
        }

        if (detectedTypes.contains(PiiType.BARCODE)) {
            score += BARCODE_SCORE;
        }

        return score;
    }

    private int calculateMetadataScore(MetadataResult metadata) {
        int score = 0;

        if (metadata.gps().detected()) {
            score += GPS_SCORE;
        }

        if (metadata.capturedAt() != null) {
            score += CAPTURED_AT_SCORE;
        }

        if (metadata.device().detected()) {
            score += DEVICE_SCORE;
        }

        return score;
    }

    private int calculateCombinationScore(
            Set<PiiType> detectedTypes,
            MetadataResult metadata
    ) {
        int score = 0;

        boolean hasPhone = detectedTypes.contains(PiiType.PHONE);
        boolean hasAddress = detectedTypes.contains(PiiType.ADDRESS);
        boolean hasName = detectedTypes.contains(PiiType.PERSON_NAME);
        boolean hasGps = metadata.gps().detected();

        if (hasPhone && hasAddress) {
            score += PHONE_ADDRESS_COMBINATION_SCORE;
        }

        if (hasName && hasAddress) {
            score += NAME_ADDRESS_COMBINATION_SCORE;
        }

        if (hasName && hasPhone) {
            score += NAME_PHONE_COMBINATION_SCORE;
        }

        if (hasAddress && hasGps) {
            score += ADDRESS_GPS_COMBINATION_SCORE;
        }

        return score;
    }
}