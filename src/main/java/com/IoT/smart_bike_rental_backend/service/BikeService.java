package com.IoT.smart_bike_rental_backend.service;

import com.IoT.smart_bike_rental_backend.model.Bike;
import com.IoT.smart_bike_rental_backend.model.Ride;
import com.IoT.smart_bike_rental_backend.mqtt.MqttService;
import com.IoT.smart_bike_rental_backend.repository.Bikerepository;
import com.IoT.smart_bike_rental_backend.repository.Riderepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BikeService {

    private final MqttService mqttService;
    private final Riderepository rideRepository;
    private final Bikerepository bikeRepository;

    public void unlockBike(String bikeId) throws Exception {

        mqttService.publish("bike/" + bikeId + "/command", "UNLOCK");

        Ride ride = new Ride();
        ride.setStartTime(LocalDateTime.now());
        ride.setActive(true);

        rideRepository.save(ride);
    }

    public void lockBike(String bikeId) throws Exception {

        mqttService.publish("bike/" + bikeId + "/command", "LOCK");

        Bike bike = bikeRepository.findByBikeId(bikeId)
                .orElseThrow(() -> new IllegalArgumentException("Bike not found"));

        Ride ride = rideRepository.findByBikeIdAndActiveTrue(bike.getId())
                .orElseThrow(() -> new IllegalArgumentException("No active ride found"));

        ride.setEndTime(LocalDateTime.now());
        ride.setActive(false);

        long minutes = Duration.between(
                ride.getStartTime(),
                ride.getEndTime()
        ).toMinutes();

        ride.setCost(minutes * 0.5);

        rideRepository.save(ride);
    }
}
