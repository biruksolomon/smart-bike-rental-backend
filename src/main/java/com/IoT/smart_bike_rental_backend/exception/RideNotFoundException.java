package com.IoT.smart_bike_rental_backend.exception;

public class RideNotFoundException extends ResourceNotFoundException {
    public RideNotFoundException(Long rideId) {
        super("Ride not found with id: " + rideId);
    }

    public RideNotFoundException(String message) {
        super(message);
    }
}
