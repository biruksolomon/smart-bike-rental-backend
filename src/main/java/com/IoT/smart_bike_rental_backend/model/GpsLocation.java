package com.IoT.smart_bike_rental_backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a GPS coordinate with latitude and longitude.
 * Used to track bike locations during rides.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
    public class GpsLocation {
    private double latitude;
    private double longitude;
}