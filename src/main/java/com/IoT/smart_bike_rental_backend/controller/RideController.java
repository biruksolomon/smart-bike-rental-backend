package com.IoT.smart_bike_rental_backend.controller;

import com.IoT.smart_bike_rental_backend.model.Ride;
import com.IoT.smart_bike_rental_backend.service.RideService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rides")
@RequiredArgsConstructor
public class RideController {

    private final RideService rideService;

    /**
     * Start a new ride
     * POST /api/rides/start?userId=...&qrCode=...
     */
    @PostMapping("/start")
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
    public ResponseEntity<?> getRide(@PathVariable Long rideId) {
        try {
            Ride ride = rideService.getRide(rideId);
            return ResponseEntity.ok(ride);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
