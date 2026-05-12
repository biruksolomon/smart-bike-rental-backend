package com.IoT.smart_bike_rental_backend.service;

import com.IoT.smart_bike_rental_backend.dto.BikeStatusResponse;
import com.IoT.smart_bike_rental_backend.model.Bike;
import com.IoT.smart_bike_rental_backend.model.Ride;
import com.IoT.smart_bike_rental_backend.mqtt.MqttService;
import com.IoT.smart_bike_rental_backend.repository.Bikerepository;
import com.IoT.smart_bike_rental_backend.repository.Riderepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BikeService {

    private final MqttService mqttService;
    private final Riderepository rideRepository;
    private final Bikerepository bikeRepository;

    /**
     * Get bike status by bike ID
     */
    public BikeStatusResponse getBikeStatus(String bikeId) {
        Bike bike = bikeRepository.findByBikeId(bikeId)
                .orElseThrow(() -> new IllegalArgumentException("Bike not found with ID: " + bikeId));
        return BikeStatusResponse.fromBike(bike);
    }

    /**
     * Get bike status by QR code (used when scanning)
     */
    public BikeStatusResponse getBikeStatusByQrCode(String qrCode) {
        Bike bike = bikeRepository.findByQrCode(qrCode)
                .orElseThrow(() -> new IllegalArgumentException("Bike not found with QR code: " + qrCode));
        return BikeStatusResponse.fromBike(bike);
    }

    /**
     * Get all bikes
     */
    public List<BikeStatusResponse> getAllBikes() {
        return bikeRepository.findAll().stream()
                .map(BikeStatusResponse::fromBike)
                .collect(Collectors.toList());
    }

    /**
     * Get all available bikes (LOCKED status and usable)
     */
    public List<BikeStatusResponse> getAvailableBikes() {
        return bikeRepository.findAll().stream()
                .filter(Bike::isAvailable)
                .map(BikeStatusResponse::fromBike)
                .collect(Collectors.toList());
    }

    /**
     * Create a new bike in the system
     */
    @Transactional
    public Bike createBike(String bikeId, String qrCode, String bikeType, String bikeSize) {
        // Check if bike already exists
        if (bikeRepository.findByBikeId(bikeId).isPresent()) {
            throw new IllegalArgumentException("Bike with ID " + bikeId + " already exists");
        }

        Bike bike = new Bike();
        bike.setBikeId(bikeId);
        bike.setQrCode(qrCode != null ? qrCode : bikeId);
        bike.setBikeType(bikeType != null ? bikeType : "Mountain");
        bike.setBikeSize(bikeSize != null ? bikeSize : "26");
        bike.setStatus("LOCKED");
        bike.setIsUsable(true);
        bike.setLastUpdated(LocalDateTime.now());

        log.info("Creating new bike with ID: {} | Type: {} | Size: {}", bikeId, bikeType, bikeSize);
        return bikeRepository.save(bike);
    }

    /**
     * Update bike location (called from ESP32 via MQTT or API)
     */
    @Transactional
    public Bike updateBikeLocation(String bikeId, Double latitude, Double longitude) {
        Bike bike = bikeRepository.findByBikeId(bikeId)
                .orElseThrow(() -> new IllegalArgumentException("Bike not found with ID: " + bikeId));

        bike.setLatitude(latitude);
        bike.setLongitude(longitude);
        bike.setLastUpdated(LocalDateTime.now());

        log.info("Updated location for bike {}: ({}, {})", bikeId, latitude, longitude);
        return bikeRepository.save(bike);
    }

    /**
     * Update bike battery level (called from ESP32 via MQTT or API)
     */
    @Transactional
    public Bike updateBikeBattery(String bikeId, Integer batteryLevel) {
        Bike bike = bikeRepository.findByBikeId(bikeId)
                .orElseThrow(() -> new IllegalArgumentException("Bike not found with ID: " + bikeId));

        bike.setBatteryLevel(batteryLevel);
        bike.setLastUpdated(LocalDateTime.now());

        log.info("Updated battery level for bike {}: {}%", bikeId, batteryLevel);
        return bikeRepository.save(bike);
    }

    /**
     * Set bike maintenance status
     */
    @Transactional
    public Bike setBikeMaintenanceStatus(String bikeId, boolean isUsable) {
        Bike bike = bikeRepository.findByBikeId(bikeId)
                .orElseThrow(() -> new IllegalArgumentException("Bike not found with ID: " + bikeId));

        bike.setIsUsable(isUsable);
        if (!isUsable) {
            bike.setStatus("MAINTENANCE");
        } else if (bike.getCurrentUser() == null) {
            bike.setStatus("LOCKED");
        }
        bike.setLastUpdated(LocalDateTime.now());

        log.info("Set maintenance status for bike {}: usable={}", bikeId, isUsable);
        return bikeRepository.save(bike);
    }

    /**
     * Send direct unlock command (admin/emergency use only)
     * Note: For normal rides, use RideService.startRide() instead
     */
    public void sendUnlockCommand(String bikeId) {
        Bike bike = bikeRepository.findByBikeId(bikeId)
                .orElseThrow(() -> new IllegalArgumentException("Bike not found with ID: " + bikeId));

        String topic = "bike/" + bikeId + "/command";
        try {
            mqttService.publish(topic, "UNLOCK");
            log.info("Direct UNLOCK command sent to bike {}", bikeId);
        } catch (Exception e) {
            log.error("Failed to send UNLOCK command: {}", e.getMessage());
            throw new RuntimeException("Failed to send unlock command", e);
        }
    }

    /**
     * Send direct lock command (admin/emergency use only)
     * Note: For normal rides, use RideService.endRide() instead
     */
    public void sendLockCommand(String bikeId) {
        Bike bike = bikeRepository.findByBikeId(bikeId)
                .orElseThrow(() -> new IllegalArgumentException("Bike not found with ID: " + bikeId));

        String topic = "bike/" + bikeId + "/command";
        try {
            mqttService.publish(topic, "LOCK");
            log.info("Direct LOCK command sent to bike {}", bikeId);
        } catch (Exception e) {
            log.error("Failed to send LOCK command: {}", e.getMessage());
            throw new RuntimeException("Failed to send lock command", e);
        }
    }

    /**
     * Process status update from bike (received via MQTT)
     * This is called when the bike ESP32 publishes to bike/{bikeId}/status
     */
    @Transactional
    public void processStatusUpdate(String bikeId, String status) {
        Optional<Bike> bikeOpt = bikeRepository.findByBikeId(bikeId);
        if (bikeOpt.isEmpty()) {
            log.warn("Received status update for unknown bike: {}", bikeId);
            return;
        }

        Bike bike = bikeOpt.get();
        log.info("Processing status update for bike {}: {}", bikeId, status);

        // Parse status message (could be JSON in production)
        // For now, expecting simple status like "LOCKED", "UNLOCKED", "BATTERY:85"
        if (status.startsWith("BATTERY:")) {
            try {
                int battery = Integer.parseInt(status.substring(8));
                bike.setBatteryLevel(battery);
            } catch (NumberFormatException e) {
                log.warn("Invalid battery level in status: {}", status);
            }
        } else if (status.startsWith("GPS:")) {
            // Format: GPS:lat,lng
            try {
                String[] coords = status.substring(4).split(",");
                bike.setLatitude(Double.parseDouble(coords[0]));
                bike.setLongitude(Double.parseDouble(coords[1]));
            } catch (Exception e) {
                log.warn("Invalid GPS coordinates in status: {}", status);
            }
        } else if ("LOCKED".equals(status) || "UNLOCKED".equals(status)) {
            // Bike confirmed lock/unlock state
            log.info("Bike {} confirmed state: {}", bikeId, status);
        }

        bike.setLastUpdated(LocalDateTime.now());
        bikeRepository.save(bike);
    }
}
