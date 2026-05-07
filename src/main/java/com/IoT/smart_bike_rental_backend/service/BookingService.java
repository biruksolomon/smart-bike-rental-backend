package com.IoT.smart_bike_rental_backend.service;

import com.IoT.smart_bike_rental_backend.dto.BikeStatusResponse;
import com.IoT.smart_bike_rental_backend.dto.RideResponse;
import com.IoT.smart_bike_rental_backend.dto.StartRideRequest;
import com.IoT.smart_bike_rental_backend.model.Bike;
import com.IoT.smart_bike_rental_backend.model.User;
import com.IoT.smart_bike_rental_backend.repository.Bikerepository;
import com.IoT.smart_bike_rental_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * BookingService - Orchestrates the complete bike booking flow
 *
 * Flow per system diagram:
 * 1. User Mobile App scans QR Code
 * 2. Send Bike ID to Backend Server
 * 3. Simulate Payment
 * 4. Check if payment successful
 * 5. Fetch Bike Status
 * 6. Check if Bike is Usable
 * 7. If yes: Publish UNLOCK command via MQTT
 * 8. If no: Send failure message
 * 9. Smart Lock receives command and unlocks
 * 10. When ending: Publish LOCK command, compute cost & duration
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final UserRepository userRepository;
    private final Bikerepository bikeRepository;
    private final BikeService bikeService;
    private final RideService rideService;
    private final PaymentService paymentService;

    /**
     * Complete booking flow - from QR scan to bike unlock
     * This is the main entry point called by the mobile app
     */
    public BookingResult processBooking(Long userId, String qrCode) {
        log.info("Processing booking for user {} with QR code {}", userId, qrCode);

        // Step 1: Validate user
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return BookingResult.failure("User not found");
        }

        // Step 2: Fetch bike status
        BikeStatusResponse bikeStatus;
        try {
            bikeStatus = bikeService.getBikeStatusByQrCode(qrCode);
        } catch (IllegalArgumentException e) {
            return BookingResult.failure("Bike not found with this QR code");
        }

        // Step 3: Check if bike is usable
        if (!bikeStatus.getIsAvailable()) {
            String reason = determineUnavailabilityReason(bikeStatus);
            log.warn("Bike {} is not available: {}", bikeStatus.getBikeId(), reason);
            return BookingResult.failure(reason);
        }

        // Step 4: Simulate payment
        PaymentService.PaymentResult paymentResult = paymentService.processPayment(user, bikeStatus.getBikeId());
        if (!paymentResult.isSuccess()) {
            return BookingResult.failure("Payment failed: " + paymentResult.getMessage());
        }
        log.info("Payment authorized for user {} - Transaction: {}",
                user.getEmail(), paymentResult.getTransactionId());

        // Step 5: Start ride (this will send UNLOCK command)
        try {
            StartRideRequest request = new StartRideRequest();
            request.setUserId(userId);
            request.setQrCode(qrCode);

            RideResponse rideResponse = rideService.startRide(request);

            return BookingResult.success(
                    "Booking successful! Bike unlocked.",
                    rideResponse,
                    paymentResult.getTransactionId()
            );
        } catch (Exception e) {
            log.error("Failed to start ride after payment: {}", e.getMessage());
            // In production, refund the payment here
            paymentService.refundPayment(paymentResult.getTransactionId());
            return BookingResult.failure("Failed to unlock bike: " + e.getMessage());
        }
    }

    /**
     * Determine why a bike is unavailable
     */
    private String determineUnavailabilityReason(BikeStatusResponse status) {
        if ("IN_USE".equals(status.getStatus())) {
            return "Bike is currently in use by another user";
        }
        if ("MAINTENANCE".equals(status.getStatus())) {
            return "Bike is under maintenance";
        }
        if (!Boolean.TRUE.equals(status.getIsUsable())) {
            return "Bike is not available for rental";
        }
        if (status.getBatteryLevel() != null && status.getBatteryLevel() < 10) {
            return "Bike battery is too low";
        }
        return "Bike is not available";
    }

    /**
     * Result of a booking attempt
     */
    public static class BookingResult {
        private final boolean success;
        private final String message;
        private final RideResponse rideResponse;
        private final String transactionId;

        private BookingResult(boolean success, String message, RideResponse rideResponse, String transactionId) {
            this.success = success;
            this.message = message;
            this.rideResponse = rideResponse;
            this.transactionId = transactionId;
        }

        public static BookingResult success(String message, RideResponse rideResponse, String transactionId) {
            return new BookingResult(true, message, rideResponse, transactionId);
        }

        public static BookingResult failure(String message) {
            return new BookingResult(false, message, null, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public RideResponse getRideResponse() {
            return rideResponse;
        }

        public String getTransactionId() {
            return transactionId;
        }
    }
}
