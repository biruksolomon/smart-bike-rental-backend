package com.IoT.smart_bike_rental_backend.service;

import com.IoT.smart_bike_rental_backend.model.Ride;
import com.IoT.smart_bike_rental_backend.mqtt.MqttService;
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

    public void unlockBike(String bikeId) throws Exception {

        mqttService.publish("bike/" + bikeId + "/command", "UNLOCK");

        Ride ride = new Ride();
        ride.setBikeId(bikeId);
        ride.setStartTime(LocalDateTime.now());
        ride.setActive(true);

        rideRepository.save(ride);
    }

    public void lockBike(String bikeId) throws Exception {

        mqttService.publish("bike/" + bikeId + "/command", "LOCK");

        Ride ride = rideRepository.findByBikeIdAndActiveTrue(bikeId);

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