package com.IoT.smart_bike_rental_backend.controller;

import com.IoT.smart_bike_rental_backend.model.Bike;
import com.IoT.smart_bike_rental_backend.repository.Bikerepository;
import com.IoT.smart_bike_rental_backend.service.BikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/bikes")
@RequiredArgsConstructor
public class BikeController {

    private final BikeService bikeService;
    private final Bikerepository bikerepository;

    @PostMapping
    public Bike createBike(@RequestParam String bikeId) {

        Bike bike = new Bike();
        bike.setBikeId(bikeId);
        bike.setStatus("LOCKED");
        bike.setLastUpdated(LocalDateTime.now());

        return bikerepository.save(bike);
    }

    @PostMapping("/{bikeId}/unlock")
    public String unlock(@PathVariable String bikeId) throws Exception {
        bikeService.unlockBike(bikeId);
        return "Unlock command sent";
    }

    @PostMapping("/{bikeId}/lock")
    public String lock(@PathVariable Long bikeId) throws Exception {
        bikeService.lockBike(bikeId);
        return "Lock command sent";
    }
}