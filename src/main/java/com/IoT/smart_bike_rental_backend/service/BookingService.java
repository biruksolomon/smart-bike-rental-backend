package com.IoT.smart_bike_rental_backend.service;

import com.IoT.smart_bike_rental_backend.dto.BikeStatusResponse;
import com.IoT.smart_bike_rental_backend.dto.RideResponse;
import com.IoT.smart_bike_rental_backend.dto.StartRideRequest;
import com.IoT.smart_bike_rental_backend.model.User;
import com.IoT.smart_bike_rental_backend.repository.Bikerepository;
import com.IoT.smart_bike_rental_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * BookingService — orchestrates the complete bike booking flow.
 *
 * Flow (per system diagram):
 *
 *   Mobile App (QR scan)
 *     │
 *     ▼
 *   processBooking(userId, qrCode)
 *     │
 *     ├─ 1. Validate user exists
 *     ├─ 2. Fetch bike status by QR code
 *     ├─ 3. Check bike is available
 *     ├─ 4. Generate unique txRef
 *     ├─ 5. ChapaPaymentService.initializePayment()  ← Chapa SDK
 *     │       └─ Returns InitializeResponseData with checkoutUrl
 *     ├─ 6. RideService.startRide()                  ← MQTT UNLOCK command
 *     └─ 7. Return BookingResult.success(checkoutUrl, txRef, rideResponse)
 *
 * The caller (BookingController) should:
 *   • Redirect / return the checkoutUrl to the mobile app so the user can pay.
 *   • Store the txRef — Chapa will echo it back in the webhook.
 *
 * After payment completes, Chapa calls:
 *   POST /api/webhooks/payment/chapa  (PaymentWebhookController)
 *     → verifies signature → verifies transaction → updates Ride.paymentStatus
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final UserRepository userRepository;
    private final Bikerepository bikeRepository;
    private final BikeService bikeService;
    private final RideService rideService;

    /**
     * Main booking entry point — called by BookingController when the user scans a bike QR code.
     *
     * @param userId  ID of the authenticated user (from JWT).
     * @param qrCode  QR code string scanned from the bike.
     * @return        {@link BookingResult} — success carries the Chapa checkout URL and rideResponse;
     *                failure carries an error message.
     */
    public BookingResult processBooking(Long userId, String qrCode) {
        log.info("Processing booking — userId: {}, qrCode: {}", userId, qrCode);

        // ── Step 1: Validate user ────────────────────────────────────────────
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return BookingResult.failure("User not found");
        }

        // ── Step 2: Fetch bike status ─────────────────────────────────────────
        BikeStatusResponse bikeStatus;
        try {
            bikeStatus = bikeService.getBikeStatusByQrCode(qrCode);
        } catch (IllegalArgumentException e) {
            return BookingResult.failure("Bike not found with this QR code");
        }

        // ── Step 3: Check bike availability ──────────────────────────────────
        if (!bikeStatus.getIsAvailable()) {
            String reason = determineUnavailabilityReason(bikeStatus);
            log.warn("Bike {} is not available: {}", bikeStatus.getBikeId(), reason);
            return BookingResult.failure(reason);
        }

        // ── Step 4: Start ride — send MQTT UNLOCK command ──────────────────────
        try {
            StartRideRequest request = new StartRideRequest();
            request.setUserId(userId);
            request.setQrCode(qrCode);

            RideResponse rideResponse = rideService.startRide(request);

            log.info("Ride started — rideId: {}", rideResponse.getRideId());

            // ── Step 5: Return success — payment will be initialized at ride end ──
            return BookingResult.success(
                    "Bike unlocked! Pay after your ride.",
                    rideResponse,
                    null,        // no txRef yet
                    null         // no checkoutUrl yet
            );

        } catch (Exception e) {
            log.error("Failed to start ride for userId {}: {}", userId, e.getMessage());
            return BookingResult.failure("Failed to unlock bike: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Maps a BikeStatusResponse to a human-readable unavailability reason. */
    private String determineUnavailabilityReason(BikeStatusResponse status) {
        if ("IN_USE".equals(status.getStatus()))          return "Bike is currently in use by another user";
        if ("MAINTENANCE".equals(status.getStatus()))     return "Bike is under maintenance";
        if (!Boolean.TRUE.equals(status.getIsUsable()))   return "Bike is not available for rental";
        if (status.getBatteryLevel() != null && status.getBatteryLevel() < 10)
            return "Bike battery is too low";
        return "Bike is not available";
    }

    // -------------------------------------------------------------------------
    // Result type
    // -------------------------------------------------------------------------

    /**
     * Encapsulates the outcome of a booking attempt.
     *
     * On success:
     *   • {@link #getRideResponse()} — the started ride (id, bike, user, startTime …)
     *   • {@link #getTransactionId()} — the Chapa txRef (store this on the Ride)
     *   • {@link #getCheckoutUrl()}  — redirect the user here to complete payment
     *
     * On failure:
     *   • {@link #getMessage()} — human-readable reason for the failure
     */
    public static class BookingResult {

        private final boolean success;
        private final String message;
        private final RideResponse rideResponse;
        private final String transactionId;
        private final String checkoutUrl;

        private BookingResult(boolean success, String message,
                              RideResponse rideResponse, String transactionId, String checkoutUrl) {
            this.success = success;
            this.message = message;
            this.rideResponse = rideResponse;
            this.transactionId = transactionId;
            this.checkoutUrl = checkoutUrl;
        }

        public static BookingResult success(String message, RideResponse rideResponse,
                                            String transactionId, String checkoutUrl) {
            return new BookingResult(true, message, rideResponse, transactionId, checkoutUrl);
        }

        public static BookingResult failure(String message) {
            return new BookingResult(false, message, null, null, null);
        }

        public boolean isSuccess()            { return success; }
        public String getMessage()            { return message; }
        public RideResponse getRideResponse() { return rideResponse; }
        public String getTransactionId()      { return transactionId; }
        public String getCheckoutUrl()        { return checkoutUrl; }
    }
}
