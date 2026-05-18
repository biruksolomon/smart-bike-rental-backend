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


    @Transactional
    public void processGpsUpdate(String bikeId, String payload) {
        bikeRepository.findByBikeId(bikeId).ifPresentOrElse(bike -> {
            String latVal = extractJsonNumber(payload, "lat");
            String lonVal = extractJsonNumber(payload, "lon");  // ESP32 uses "lon" not "lng"
            if (latVal != null && lonVal != null) {
                try {
                    bike.setLatitude(Double.parseDouble(latVal));
                    bike.setLongitude(Double.parseDouble(lonVal));
                    bike.setLastUpdated(LocalDateTime.now());
                    bikeRepository.save(bike);
                    log.info("GPS updated for bike {}: {},{}", bikeId, latVal, lonVal);
                } catch (NumberFormatException e) {
                    log.warn("Invalid GPS in payload: {}", payload);
                }
            }
        }, () -> log.warn("GPS update for unknown bike: {}", bikeId));
    }


    @Transactional
    public void processAlertUpdate(String bikeId, String payload) {
        if (payload == null) return;
        String alert = payload.trim();
        log.info("Alert from bike {}: {}", bikeId, alert);

        bikeRepository.findByBikeId(bikeId).ifPresent(bike -> {
            if (alert.contains("THEFT") || alert.contains("THEFT ALERT")) {
                // Mark bike as potentially stolen — you can add a field or just log + notify
                bike.setLastUpdated(LocalDateTime.now());
                bikeRepository.save(bike);
                log.warn("THEFT ALERT received for bike {}! Last known GPS: {},{}",
                        bikeId, bike.getLatitude(), bike.getLongitude());
                // TODO: trigger push notification to admin here
            }
            // "SAFE" alerts are informational — no action needed
        });
    }

    /**
     * Send direct unlock command (admin/emergency use only).
     * Publishes JSON: {"command":"UNLOCK","token":"<mqttToken>"}
     * Note: For normal rides, use RideService.startRide() instead
     */
    public void sendUnlockCommand(String bikeId) {
        bikeRepository.findByBikeId(bikeId)
                .orElseThrow(() -> new IllegalArgumentException("Bike not found with ID: " + bikeId));
        try {
            mqttService.sendUnlockCommand(bikeId);
            log.info("Direct UNLOCK command (JSON+token) sent to bike {}", bikeId);
        } catch (Exception e) {
            log.error("Failed to send UNLOCK command: {}", e.getMessage());
            throw new RuntimeException("Failed to send unlock command", e);
        }
    }

    /**
     * Send direct lock command (admin/emergency use only).
     * Publishes JSON: {"command":"LOCK","token":"<mqttToken>"}
     * Note: For normal rides, use RideService.endRide() instead
     */
    public void sendLockCommand(String bikeId) {
        bikeRepository.findByBikeId(bikeId)
                .orElseThrow(() -> new IllegalArgumentException("Bike not found with ID: " + bikeId));
        try {
            mqttService.sendLockCommand(bikeId);
            log.info("Direct LOCK command (JSON+token) sent to bike {}", bikeId);
        } catch (Exception e) {
            log.error("Failed to send LOCK command: {}", e.getMessage());
            throw new RuntimeException("Failed to send lock command", e);
        }
    }

    /**
     * Process status update from bike (received via MQTT).
     * Called when the ESP32 publishes to bike/{bikeId}/status.
     *
     * Expected JSON payloads:
     *   {"token":"1234","status":"LOCKED"}
     *   {"token":"1234","status":"UNLOCKED"}
     *   {"token":"1234","battery":85}
     *   {"token":"1234","lat":9.03,"lng":38.74}
     *
     * Token validation is already done in MqttService before this method is called.
     */
    @Transactional
    public void processStatusUpdate(String bikeId, String payload) {
        Optional<Bike> bikeOpt = bikeRepository.findByBikeId(bikeId);
        if (bikeOpt.isEmpty()) {
            log.warn("Received status update for unknown bike: {}", bikeId);
            return;
        }

        Bike bike = bikeOpt.get();
        log.info("Processing JSON status update for bike {}: {}", bikeId, payload);

        // --- Parse JSON fields without requiring Jackson ---

        // "status" field  →  LOCKED / UNLOCKED
        String statusVal = extractJsonString(payload, "status");
        if ("LOCKED".equals(statusVal) || "UNLOCKED".equals(statusVal)) {
            log.info("Bike {} confirmed state: {}", bikeId, statusVal);
            // Optionally mirror confirmed state back to DB:
            // bike.setStatus(statusVal);
        }

        // "battery" field  →  integer 0-100
        String batteryVal = extractJsonNumber(payload, "battery");
        if (batteryVal != null) {
            try {
                bike.setBatteryLevel(Integer.parseInt(batteryVal));
                log.debug("Bike {} battery: {}%", bikeId, batteryVal);
            } catch (NumberFormatException e) {
                log.warn("Invalid battery value in status payload: {}", payload);
            }
        }

        // "lat" / "lng" fields  →  GPS coordinates
        String latVal = extractJsonNumber(payload, "lat");
        String lngVal = extractJsonNumber(payload, "lng");
        if (latVal != null && lngVal != null) {
            try {
                bike.setLatitude(Double.parseDouble(latVal));
                bike.setLongitude(Double.parseDouble(lngVal));
                log.debug("Bike {} GPS updated: {},{}", bikeId, latVal, lngVal);
            } catch (NumberFormatException e) {
                log.warn("Invalid GPS values in status payload: {}", payload);
            }
        }

        bike.setLastUpdated(LocalDateTime.now());
        bikeRepository.save(bike);
    }

    // -----------------------------------------------------------------------
    // Tiny JSON helpers — extract a string value or a numeric value by key
    // without requiring Jackson (keeps the dependency footprint minimal).
    // -----------------------------------------------------------------------

    /** Returns the string value for "key":"value" in a JSON string, or null. */
    private String extractJsonString(String json, String key) {
        try {
            String search = "\"" + key + "\"";
            int idx = json.indexOf(search);
            if (idx == -1) return null;
            int colon = json.indexOf(':', idx + search.length());
            int q1 = json.indexOf('"', colon + 1);
            int q2 = json.indexOf('"', q1 + 1);
            if (q1 == -1 || q2 == -1) return null;
            return json.substring(q1 + 1, q2);
        } catch (Exception e) {
            return null;
        }
    }

    /** Returns the numeric value for "key":value in a JSON string, or null. */
    private String extractJsonNumber(String json, String key) {
        try {
            String search = "\"" + key + "\"";
            int idx = json.indexOf(search);
            if (idx == -1) return null;
            int colon = json.indexOf(':', idx + search.length());
            // skip whitespace
            int start = colon + 1;
            while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\t')) start++;
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-')) end++;
            if (end == start) return null;
            return json.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }
}
