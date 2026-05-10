package com.IoT.smart_bike_rental_backend.service;

import com.IoT.smart_bike_rental_backend.dto.BikeStatusResponse;
import com.IoT.smart_bike_rental_backend.dto.RideResponse;
import com.IoT.smart_bike_rental_backend.dto.UserProfileResponse;
import com.IoT.smart_bike_rental_backend.model.Bike;
import com.IoT.smart_bike_rental_backend.model.Ride;
import com.IoT.smart_bike_rental_backend.model.User;
import com.IoT.smart_bike_rental_backend.repository.Bikerepository;
import com.IoT.smart_bike_rental_backend.repository.Riderepository;
import com.IoT.smart_bike_rental_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository userRepository;
    private final Bikerepository bikeRepository;
    private final Riderepository rideRepository;
    private final PasswordEncoder passwordEncoder;
    private final BikeService bikeService;

    // ==================== DASHBOARD STATISTICS ====================

    /**
     * Get dashboard statistics for admin overview
     */
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        // User statistics
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.findAll().stream()
                .filter(u -> u.getIsActive() && "USER".equals(u.getRole()))
                .count();
        long adminCount = userRepository.findAll().stream()
                .filter(u -> "ADMIN".equals(u.getRole()))
                .count();

        // Bike statistics
        long totalBikes = bikeRepository.count();
        long availableBikes = bikeRepository.findAll().stream()
                .filter(Bike::isAvailable)
                .count();
        long inUseBikes = bikeRepository.findByStatus("UNLOCKED").size();
        long maintenanceBikes = bikeRepository.findByStatus("MAINTENANCE").size();

        // Ride statistics
        long totalRides = rideRepository.count();
        long activeRides = rideRepository.findByEndTimeIsNull().size();
        long completedRides = rideRepository.findAll().stream()
                .filter(r -> r.getEndTime() != null)
                .count();

        // Revenue (sum of all completed ride costs)
        BigDecimal totalRevenue = rideRepository.findAll().stream()
                .filter(r -> r.getCost() != null)
                .map(Ride::getCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Today's statistics
        LocalDateTime startOfDay = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
        long todayRides = rideRepository.findAll().stream()
                .filter(r -> r.getStartTime() != null && r.getStartTime().isAfter(startOfDay))
                .count();
        BigDecimal todayRevenue = rideRepository.findAll().stream()
                .filter(r -> r.getEndTime() != null && r.getEndTime().isAfter(startOfDay) && r.getCost() != null)
                .map(Ride::getCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        stats.put("totalUsers", totalUsers);
        stats.put("activeUsers", activeUsers);
        stats.put("adminCount", adminCount);
        stats.put("totalBikes", totalBikes);
        stats.put("availableBikes", availableBikes);
        stats.put("inUseBikes", inUseBikes);
        stats.put("maintenanceBikes", maintenanceBikes);
        stats.put("totalRides", totalRides);
        stats.put("activeRides", activeRides);
        stats.put("completedRides", completedRides);
        stats.put("totalRevenue", totalRevenue);
        stats.put("todayRides", todayRides);
        stats.put("todayRevenue", todayRevenue);

        return stats;
    }

    // ==================== USER MANAGEMENT ====================

    /**
     * Get all users
     */
    public List<UserProfileResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToUserProfile)
                .collect(Collectors.toList());
    }

    /**
     * Get user by ID
     */
    public UserProfileResponse getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
        return mapToUserProfile(user);
    }

    /**
     * Update user status (activate/deactivate)
     */
    @Transactional
    public UserProfileResponse updateUserStatus(Long userId, boolean isActive) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        user.setIsActive(isActive);
        user.setUpdatedAt(LocalDateTime.now());
        User savedUser = userRepository.save(user);

        log.info("Admin updated user {} status to: {}", userId, isActive ? "ACTIVE" : "INACTIVE");
        return mapToUserProfile(savedUser);
    }

    /**
     * Update user role
     */
    @Transactional
    public UserProfileResponse updateUserRole(Long userId, String role) {
        if (!"USER".equals(role) && !"ADMIN".equals(role)) {
            throw new IllegalArgumentException("Invalid role. Must be USER or ADMIN");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        user.setRole(role);
        user.setUpdatedAt(LocalDateTime.now());
        User savedUser = userRepository.save(user);

        log.info("Admin updated user {} role to: {}", userId, role);
        return mapToUserProfile(savedUser);
    }

    /**
     * Delete user (soft delete - just deactivates)
     */
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        // Check if user has active rides
        List<Ride> activeRides = rideRepository.findByUserAndEndTimeIsNull(user);
        if (!activeRides.isEmpty()) {
            throw new IllegalStateException("Cannot delete user with active rides");
        }

        user.setIsActive(false);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("Admin soft-deleted user: {}", userId);
    }

    /**
     * Reset user password (admin override)
     */
    @Transactional
    public void resetUserPassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("Admin reset password for user: {}", userId);
    }

    // ==================== BIKE MANAGEMENT ====================

    /**
     * Get all bikes with status
     */
    public List<BikeStatusResponse> getAllBikes() {
        return bikeRepository.findAll().stream()
                .map(BikeStatusResponse::fromBike)
                .collect(Collectors.toList());
    }

    /**
     * Create a new bike
     */
    @Transactional
    public BikeStatusResponse createBike(String bikeId, String qrCode, Double latitude, Double longitude) {
        if (bikeRepository.findByBikeId(bikeId).isPresent()) {
            throw new IllegalArgumentException("Bike with ID " + bikeId + " already exists");
        }

        Bike bike = new Bike();
        bike.setBikeId(bikeId);
        bike.setQrCode(qrCode != null ? qrCode : bikeId);
        bike.setStatus("LOCKED");
        bike.setIsUsable(true);
        bike.setBatteryLevel(100);
        bike.setLatitude(latitude);
        bike.setLongitude(longitude);
        bike.setLastUpdated(LocalDateTime.now());

        Bike savedBike = bikeRepository.save(bike);
        log.info("Admin created new bike: {}", bikeId);

        return BikeStatusResponse.fromBike(savedBike);
    }

    /**
     * Update bike details
     */
    @Transactional
    public BikeStatusResponse updateBike(String bikeId, String qrCode, Double latitude, Double longitude, Integer batteryLevel) {
        Bike bike = bikeRepository.findByBikeId(bikeId)
                .orElseThrow(() -> new IllegalArgumentException("Bike not found with ID: " + bikeId));

        if (qrCode != null) bike.setQrCode(qrCode);
        if (latitude != null) bike.setLatitude(latitude);
        if (longitude != null) bike.setLongitude(longitude);
        if (batteryLevel != null) bike.setBatteryLevel(batteryLevel);
        bike.setLastUpdated(LocalDateTime.now());

        Bike savedBike = bikeRepository.save(bike);
        log.info("Admin updated bike: {}", bikeId);

        return BikeStatusResponse.fromBike(savedBike);
    }

    /**
     * Set bike maintenance mode
     */
    @Transactional
    public BikeStatusResponse setBikeMaintenanceMode(String bikeId, boolean maintenance) {
        Bike bike = bikeRepository.findByBikeId(bikeId)
                .orElseThrow(() -> new IllegalArgumentException("Bike not found with ID: " + bikeId));

        bike.setIsUsable(!maintenance);
        if (maintenance) {
            bike.setStatus("MAINTENANCE");
        } else if (bike.getCurrentUser() == null) {
            bike.setStatus("LOCKED");
        }
        bike.setLastUpdated(LocalDateTime.now());

        Bike savedBike = bikeRepository.save(bike);
        log.info("Admin set bike {} maintenance mode: {}", bikeId, maintenance);

        return BikeStatusResponse.fromBike(savedBike);
    }

    /**
     * Delete bike
     */
    @Transactional
    public void deleteBike(String bikeId) {
        Bike bike = bikeRepository.findByBikeId(bikeId)
                .orElseThrow(() -> new IllegalArgumentException("Bike not found with ID: " + bikeId));

        // Check if bike is in use
        if ("UNLOCKED".equals(bike.getStatus()) || bike.getCurrentUser() != null) {
            throw new IllegalStateException("Cannot delete bike that is currently in use");
        }

        bikeRepository.delete(bike);
        log.info("Admin deleted bike: {}", bikeId);
    }

    /**
     * Force unlock bike (emergency)
     */
    public void forceUnlockBike(String bikeId) {
        bikeService.sendUnlockCommand(bikeId);
        log.warn("Admin force-unlocked bike: {}", bikeId);
    }

    /**
     * Force lock bike (emergency)
     */
    public void forceLockBike(String bikeId) {
        bikeService.sendLockCommand(bikeId);
        log.warn("Admin force-locked bike: {}", bikeId);
    }

    // ==================== RIDE MANAGEMENT ====================

    /**
     * Get all rides
     */
    public List<RideResponse> getAllRides() {
        return rideRepository.findAll().stream()
                .map(RideResponse::fromRide)
                .collect(Collectors.toList());
    }

    /**
     * Get active rides
     */
    public List<RideResponse> getActiveRides() {
        return rideRepository.findByEndTimeIsNull().stream()
                .map(RideResponse::fromRide)
                .collect(Collectors.toList());
    }

    /**
     * Get rides by user
     */
    public List<RideResponse> getRidesByUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        return rideRepository.findByUser(user).stream()
                .map(RideResponse::fromRide)
                .collect(Collectors.toList());
    }

    /**
     * Get rides by bike
     */
    public List<RideResponse> getRidesByBike(String bikeId) {
        Bike bike = bikeRepository.findByBikeId(bikeId)
                .orElseThrow(() -> new IllegalArgumentException("Bike not found with ID: " + bikeId));

        return rideRepository.findByBike(bike).stream()
                .map(RideResponse::fromRide)
                .collect(Collectors.toList());
    }

    /**
     * Force end a ride (emergency)
     */
    @Transactional
    public RideResponse forceEndRide(Long rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new IllegalArgumentException("Ride not found with ID: " + rideId));

        if (ride.getEndTime() != null) {
            throw new IllegalStateException("Ride is already ended");
        }

        // End the ride
        ride.setEndTime(LocalDateTime.now());

        // Calculate duration
        long minutes = ChronoUnit.MINUTES.between(ride.getStartTime(), ride.getEndTime());
        ride.setDurationMinutes((long) minutes);

        // Calculate cost (basic rate: $0.15 per minute)
        BigDecimal cost = BigDecimal.valueOf(minutes).multiply(BigDecimal.valueOf(0.15));
        ride.setCost(cost);
        ride.setPaymentStatus("ADMIN_CLOSED");

        // Update bike status
        Bike bike = ride.getBike();
        if (bike != null) {
            bike.setStatus("LOCKED");
            bike.setCurrentUser(null);
            bike.setLastUpdated(LocalDateTime.now());
            bikeRepository.save(bike);

            // Send lock command
            bikeService.sendLockCommand(bike.getBikeId());
        }

        Ride savedRide = rideRepository.save(ride);
        log.warn("Admin force-ended ride: {}", rideId);

        return RideResponse.fromRide(savedRide);
    }

    // ==================== HELPER METHODS ====================

    private UserProfileResponse mapToUserProfile(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .phone(user.getPhone())
                .role(user.getRole())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
