package com.IoT.smart_bike_rental_backend.controller;

import com.IoT.smart_bike_rental_backend.dto.ApiResponse;
import com.IoT.smart_bike_rental_backend.dto.BikeStatusResponse;
import com.IoT.smart_bike_rental_backend.model.Bike;
import com.IoT.smart_bike_rental_backend.service.BikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bikes")
@RequiredArgsConstructor
@Tag(name = "Bikes", description = "Bike management and control endpoints")
@Slf4j
public class BikeController {

    private final BikeService bikeService;

    /**
     * Create a new bike in the system
     */
    @PostMapping
    @Operation(
            summary = "Create a new bike",
            description = "Register a new bike in the rental system with a unique bike ID and QR code"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Bike created successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bike ID already exists")
    })
    public ResponseEntity<?> createBike(
            @RequestParam String bikeId,
            @RequestParam(required = false) String qrCode) {
        try {
            Bike bike = bikeService.createBike(bikeId, qrCode);
            return ResponseEntity.ok(ApiResponse.success("Bike created successfully", bike));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Get bike status by bike ID
     */
    @GetMapping("/{bikeId}")
    @Operation(
            summary = "Get bike details",
            description = "Retrieve bike status and information by bike ID"
    )
    public ResponseEntity<?> getBike(@PathVariable String bikeId) {
        try {
            BikeStatusResponse status = bikeService.getBikeStatus(bikeId);
            return ResponseEntity.ok(ApiResponse.success("Bike found", status));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get bike status by QR code (used when scanning)
     */
    @GetMapping("/qr/{qrCode}")
    @Operation(
            summary = "Get bike by QR code",
            description = "Retrieve bike status by scanning QR code - used by mobile app before starting ride"
    )
    public ResponseEntity<?> getBikeByQrCode(@PathVariable String qrCode) {
        try {
            BikeStatusResponse status = bikeService.getBikeStatusByQrCode(qrCode);
            return ResponseEntity.ok(ApiResponse.success("Bike found", status));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get all bikes
     */
    @GetMapping
    @Operation(
            summary = "Get all bikes",
            description = "Retrieve list of all bikes in the system"
    )
    public ResponseEntity<?> getAllBikes() {
        List<BikeStatusResponse> bikes = bikeService.getAllBikes();
        return ResponseEntity.ok(ApiResponse.success("Bikes retrieved", bikes));
    }

    /**
     * Get all available bikes
     */
    @GetMapping("/available")
    @Operation(
            summary = "Get available bikes",
            description = "Retrieve list of all bikes that are available for rental (LOCKED status and usable)"
    )
    public ResponseEntity<?> getAvailableBikes() {
        List<BikeStatusResponse> bikes = bikeService.getAvailableBikes();
        return ResponseEntity.ok(ApiResponse.success("Available bikes retrieved", bikes));
    }

    /**
     * Update bike location
     */
    @PutMapping("/{bikeId}/location")
    @Operation(
            summary = "Update bike location",
            description = "Update GPS coordinates for a bike (called by ESP32 or admin)"
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> updateLocation(
            @PathVariable String bikeId,
            @RequestParam Double latitude,
            @RequestParam Double longitude) {
        try {
            Bike bike = bikeService.updateBikeLocation(bikeId, latitude, longitude);
            return ResponseEntity.ok(ApiResponse.success("Location updated", BikeStatusResponse.fromBike(bike)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Update bike battery level
     */
    @PutMapping("/{bikeId}/battery")
    @Operation(
            summary = "Update bike battery",
            description = "Update battery level for a bike (called by ESP32)"
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> updateBattery(
            @PathVariable String bikeId,
            @RequestParam Integer batteryLevel) {
        try {
            Bike bike = bikeService.updateBikeBattery(bikeId, batteryLevel);
            return ResponseEntity.ok(ApiResponse.success("Battery level updated", BikeStatusResponse.fromBike(bike)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Set bike maintenance status
     */
    @PutMapping("/{bikeId}/maintenance")
    @Operation(
            summary = "Set maintenance status",
            description = "Mark bike as usable or under maintenance"
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> setMaintenanceStatus(
            @PathVariable String bikeId,
            @RequestParam boolean isUsable) {
        try {
            Bike bike = bikeService.setBikeMaintenanceStatus(bikeId, isUsable);
            return ResponseEntity.ok(ApiResponse.success(
                    isUsable ? "Bike marked as usable" : "Bike marked for maintenance",
                    BikeStatusResponse.fromBike(bike)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Send direct unlock command (admin/emergency use)
     * Note: For normal rides, use /api/rides/start instead
     */
    @PostMapping("/{bikeId}/unlock")
    @Operation(
            summary = "Direct unlock (admin)",
            description = "Send direct UNLOCK command to bike via MQTT. For normal rides, use /api/rides/start instead."
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> unlock(@PathVariable String bikeId) {
        try {
            bikeService.sendUnlockCommand(bikeId);
            return ResponseEntity.ok(ApiResponse.success("Unlock command sent", null));
        } catch (Exception e) {
            log.error("Failed to unlock bike {}: {}", bikeId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to send unlock command: " + e.getMessage()));
        }
    }

    /**
     * Send direct lock command (admin/emergency use)
     * Note: For normal rides, use /api/rides/end instead
     */
    @PostMapping("/{bikeId}/lock")
    @Operation(
            summary = "Direct lock (admin)",
            description = "Send direct LOCK command to bike via MQTT. For normal rides, use /api/rides/end instead."
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> lock(@PathVariable String bikeId) {
        try {
            bikeService.sendLockCommand(bikeId);
            return ResponseEntity.ok(ApiResponse.success("Lock command sent", null));
        } catch (Exception e) {
            log.error("Failed to lock bike {}: {}", bikeId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to send lock command: " + e.getMessage()));
        }
    }

    /**
     * Receive status update from bike (ESP32 callback)
     */
    @PostMapping("/{bikeId}/status")
    @Operation(
            summary = "Receive status update",
            description = "Endpoint for ESP32 to report status updates (alternative to MQTT)"
    )
    public ResponseEntity<?> receiveStatusUpdate(
            @PathVariable String bikeId,
            @RequestBody String status) {
        bikeService.processStatusUpdate(bikeId, status);
        return ResponseEntity.ok(ApiResponse.success("Status update processed", null));
    }
}
