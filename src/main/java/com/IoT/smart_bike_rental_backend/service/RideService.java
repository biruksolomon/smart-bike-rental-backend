package com.IoT.smart_bike_rental_backend.service;

import com.IoT.smart_bike_rental_backend.dto.EndRideRequest;
import com.IoT.smart_bike_rental_backend.dto.RideResponse;
import com.IoT.smart_bike_rental_backend.dto.StartRideRequest;
import com.IoT.smart_bike_rental_backend.model.Bike;
import com.IoT.smart_bike_rental_backend.model.Ride;
import com.IoT.smart_bike_rental_backend.model.User;
import com.IoT.smart_bike_rental_backend.mqtt.MqttService;
import com.IoT.smart_bike_rental_backend.repository.Bikerepository;
import com.IoT.smart_bike_rental_backend.repository.Riderepository;
import com.IoT.smart_bike_rental_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RideService {

    private final Riderepository rideRepository;
    private final Bikerepository bikeRepository;
    private final UserRepository userRepository;
    private final MqttService mqttService;
    private final ChapaPaymentService chapaPaymentService;

    @Value("${chapa.enabled:true}")
    private boolean chapaPaymentEnabled;

    // Pricing configuration
    private static final BigDecimal PRICE_PER_MINUTE = new BigDecimal("0.15");
    private static final BigDecimal MINIMUM_CHARGE = new BigDecimal("1.00");

    /**
     * Start a new ride - Full flow per the system diagram:
     * 1. Receive QR code from mobile app
     * 2. Initialize payment with Chapa (pre-authorization)
     * 3. Fetch bike status
     * 4. Check if bike is usable
     * 5. If yes, send UNLOCK command via MQTT
     * 6. Create ride record with Chapa transaction reference
     */
    @Transactional
    public RideResponse startRide(StartRideRequest request) {
        log.info("Starting ride for user {} with QR code {}", request.getUserId(), request.getQrCode());

        // Step 1: Validate user exists
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + request.getUserId()));

        // Check if user already has an active ride
        Optional<Ride> existingRide = rideRepository.findByUserAndActiveTrue(user);
        if (existingRide.isPresent()) {
            throw new IllegalStateException("User already has an active ride. Please end your current ride first.");
        }

        // Step 2: Initialize payment with Chapa (pre-authorization)
        String chapaTxRef = "BIKE-RIDE-" + UUID.randomUUID().toString();
        String chapaCheckoutUrl = null;

        if (chapaPaymentEnabled) {
            try {
                // Initialize payment with Chapa for minimum amount
                ChapaPaymentService.PaymentInitResponse paymentResponse = initiateChapaPayment(
                        user, chapaTxRef, MINIMUM_CHARGE);
                chapaCheckoutUrl = paymentResponse.getCheckoutUrl();
                log.info("Chapa payment initialized for user {} - Checkout URL provided", user.getEmail());
            } catch (Exception e) {
                log.error("Chapa payment initialization failed for user {}: {}", user.getEmail(), e.getMessage());
                throw new IllegalStateException("Payment initialization failed: " + e.getMessage());
            }
        }

        // Step 3: Fetch bike status by QR code
        Bike bike = bikeRepository.findByQrCode(request.getQrCode())
                .orElseThrow(() -> new IllegalArgumentException("Bike not found with QR code: " + request.getQrCode()));

        log.info("Bike found: {} with status {}", bike.getBikeId(), bike.getStatus());

        // Step 4: Check if bike is usable
        if (!bike.isAvailable()) {
            String reason = "IN_USE".equals(bike.getStatus())
                    ? "Bike is currently in use by another user"
                    : !Boolean.TRUE.equals(bike.getIsUsable())
                      ? "Bike is under maintenance or not usable"
                      : "Bike is not available";
            throw new IllegalStateException(reason);
        }

        // Step 5: Create ride record with Chapa transaction reference
        Ride ride = new Ride();
        ride.setUser(user);
        ride.setBike(bike);
        ride.setStartTime(LocalDateTime.now());
        ride.setActive(true);
        ride.setCost(BigDecimal.ZERO);
        ride.setPaymentStatus("AUTHORIZED");
        ride.setChapaTxRef(chapaTxRef);
        ride.setStartLatitude(request.getStartLatitude());
        ride.setStartLongitude(request.getStartLongitude());

        Ride savedRide = rideRepository.save(ride);
        log.info("Ride record created with ID {} - Chapa TxRef: {}", savedRide.getId(), chapaTxRef);

        // Step 6: Update bike status to IN_USE
        bike.setStatus("IN_USE");
        bike.setCurrentUser(user);
        bike.setLastUpdated(LocalDateTime.now());
        bikeRepository.save(bike);

        // Step 7: Send MQTT command to ESP32 to unlock bike
        sendUnlockCommand(bike.getBikeId());

        return RideResponse.fromRide(savedRide, "Ride started successfully. Bike unlocked. Payment authorized with Chapa.");
    }

    /**
     * End a ride - Full flow per the system diagram:
     * 1. Send LOCK command via MQTT
     * 2. Compute ride cost and duration
     * 3. Capture payment with Chapa (final charge)
     * 4. Store ride data
     * 5. Update bike status
     */
    @Transactional
    public RideResponse endRide(EndRideRequest request) {
        log.info("Ending ride with ID {}", request.getRideId());

        // Find active ride
        Ride ride = rideRepository.findById(request.getRideId())
                .orElseThrow(() -> new IllegalArgumentException("Ride not found with ID: " + request.getRideId()));

        if (!ride.isActive()) {
            throw new IllegalStateException("Ride is already completed");
        }

        // Set end time
        ride.setEndTime(LocalDateTime.now());
        ride.setActive(false);
        ride.setEndLatitude(request.getEndLatitude());
        ride.setEndLongitude(request.getEndLongitude());

        // Calculate duration in minutes
        long durationMinutes = Duration.between(ride.getStartTime(), ride.getEndTime()).toMinutes();
        ride.setDurationMinutes(durationMinutes);

        // Calculate cost (minimum charge applies)
        BigDecimal cost = PRICE_PER_MINUTE.multiply(BigDecimal.valueOf(durationMinutes));
        if (cost.compareTo(MINIMUM_CHARGE) < 0) {
            cost = MINIMUM_CHARGE;
        }
        ride.setCost(cost);

        // Capture payment with Chapa (if enabled)
        if (chapaPaymentEnabled && ride.getChapaTxRef() != null) {
            try {
                // Verify payment with Chapa before finalizing
                ChapaPaymentService.PaymentVerifyResponse verification =
                        chapaPaymentService.verifyPayment(ride.getChapaTxRef());

                ride.setChapaChargeId(verification.getTxRef());
                ride.setPaymentStatus("PENDING_WEBHOOK"); // Wait for webhook confirmation of final amount
                log.info("Chapa payment verified for ride {} - Awaiting webhook confirmation", ride.getId());
            } catch (Exception e) {
                log.error("Chapa payment verification failed for ride {}: {}", ride.getId(), e.getMessage());
                ride.setPaymentStatus("PAYMENT_FAILED");
                rideRepository.save(ride);
                throw new IllegalStateException("Payment verification failed: " + e.getMessage());
            }
        } else if (!chapaPaymentEnabled) {
            ride.setPaymentStatus("COMPLETED");
        }

        Ride savedRide = rideRepository.save(ride);
        log.info("Ride {} completed. Duration: {} minutes, Cost: ${} - Payment Status: {}",
                savedRide.getId(), durationMinutes, cost, savedRide.getPaymentStatus());

        // Update bike status back to LOCKED
        Bike bike = ride.getBike();
        bike.setStatus("LOCKED");
        bike.setCurrentUser(null);
        bike.setLastUpdated(LocalDateTime.now());

        // Update bike location if provided
        if (request.getEndLatitude() != null && request.getEndLongitude() != null) {
            bike.setLatitude(request.getEndLatitude());
            bike.setLongitude(request.getEndLongitude());
        }

        bikeRepository.save(bike);

        // Send MQTT command to ESP32 to lock bike
        sendLockCommand(bike.getBikeId());

        return RideResponse.fromRide(savedRide,
                String.format("Ride completed. Duration: %d minutes. Total cost: $%s. Payment capture initiated.", durationMinutes, cost.toString()));
    }

    /**
     * End a ride by user ID (finds the active ride for the user)
     */
    @Transactional
    public RideResponse endRideByUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        Ride ride = rideRepository.findByUserAndActiveTrue(user)
                .orElseThrow(() -> new IllegalStateException("No active ride found for user"));

        EndRideRequest request = new EndRideRequest();
        request.setRideId(ride.getId());
        return endRide(request);
    }

    /**
     * Get the current active ride for a user
     */
    public Optional<RideResponse> getActiveRide(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        return rideRepository.findByUserAndActiveTrue(user)
                .map(ride -> RideResponse.fromRide(ride, "Active ride found"));
    }

    /**
     * Get ride history for a user
     */
    public List<Ride> getUserRideHistory(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        return rideRepository.findByUserIdOrderByStartTimeDesc(userId);
    }

    /**
     * Get a specific ride by ID
     */
    public Ride getRide(Long rideId) {
        return rideRepository.findById(rideId)
                .orElseThrow(() -> new IllegalArgumentException("Ride not found with ID: " + rideId));
    }

    /**
     * Initialize Chapa payment for ride start
     */
    private ChapaPaymentService.PaymentInitResponse initiateChapaPayment(User user, String txRef, BigDecimal amount) {
        log.info("Initializing Chapa payment for user {} - Amount: {}, TxRef: {}", user.getEmail(), amount, txRef);
        return chapaPaymentService.initializePayment(
                txRef,
                user.getEmail(),
                user.getName() ,
                amount
        );
    }

    /**
     * Simulate payment - In production, integrate with payment gateway
     * For now, always returns true (payment successful)
     */
    private boolean simulatePayment(User user) {
        log.info("Simulating payment for user: {}", user.getEmail());
        // In production: Integrate with Stripe, PayPal, etc.
        // For now, always return true (successful payment)
        return true;
    }

    /**
     * Send UNLOCK command to bike via MQTT
     * Topic format: bike/{bikeId}/command
     * Payload: UNLOCK
     */
    private void sendUnlockCommand(String bikeId) {
        String topic = "bike/" + bikeId + "/command";
        try {
            mqttService.publish(topic, "UNLOCK");
            log.info("UNLOCK command sent to topic: {}", topic);
        } catch (Exception e) {
            log.error("Failed to send UNLOCK command to {}: {}", topic, e.getMessage());
            // Don't throw - the ride is still valid even if MQTT fails
            // The bike might unlock on retry or manual intervention
        }
    }

    /**
     * Send LOCK command to bike via MQTT
     * Topic format: bike/{bikeId}/command
     * Payload: LOCK
     */
    private void sendLockCommand(String bikeId) {
        String topic = "bike/" + bikeId + "/command";
        try {
            mqttService.publish(topic, "LOCK");
            log.info("LOCK command sent to topic: {}", topic);
        } catch (Exception e) {
            log.error("Failed to send LOCK command to {}: {}", topic, e.getMessage());
            // Don't throw - the ride is still completed even if MQTT fails
        }
    }
}
