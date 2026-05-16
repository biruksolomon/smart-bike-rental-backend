package com.IoT.smart_bike_rental_backend.controller;

import com.IoT.smart_bike_rental_backend.dto.ApiResponse;
import com.IoT.smart_bike_rental_backend.dto.EndRideRequest;
import com.IoT.smart_bike_rental_backend.dto.RideResponse;
import com.IoT.smart_bike_rental_backend.dto.StartRideRequest;
import com.IoT.smart_bike_rental_backend.model.Ride;
import com.IoT.smart_bike_rental_backend.service.RideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/rides")
@RequiredArgsConstructor
@Tag(name = "Rides", description = "Bike ride management endpoints - Start, End, and Track rides")
@Slf4j
public class RideController {

    private final RideService rideService;

    /**
     * Start a new ride from a booking
     * This is called AFTER a booking has been created
     *
     * Flow:
     * 1. User calls POST /api/booking/scan to create booking (returns bookingId)
     * 2. User calls this endpoint with bookingId and userId
     * 3. Backend creates Ride record
     * 4. Backend sends UNLOCK command via MQTT
     * 5. Returns ride info to mobile app
     */
    @PostMapping("/start")
    @Operation(
            summary = "Start a new ride from a booking",
            description = "Initiate a bike ride using a bookingId. Creates ride record, validates bike availability, and sends UNLOCK command to ESP32."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Ride started successfully, bike unlocked"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid booking, user, or bike not available"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "User already has an active ride")
    })
    public ResponseEntity<?> startRide(@RequestBody StartRideRequest request) {
        try {
            log.info("Received start ride request for user {} with booking {}",
                    request.getUserId(), request.getBookingId());

            RideResponse response = rideService.startRide(request);
            return ResponseEntity.ok(ApiResponse.success("Ride started successfully", response));

        } catch (IllegalArgumentException e) {
            log.warn("Start ride failed - bad request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("Start ride failed - conflict: {}", e.getMessage());
            return ResponseEntity.status(409)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Start a new ride (alternative with query params)
     */
    @PostMapping("/start/simple")
    @Operation(
            summary = "Start a new ride (simple)",
            description = "Start a ride using query parameters instead of request body"
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> startRideSimple(
            @RequestParam Long userId,
            @RequestParam Long bookingId) {
        StartRideRequest request = new StartRideRequest();
        request.setUserId(userId);
        request.setBookingId(bookingId);
        return startRide(request);
    }

    /**
     * End a ride
     * Called when user wants to end their ride
     *
     * Flow:
     * 1. User taps "End Ride" in mobile app
     * 2. Backend calculates ride duration and cost
     * 3. Backend sends LOCK command via MQTT
     * 4. Returns final ride info with cost
     */
    @PostMapping("/end")
    @Operation(
            summary = "End a ride",
            description = "Complete an ongoing bike ride. Calculates cost based on duration, sends LOCK command to ESP32."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Ride ended successfully with cost calculated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid ride ID or ride already completed")
    })
    public ResponseEntity<?> endRide(@RequestBody EndRideRequest request) {
        try {
            log.info("Received end ride request for ride ID {}", request.getRideId());

            RideResponse response = rideService.endRide(request);
            return ResponseEntity.ok(ApiResponse.success("Ride completed successfully", response));

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("End ride failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * End a ride (alternative with query param for backwards compatibility)
     */
    @PostMapping("/end/simple")
    @Operation(
            summary = "End a ride (simple)",
            description = "End a ride using query parameter instead of request body"
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> endRideSimple(@RequestParam Long rideId) {
        EndRideRequest request = new EndRideRequest();
        request.setRideId(rideId);
        return endRide(request);
    }

    /**
     * End ride by user ID (ends the user's active ride)
     */
    @PostMapping("/end/user/{userId}")
    @Operation(
            summary = "End active ride for user",
            description = "Find and end the currently active ride for a specific user"
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> endRideByUser(@PathVariable Long userId) {
        try {
            RideResponse response = rideService.endRideByUser(userId);
            return ResponseEntity.ok(ApiResponse.success("Ride completed successfully", response));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Get active ride for a user
     */
    @GetMapping("/active/{userId}")
    @Operation(
            summary = "Get active ride",
            description = "Check if user has an active ride and return its details"
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> getActiveRide(@PathVariable Long userId) {
        try {
            Optional<RideResponse> activeRide = rideService.getActiveRide(userId);
            if (activeRide.isPresent()) {
                return ResponseEntity.ok(ApiResponse.success("Active ride found", activeRide.get()));
            } else {
                return ResponseEntity.ok(ApiResponse.success("No active ride", null));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Get ride history for a user
     */
    @GetMapping("/user/{userId}")
    @Operation(
            summary = "Get user ride history",
            description = "Retrieve all past rides for a specific user, ordered by most recent first"
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> getUserRides(@PathVariable Long userId) {
        try {
            List<Ride> rides = rideService.getUserRideHistory(userId);
            return ResponseEntity.ok(ApiResponse.success("Ride history retrieved", rides));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Get specific ride by ID
     */
    @GetMapping("/{rideId}")
    @Operation(
            summary = "Get ride details",
            description = "Retrieve specific ride information by ride ID"
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> getRide(@PathVariable Long rideId) {
        try {
            Ride ride = rideService.getRide(rideId);
            return ResponseEntity.ok(ApiResponse.success("Ride details retrieved", ride));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
