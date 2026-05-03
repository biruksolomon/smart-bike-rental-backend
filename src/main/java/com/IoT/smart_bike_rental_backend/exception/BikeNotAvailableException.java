package com.IoT.smart_bike_rental_backend.exception;

public class BikeNotAvailableException extends RuntimeException {
    public BikeNotAvailableException(Long bikeId) {
        super("Bike with id " + bikeId + " is not available for booking");
    }

    public BikeNotAvailableException(String message) {
        super(message);
    }
}
