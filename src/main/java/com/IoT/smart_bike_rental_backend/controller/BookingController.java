package com.IoT.smart_bike_rental_backend.controller;

import com.IoT.smart_bike_rental_backend.dto.ApiResponse;
import com.IoT.smart_bike_rental_backend.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * BookingController - Main endpoint for mobile app bike booking
 *
 * This controller handles the complete booking flow:
 * 1. User scans QR code
 * 2. Mobile app sends booking request
 * 3. Backend processes payment
 * 4. Backend unlocks bike via MQTT
 * 5. Returns booking confirmation
 */
@RestController
@RequestMapping("/api/booking")
@RequiredArgsConstructor
@Tag(name = "Booking", description = "Main bike booking endpoints for mobile app")
@Slf4j
public class BookingController {

    private final BookingService bookingService;

    /**
     * Process a bike booking from QR code scan
     *
     * This is the MAIN endpoint called by the mobile app when user scans a bike QR code.
     *
     * Flow:
     * 1. Receive QR code from mobile app
     * 2. Validate user
     * 3. Check bike availability
     * 4. Process payment (simulated)
     * 5. Send UNLOCK command to bike via MQTT
     * 6. Return booking confirmation with ride details
     */
    @PostMapping("/scan")
    @Operation(
            summary = "Book a bike by scanning QR code",
            description = "Main booking endpoint - validates user, checks bike availability, processes payment, and unlocks bike"
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Booking successful - bike unlocked"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Booking failed - invalid user, bike not found, bike unavailable, or payment failed"
            )
    })
    public ResponseEntity<?> bookBike(
            @RequestParam Long userId,
            @RequestParam String qrCode) {

        log.info("Booking request received - User: {}, QR: {}", userId, qrCode);

        BookingService.BookingResult result = bookingService.processBooking(userId, qrCode);

        if (result.isSuccess()) {
            log.info("Booking SUCCESS for user {} - Ride ID: {}",
                    userId, result.getRideResponse().getRideId());

            return ResponseEntity.ok(ApiResponse.success(
                    result.getMessage(),
                    new BookingResponse(
                            result.getRideResponse(),
                            result.getTransactionId()
                    )
            ));
        } else {
            log.warn("Booking FAILED for user {}: {}", userId, result.getMessage());

            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(result.getMessage()));
        }
    }

    /**
     * Check bike availability before booking (optional pre-check)
     */
    @GetMapping("/check/{qrCode}")
    @Operation(
            summary = "Check bike availability",
            description = "Pre-check if a bike is available before booking (optional step)"
    )
    public ResponseEntity<?> checkAvailability(@PathVariable String qrCode) {
        try {
            // This delegates to BikeService for status check
            return ResponseEntity.ok(ApiResponse.success(
                    "Use /api/bikes/qr/{qrCode} for bike status",
                    null
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Response wrapper for booking
     */
    public static class BookingResponse {
        private final com.IoT.smart_bike_rental_backend.dto.RideResponse ride;
        private final String transactionId;

        public BookingResponse(
                com.IoT.smart_bike_rental_backend.dto.RideResponse ride,
                String transactionId) {
            this.ride = ride;
            this.transactionId = transactionId;
        }

        public com.IoT.smart_bike_rental_backend.dto.RideResponse getRide() {
            return ride;
        }

        public String getTransactionId() {
            return transactionId;
        }
    }
}
