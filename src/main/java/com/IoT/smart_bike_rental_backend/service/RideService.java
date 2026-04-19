package com.IoT.smart_bike_rental_backend.service;

import com.IoT.smart_bike_rental_backend.model.Bike;
import com.IoT.smart_bike_rental_backend.model.Ride;
import com.IoT.smart_bike_rental_backend.model.User;
import com.IoT.smart_bike_rental_backend.mqtt.MqttService;
import com.IoT.smart_bike_rental_backend.repository.Bikerepository;
import com.IoT.smart_bike_rental_backend.repository.Riderepository;
import com.IoT.smart_bike_rental_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RideService {

    private final Riderepository rideRepository;
    private final Bikerepository bikeRepository;
    private final UserRepository userRepository;
    private final MqttService mqttService;

    private static final Double PRICE_PER_MINUTE = 0.5;

    public Ride startRide(Long userId, String qrCode) {
        // Validate user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Validate bike exists with QR code
        Bike bike = bikeRepository.findByQrCode(qrCode)
                .orElseThrow(() -> new IllegalArgumentException("Bike not found"));

        // Validate bike is available (LOCKED status)
        if (!"LOCKED".equals(bike.getStatus())) {
            throw new IllegalArgumentException("Bike is not available for rent");
        }

        // Create ride record
        Ride ride = new Ride();
        ride.setUser(user);
        ride.setBike(bike);
        ride.setStartTime(LocalDateTime.now());
        ride.setActive(true);
        ride.setCost(0.0);

        Ride savedRide = rideRepository.save(ride);

        // Update bike status to IN_USE and set current user
        bike.setStatus("IN_USE");
        bike.setCurrentUser(user);
        bikeRepository.save(bike);

        // Send MQTT command to ESP32 to unlock bike
        try {
            mqttService.publish("bike/" + bike.getBikeId() + "/command", "UNLOCK");
        } catch (Exception e) {
            System.err.println("Failed to send unlock command: " + e.getMessage());
        }

        return savedRide;
    }

    public Ride endRide(Long rideId) {
        // Find active ride
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new IllegalArgumentException("Ride not found"));

        if (!ride.isActive()) {
            throw new IllegalArgumentException("Ride is already completed");
        }

        // Set end time and calculate cost
        ride.setEndTime(LocalDateTime.now());
        ride.setActive(false);

        long minutes = Duration.between(ride.getStartTime(), ride.getEndTime()).toMinutes();
        double cost = minutes * PRICE_PER_MINUTE;
        ride.setCost(cost);

        Ride savedRide = rideRepository.save(ride);

        // Update bike status back to LOCKED
        Bike bike = ride.getBike();
        bike.setStatus("LOCKED");
        bike.setCurrentUser(null);
        bike.setLastUpdated(LocalDateTime.now());
        bikeRepository.save(bike);

        // Send MQTT command to ESP32 to lock bike
        try {
            mqttService.publish("bike/" + bike.getBikeId() + "/command", "LOCK");
        } catch (Exception e) {
            System.err.println("Failed to send lock command: " + e.getMessage());
        }

        return savedRide;
    }

    public List<Ride> getUserRideHistory(Long userId) {
        // Validate user exists
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return rideRepository.findByUserIdOrderByStartTimeDesc(userId);
    }

    public Ride getRide(Long rideId) {
        return rideRepository.findById(rideId)
                .orElseThrow(() -> new IllegalArgumentException("Ride not found"));
    }
}
