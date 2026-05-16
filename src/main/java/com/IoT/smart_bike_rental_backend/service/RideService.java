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
import com.yaphet.chapa.model.InitializeResponseData;
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
     * 2. Fetch bike status
     * 3. Check if bike is usable
     * 4. Create ride record
     * 5. Send UNLOCK command via MQTT
     *
     * Payment initialization is deferred to endRide() to ensure the cost is exact
     * and to avoid session expiry issues.
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

        // Step 2: Fetch bike status by QR code
        Bike bike = bikeRepository.findByQrCode(request.getQrCode())
                .orElseThrow(() -> new IllegalArgumentException("Bike not found with QR code: " + request.getQrCode()));

        log.info("Bike found: {} with status {}", bike.getBikeId(), bike.getStatus());

        // Step 3: Check if bike is usable
        if (!bike.isAvailable()) {
            String reason = "IN_USE".equals(bike.getStatus())
                    ? "Bike is currently in use by another user"
                    : !Boolean.TRUE.equals(bike.getIsUsable())
                      ? "Bike is under maintenance or not usable"
                      : "Bike is not available";
            throw new IllegalStateException(reason);
        }

        // Step 4: Create ride record
        Ride ride = new Ride();
        ride.setUser(user);
        ride.setBike(bike);
        ride.setStartTime(LocalDateTime.now());
        ride.setActive(true);
        ride.setCost(BigDecimal.ZERO);
        ride.setPaymentStatus("PENDING");
        ride.setStartLatitude(request.getStartLatitude());
        ride.setStartLongitude(request.getStartLongitude());

        Ride savedRide = rideRepository.save(ride);
        log.info("Ride record created with ID {}", savedRide.getId());

        // Step 5: Update bike status to IN_USE
        bike.setStatus("IN_USE");
        bike.setCurrentUser(user);
        bike.setLastUpdated(LocalDateTime.now());
        bikeRepository.save(bike);

        // Step 6: Send MQTT command to ESP32 to unlock bike
        sendUnlockCommand(bike.getBikeId());

        return RideResponse.fromRide(savedRide, "Ride started successfully. Bike unlocked! Pay after your ride.");
    }

    /**
     * End a ride - Full flow per the system diagram:
     * 1. Compute ride cost and duration
     * 2. Initialize payment with Chapa with the exact calculated cost
     * 3. Store ride data
     * 4. Update bike status
     * 5. Send LOCK command via MQTT
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

        // Initialize payment with Chapa at ride end with exact cost (if enabled)
        String checkoutUrl = null;
        if (chapaPaymentEnabled) {
            try {
                String txRef = "BIKE-RIDE-" + ride.getId() + "-" + System.currentTimeMillis();

                InitializeResponseData paymentResponse = chapaPaymentService.initializePayment(
                        txRef,
                        ride.getUser().getEmail(),
                        ride.getUser().getName(),
                        cost                        // exact calculated cost
                );

                checkoutUrl = paymentResponse.getData() != null
                        ? paymentResponse.getData().getCheckOutUrl()
                        : null;

                ride.setChapaTxRef(txRef);
                ride.setPaymentStatus("PENDING_PAYMENT");

                log.info("Chapa payment initialized for ride {} - TxRef: {} - Cost: {}", ride.getId(), txRef, cost);

            } catch (Exception e) {
                log.error("Payment init failed for ride {}: {}", ride.getId(), e.getMessage());
                ride.setPaymentStatus("PAYMENT_FAILED");
                rideRepository.save(ride);
                throw new IllegalStateException("Payment initialization failed: " + e.getMessage());
            }
        } else {
            ride.setPaymentStatus("COMPLETED");
        }

        Ride savedRide = rideRepository.save(ride);
        log.info("Ride {} completed. Duration: {} minutes, Cost: {} ETB - Payment Status: {}",
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

        return RideResponse.fromRideWithCheckout(savedRide,
                String.format("Ride completed. Duration: %d min. Cost: %s ETB. Please complete payment.",
                        durationMinutes, cost),
                checkoutUrl);
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
