package com.IoT.smart_bike_rental_backend.controller;

import com.IoT.smart_bike_rental_backend.model.Bike;
import com.IoT.smart_bike_rental_backend.repository.Bikerepository;
import com.IoT.smart_bike_rental_backend.service.BikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/bikes")
@RequiredArgsConstructor
public class BikeController {

    private final BikeService bikeService;
    private final Bikerepository bikeRepository;

    @PostMapping
    public Bike createBike(@RequestParam String bikeId,
                           @RequestParam(required = false) String qrCode) {

        Bike bike = new Bike();
        bike.setBikeId(bikeId);
        bike.setQrCode(qrCode != null ? qrCode : bikeId); // Default to bikeId if qrCode not provided
        bike.setStatus("LOCKED");
        bike.setLastUpdated(LocalDateTime.now());

        return bikeRepository.save(bike);
    }


    @GetMapping("/{bikeId}")
    public ResponseEntity<?> getBike(@PathVariable String bikeId) {
        var bike = bikeRepository.findByBikeId(bikeId);
        if (bike.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(bike.get());
    }

    @GetMapping
    public ResponseEntity<?> getAllBikes() {
        return ResponseEntity.ok(bikeRepository.findAll());
    }
    @PostMapping("/{bikeId}/unlock")
    public String unlock(@PathVariable String bikeId) throws Exception {
        bikeService.unlockBike(bikeId);
        return "Unlock command sent";
    }

    @PostMapping("/{bikeId}/lock")
    public String lock(@PathVariable String bikeId) throws Exception {
        bikeService.lockBike(bikeId);
        return "Lock command sent";
    }
}




