package com.nocklock.securitylens.dto;

public record MetadataResult(
            GpsInfo gps,
            String capturedAt,
            DeviceInfo device
){
    public record GpsInfo(
            boolean detected,
            Double latitude,
            Double longitude
    ){

    }

    public record DeviceInfo(
            boolean detected,
            String make,
            String model
    )
    {
    }
}
