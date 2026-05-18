package com.IoT.smart_bike_rental_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response returned when GPS-based distance calculation is completed.
 * Contains the calculated distance in km and the total payment amount.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RideEndResponse {
    private double distanceKm;
    private BigDecimal totalPayment;
}