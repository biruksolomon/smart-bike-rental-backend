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

    /**
     * Create a new bike
     * POST /api/bikes?bikeId=...&qrCode=...
     */
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

    /**
     * Get bike by ID
     * GET /api/bikes/{bikeId}
     */
    @GetMapping("/{bikeId}")
    public ResponseEntity<?> getBike(@PathVariable String bikeId) {
        var bike = bikeRepository.findByBikeId(bikeId);
        if (bike.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(bike.get());
    }

    /**
     * Get all bikes
     * GET /api/bikes
     */
    @GetMapping
    public ResponseEntity<?> getAllBikes() {
        return ResponseEntity.ok(bikeRepository.findAll());
    }

    /**
     * Unlock a bike via MQTT
     * POST /api/bikes/{bikeId}/unlock
     */
    @PostMapping("/{bikeId}/unlock")
    public ResponseEntity<?> unlock(@PathVariable String bikeId) {
        try {
            bikeService.unlockBike(bikeId);
            return ResponseEntity.ok("Unlock command sent");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to send unlock command: " + e.getMessage());
        }
    }

    /**
     * Lock a bike via MQTT
     * POST /api/bikes/{bikeId}/lock
     */
    @PostMapping("/{bikeId}/lock")
    public ResponseEntity<?> lock(@PathVariable String bikeId) {
        try {
            bikeService.lockBike(bikeId);
            return ResponseEntity.ok("Lock command sent");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to send lock command: " + e.getMessage());
        }
    }
}




