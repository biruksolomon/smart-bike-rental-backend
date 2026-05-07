package com.IoT.smart_bike_rental_backend.dto;

import com.IoT.smart_bike_rental_backend.model.Bike;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BikeStatusResponse {
    private Long id;
    private String bikeId;
    private String qrCode;
    private String status;
    private Boolean isUsable;
    private Boolean isAvailable;
    private Integer batteryLevel;
    private Double latitude;
    private Double longitude;
    private LocalDateTime lastUpdated;
    private String currentUserEmail;

    public static BikeStatusResponse fromBike(Bike bike) {
        return BikeStatusResponse.builder()
                .id(bike.getId())
                .bikeId(bike.getBikeId())
                .qrCode(bike.getQrCode())
                .status(bike.getStatus())
                .isUsable(bike.getIsUsable())
                .isAvailable(bike.isAvailable())
                .batteryLevel(bike.getBatteryLevel())
                .latitude(bike.getLatitude())
                .longitude(bike.getLongitude())
                .lastUpdated(bike.getLastUpdated())
                .currentUserEmail(bike.getCurrentUser() != null ? bike.getCurrentUser().getEmail() : null)
                .build();
    }
}
