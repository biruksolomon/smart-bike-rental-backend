package com.IoT.smart_bike_rental_backend.service;

import com.IoT.smart_bike_rental_backend.dto.BookingResponse;
import com.IoT.smart_bike_rental_backend.model.Bike;
import com.IoT.smart_bike_rental_backend.model.Booking;
import com.IoT.smart_bike_rental_backend.model.User;
import com.IoT.smart_bike_rental_backend.repository.Bikerepository;
import com.IoT.smart_bike_rental_backend.repository.BookingRepository;
import com.IoT.smart_bike_rental_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

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
    private final BookingRepository bookingRepository;

    /**
     * Main booking entry point — called by BookingController when the user scans a bike QR code.
     *
     * Flow:
     * 1. Validate user
     * 2. Find bike by QR code
     * 3. Check bike availability
     * 4. Create Booking record with status PENDING
     * 5. Return bookingId to user
     *
     * User then calls startRide(bookingId) to actually start the ride and unlock the bike.
     *
     * @param userId  ID of the authenticated user (from JWT).
     * @param qrCode  QR code string scanned from the bike.
     * @return        {@link BookingResult} — success carries bookingId;
     *                failure carries an error message.
     */
    public BookingResult processBooking(Long userId, String qrCode) {
        log.info("Processing booking — userId: {}, qrCode: {}", userId, qrCode);

        // ── Step 1: Validate user ────────────────────────────────────────────
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return BookingResult.failure("User not found");
        }

        // ── Step 2: Find bike by QR code ─────────────────────────────────────
        Bike bike = bikeRepository.findByQrCode(qrCode)
                .orElse(null);
        if (bike == null) {
            return BookingResult.failure("Bike not found with this QR code");
        }

        // ── Step 3: Check bike availability ──────────────────────────────────
        if (!bike.isAvailable()) {
            String reason = determineUnavailabilityReason(bike);
            log.warn("Bike {} is not available: {}", bike.getBikeId(), reason);
            return BookingResult.failure(reason);
        }

        // ── Step 4: Create Booking record ────────────────────────────────────
        try {
            Booking booking = new Booking();
            booking.setUser(user);
            booking.setBike(bike);
            booking.setBookingTime(LocalDateTime.now());
            booking.setStatus("PENDING");
            booking.setQrCode(qrCode);

            Booking savedBooking = bookingRepository.save(booking);
            log.info("Booking created — bookingId: {}, userId: {}, bikeId: {}",
                    savedBooking.getId(), userId, bike.getBikeId());

            // ── Step 5: Return success with bookingId ───────────────────────────
            return BookingResult.successBooking(
                    "Bike reserved! Click 'Start Ride' to unlock and begin your journey.",
                    BookingResponse.fromBooking(savedBooking)
            );

        } catch (Exception e) {
            log.error("Failed to create booking for userId {}: {}", userId, e.getMessage());
            return BookingResult.failure("Failed to reserve bike: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Maps a Bike to a human-readable unavailability reason. */
    private String determineUnavailabilityReason(Bike bike) {
        if ("IN_USE".equals(bike.getStatus()))           return "Bike is currently in use by another user";
        if ("MAINTENANCE".equals(bike.getStatus()))      return "Bike is under maintenance";
        if (!Boolean.TRUE.equals(bike.getIsUsable()))    return "Bike is not available for rental";
        return "Bike is not available";
    }

    // -------------------------------------------------------------------------
    // Result type
    // -------------------------------------------------------------------------

    /**
     * Encapsulates the outcome of a booking attempt.
     *
     * On success (from booking):
     *   • {@link #getBookingResponse()} — the booking with bookingId
     *
     * On failure:
     *   • {@link #getMessage()} — human-readable reason for the failure
     */
    public static class BookingResult {

        private final boolean success;
        private final String message;
        private final BookingResponse bookingResponse;

        private BookingResult(boolean success, String message, BookingResponse bookingResponse) {
            this.success = success;
            this.message = message;
            this.bookingResponse = bookingResponse;
        }

        public static BookingResult successBooking(String message, BookingResponse bookingResponse) {
            return new BookingResult(true, message, bookingResponse);
        }

        public static BookingResult failure(String message) {
            return new BookingResult(false, message, null);
        }

        public boolean isSuccess()                    { return success; }
        public String getMessage()                    { return message; }
        public BookingResponse getBookingResponse()   { return bookingResponse; }
    }
}
