package com.IoT.smart_bike_rental_backend.controller;

import com.IoT.smart_bike_rental_backend.dto.ApiResponse;
import com.IoT.smart_bike_rental_backend.dto.BikeStatusResponse;
import com.IoT.smart_bike_rental_backend.dto.RideResponse;
import com.IoT.smart_bike_rental_backend.dto.UserProfileResponse;
import com.IoT.smart_bike_rental_backend.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin", description = "Admin management endpoints (requires ADMIN role)")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminService adminService;

    // ==================== DASHBOARD ====================

    @GetMapping("/dashboard")
    @Operation(summary = "Get dashboard statistics", description = "Returns overview statistics for admin dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboardStats(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(
                    ApiResponse.error("Access denied. Admin role required.")
            );
        }

        Map<String, Object> stats = adminService.getDashboardStats();
        return ResponseEntity.ok(ApiResponse.success("Dashboard statistics retrieved", stats));
    }

    // ==================== USER MANAGEMENT ====================

    @GetMapping("/users")
    @Operation(summary = "Get all users", description = "Returns list of all registered users")
    public ResponseEntity<ApiResponse<List<UserProfileResponse>>> getAllUsers(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access denied. Admin role required."));
        }

        List<UserProfileResponse> users = adminService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success("Users retrieved successfully", users));
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "Get user by ID", description = "Returns details of a specific user")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserById(
            @PathVariable Long userId,
            HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access denied. Admin role required."));
        }

        try {
            UserProfileResponse user = adminService.getUserById(userId);
            return ResponseEntity.ok(ApiResponse.success("User retrieved successfully", user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/users/{userId}/status")
    @Operation(summary = "Update user status", description = "Activate or deactivate a user account")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateUserStatus(
            @PathVariable Long userId,
            @RequestParam boolean active,
            HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access denied. Admin role required."));
        }

        try {
            UserProfileResponse user = adminService.updateUserStatus(userId, active);
            return ResponseEntity.ok(ApiResponse.success("User status updated successfully", user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/users/{userId}/role")
    @Operation(summary = "Update user role", description = "Change user role (USER or ADMIN)")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateUserRole(
            @PathVariable Long userId,
            @RequestParam String role,
            HttpServletRequest request) {
        String currentRole = (String) request.getAttribute("role");
        if (!"ADMIN".equals(currentRole)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access denied. Admin role required."));
        }

        try {
            UserProfileResponse user = adminService.updateUserRole(userId, role);
            return ResponseEntity.ok(ApiResponse.success("User role updated successfully", user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/users/{userId}")
    @Operation(summary = "Delete user", description = "Soft delete (deactivate) a user account")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable Long userId,
            HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access denied. Admin role required."));
        }

        try {
            adminService.deleteUser(userId);
            return ResponseEntity.ok(ApiResponse.success("User deleted successfully", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/users/{userId}/reset-password")
    @Operation(summary = "Reset user password", description = "Admin override to reset a user's password")
    public ResponseEntity<ApiResponse<Void>> resetUserPassword(
            @PathVariable Long userId,
            @RequestParam String newPassword,
            HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access denied. Admin role required."));
        }

        try {
            adminService.resetUserPassword(userId, newPassword);
            return ResponseEntity.ok(ApiResponse.success("Password reset successfully", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== BIKE MANAGEMENT ====================

    @GetMapping("/bikes")
    @Operation(summary = "Get all bikes", description = "Returns list of all bikes with their status")
    public ResponseEntity<ApiResponse<List<BikeStatusResponse>>> getAllBikes(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access denied. Admin role required."));
        }

        List<BikeStatusResponse> bikes = adminService.getAllBikes();
        return ResponseEntity.ok(ApiResponse.success("Bikes retrieved successfully", bikes));
    }

    @PostMapping("/bikes")
    @Operation(summary = "Create a new bike", description = "Register a new bike in the system")
    public ResponseEntity<ApiResponse<BikeStatusResponse>> createBike(
            @RequestParam String bikeId,
            @RequestParam(required = false) String qrCode,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access denied. Admin role required."));
        }

        try {
            BikeStatusResponse bike = adminService.createBike(bikeId, qrCode, latitude, longitude);
            return ResponseEntity.ok(ApiResponse.success("Bike created successfully", bike));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/bikes/{bikeId}")
    @Operation(summary = "Update bike details", description = "Update bike information")
    public ResponseEntity<ApiResponse<BikeStatusResponse>> updateBike(
            @PathVariable String bikeId,
            @RequestParam(required = false) String qrCode,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Integer batteryLevel,
            HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access denied. Admin role required."));
        }

        try {
            BikeStatusResponse bike = adminService.updateBike(bikeId, qrCode, latitude, longitude, batteryLevel);
            return ResponseEntity.ok(ApiResponse.success("Bike updated successfully", bike));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/bikes/{bikeId}/maintenance")
    @Operation(summary = "Set bike maintenance mode", description = "Enable or disable maintenance mode for a bike")
    public ResponseEntity<ApiResponse<BikeStatusResponse>> setBikeMaintenanceMode(
            @PathVariable String bikeId,
            @RequestParam boolean maintenance,
            HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access denied. Admin role required."));
        }

        try {
            BikeStatusResponse bike = adminService.setBikeMaintenanceMode(bikeId, maintenance);
            return ResponseEntity.ok(ApiResponse.success("Bike maintenance mode updated", bike));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/bikes/{bikeId}")
    @Operation(summary = "Delete bike", description = "Remove a bike from the system")
    public ResponseEntity<ApiResponse<Void>> deleteBike(
            @PathVariable String bikeId,
            HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access denied. Admin role required."));
        }

        try {
            adminService.deleteBike(bikeId);
            return ResponseEntity.ok(ApiResponse.success("Bike deleted successfully", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/bikes/{bikeId}/force-unlock")
    @Operation(summary = "Force unlock bike", description = "Emergency command to unlock a bike")
    public ResponseEntity<ApiResponse<Void>> forceUnlockBike(
            @PathVariable String bikeId,
            HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access denied. Admin role required."));
        }

        try {
            adminService.forceUnlockBike(bikeId);
            return ResponseEntity.ok(ApiResponse.success("Force unlock command sent", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/bikes/{bikeId}/force-lock")
    @Operation(summary = "Force lock bike", description = "Emergency command to lock a bike")
    public ResponseEntity<ApiResponse<Void>> forceLockBike(
            @PathVariable String bikeId,
            HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access denied. Admin role required."));
        }

        try {
            adminService.forceLockBike(bikeId);
            return ResponseEntity.ok(ApiResponse.success("Force lock command sent", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== RIDE MANAGEMENT ====================

    @GetMapping("/rides")
    @Operation(summary = "Get all rides", description = "Returns list of all rides")
    public ResponseEntity<ApiResponse<List<RideResponse>>> getAllRides(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access denied. Admin role required."));
        }

        List<RideResponse> rides = adminService.getAllRides();
        return ResponseEntity.ok(ApiResponse.success("Rides retrieved successfully", rides));
    }

    @GetMapping("/rides/active")
    @Operation(summary = "Get active rides", description = "Returns list of currently active rides")
    public ResponseEntity<ApiResponse<List<RideResponse>>> getActiveRides(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access denied. Admin role required."));
        }

        List<RideResponse> rides = adminService.getActiveRides();
        return ResponseEntity.ok(ApiResponse.success("Active rides retrieved successfully", rides));
    }

    @GetMapping("/rides/user/{userId}")
    @Operation(summary = "Get rides by user", description = "Returns all rides for a specific user")
    public ResponseEntity<ApiResponse<List<RideResponse>>> getRidesByUser(
            @PathVariable Long userId,
            HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access denied. Admin role required."));
        }

        try {
            List<RideResponse> rides = adminService.getRidesByUser(userId);
            return ResponseEntity.ok(ApiResponse.success("User rides retrieved successfully", rides));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/rides/bike/{bikeId}")
    @Operation(summary = "Get rides by bike", description = "Returns all rides for a specific bike")
    public ResponseEntity<ApiResponse<List<RideResponse>>> getRidesByBike(
            @PathVariable String bikeId,
            HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access denied. Admin role required."));
        }

        try {
            List<RideResponse> rides = adminService.getRidesByBike(bikeId);
            return ResponseEntity.ok(ApiResponse.success("Bike rides retrieved successfully", rides));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/rides/{rideId}/force-end")
    @Operation(summary = "Force end ride", description = "Emergency command to end an active ride")
    public ResponseEntity<ApiResponse<RideResponse>> forceEndRide(
            @PathVariable Long rideId,
            HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access denied. Admin role required."));
        }

        try {
            RideResponse ride = adminService.forceEndRide(rideId);
            return ResponseEntity.ok(ApiResponse.success("Ride force-ended successfully", ride));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
