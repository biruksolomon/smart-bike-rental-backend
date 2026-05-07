package com.IoT.smart_bike_rental_backend.dto;

import lombok.Data;

@Data
public class StartRideRequest {
    private Long userId;
    private String qrCode;

    // Optional GPS coordinates for start location
    private Double startLatitude;
    private Double startLongitude;
}
