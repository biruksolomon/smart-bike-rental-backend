package com.IoT.smart_bike_rental_backend.controller;

import com.IoT.smart_bike_rental_backend.model.Bike;
import com.IoT.smart_bike_rental_backend.repository.Bikerepository;
import com.IoT.smart_bike_rental_backend.service.BikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/bikes")
@RequiredArgsConstructor
@Tag(name = "Bikes", description = "Bike management and control endpoints")
public class BikeController {

    private final BikeService bikeService;
    private final Bikerepository bikeRepository;

    /**
     * Create a new bike
     * POST /api/bikes?bikeId=...&qrCode=...
     */
    @PostMapping
    @Operation(summary = "Create a new bike", description = "Add a new bike to the rental system")
    @ApiResponse(responseCode = "200", description = "Bike created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid bike ID or duplicate bike")
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
    @Operation(summary = "Get bike details", description = "Retrieve bike information by bike ID")
    @ApiResponse(responseCode = "200", description = "Bike found")
    @ApiResponse(responseCode = "404", description = "Bike not found")
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
    @Operation(summary = "Get all bikes", description = "Retrieve list of all bikes in the system")
    @ApiResponse(responseCode = "200", description = "List of bikes returned")
    public ResponseEntity<?> getAllBikes() {
        return ResponseEntity.ok(bikeRepository.findAll());
    }

    /**
     * Unlock a bike via MQTT
     * POST /api/bikes/{bikeId}/unlock
     */
    @PostMapping("/{bikeId}/unlock")
    @Operation(summary = "Unlock a bike", description = "Send MQTT unlock command to bike")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Unlock command sent successfully")
    @ApiResponse(responseCode = "400", description = "Failed to send unlock command")
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
    @Operation(summary = "Lock a bike", description = "Send MQTT lock command to bike")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Lock command sent successfully")
    @ApiResponse(responseCode = "400", description = "Failed to send lock command")
    public ResponseEntity<?> lock(@PathVariable String bikeId) {
        try {
            bikeService.lockBike(bikeId);
            return ResponseEntity.ok("Lock command sent");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to send lock command: " + e.getMessage());
        }
    }
}




