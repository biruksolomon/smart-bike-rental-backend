package com.IoT.smart_bike_rental_backend.dto;

import lombok.Data;

@Data
public class EndRideRequest {
    private Long rideId;
    private Double endLatitude;
    private Double endLongitude;
}
