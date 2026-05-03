package com.IoT.smart_bike_rental_backend.exception;

public class BikeNotFoundException extends ResourceNotFoundException {
    public BikeNotFoundException(Long bikeId) {
        super("Bike not found with id: " + bikeId);
    }

    public BikeNotFoundException(String message) {
        super(message);
    }
}
