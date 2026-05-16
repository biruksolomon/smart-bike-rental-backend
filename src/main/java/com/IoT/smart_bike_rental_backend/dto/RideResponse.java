package com.IoT.smart_bike_rental_backend.dto;

import com.IoT.smart_bike_rental_backend.model.Ride;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideResponse {
    private Long rideId;
    private Long userId;
    private String userName;
    private String userEmail;
    private String bikeId;
    private String qrCode;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMinutes;
    private BigDecimal cost;
    private String paymentStatus;
    private boolean active;
    private String bikeStatus;
    private String message;
    private String checkoutUrl;

    public static RideResponse fromRide(Ride ride) {
        return fromRide(ride, null);
    }

    public static RideResponse fromRide(Ride ride, String message) {
        return RideResponse.builder()
                .rideId(ride.getId())
                .userId(ride.getUser() != null ? ride.getUser().getId() : null)
                .userName(ride.getUser() != null ? ride.getUser().getName() : null)
                .userEmail(ride.getUser() != null ? ride.getUser().getEmail() : null)
                .bikeId(ride.getBike() != null ? ride.getBike().getBikeId() : null)
                .qrCode(ride.getBike() != null ? ride.getBike().getQrCode() : null)
                .startTime(ride.getStartTime())
                .endTime(ride.getEndTime())
                .durationMinutes(ride.getDurationMinutes())
                .cost(ride.getCost())
                .paymentStatus(ride.getPaymentStatus())
                .active(ride.isActive())
                .bikeStatus(ride.getBike() != null ? ride.getBike().getStatus() : null)
                .message(message)
                .build();
    }

    public static RideResponse fromRideWithCheckout(Ride ride, String message, String checkoutUrl) {
        RideResponse r = fromRide(ride, message);
        r.setCheckoutUrl(checkoutUrl);
        return r;
    }
}
