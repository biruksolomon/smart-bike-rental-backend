package com.IoT.smart_bike_rental_backend.controller;

import com.IoT.smart_bike_rental_backend.model.Ride;
import com.IoT.smart_bike_rental_backend.service.RideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rides")
@RequiredArgsConstructor
@Tag(name = "Rides", description = "Bike ride management endpoints")
public class RideController {

    private final RideService rideService;

    /**
     * Start a new ride
     * POST /api/rides/start?userId=...&qrCode=...
     */
    @PostMapping("/start")
    @Operation(summary = "Start a new ride", description = "Initiate a bike ride for a user using bike QR code")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Ride started successfully")
    @ApiResponse(responseCode = "400", description = "Invalid user or bike")
    public ResponseEntity<?> startRide(@RequestParam Long userId,
                                       @RequestParam String qrCode) {
        try {
            Ride ride = rideService.startRide(userId, qrCode);
            return ResponseEntity.ok(ride);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * End a ride
     * POST /api/rides/end?rideId=...
     */
    @PostMapping("/end")
    @Operation(summary = "End a ride", description = "Complete an ongoing bike ride and calculate cost")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Ride ended successfully")
    @ApiResponse(responseCode = "400", description = "Invalid ride ID or ride not active")
    public ResponseEntity<?> endRide(@RequestParam Long rideId) {
        try {
            Ride ride = rideService.endRide(rideId);
            return ResponseEntity.ok(ride);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Get ride history for a user
     * GET /api/rides/user/{userId}
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user ride history", description = "Retrieve all rides for a specific user")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Ride history retrieved")
    @ApiResponse(responseCode = "400", description = "Invalid user ID")
    public ResponseEntity<?> getUserRides(@PathVariable Long userId) {
        try {
            List<Ride> rides = rideService.getUserRideHistory(userId);
            return ResponseEntity.ok(rides);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Get specific ride by ID
     * GET /api/rides/{rideId}
     */
    @GetMapping("/{rideId}")
    @Operation(summary = "Get ride details", description = "Retrieve specific ride information by ride ID")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Ride details retrieved")
    @ApiResponse(responseCode = "404", description = "Ride not found")
    public ResponseEntity<?> getRide(@PathVariable Long rideId) {
        try {
            Ride ride = rideService.getRide(rideId);
            return ResponseEntity.ok(ride);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
