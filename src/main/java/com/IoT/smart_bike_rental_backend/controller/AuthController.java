package com.IoT.smart_bike_rental_backend.controller;

import com.IoT.smart_bike_rental_backend.dto.AuthRequest;
import com.IoT.smart_bike_rental_backend.dto.AuthResponse;
import com.IoT.smart_bike_rental_backend.dto.TokenValidationResponse;
import com.IoT.smart_bike_rental_backend.dto.UserProfileResponse;
import com.IoT.smart_bike_rental_backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User authentication endpoints")
public class AuthController {

    private final AuthService authService;

    /**
     * Register a new user
     * POST /api/auth/register
     */
    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Create a new user account with email and password")
    @ApiResponse(responseCode = "201", description = "User registered successfully")
    @ApiResponse(responseCode = "400", description = "Invalid input or user already exists")
    public ResponseEntity<?> register(@org.springframework.web.bind.annotation.RequestBody AuthRequest request) {
        AuthResponse response = authService.register(request);
        if (response.getError()) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * User login
     * POST /api/auth/login
     */
    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate user with email and password")
    @ApiResponse(responseCode = "200", description = "Login successful, returns JWT token")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    public ResponseEntity<?> login(@org.springframework.web.bind.annotation.RequestBody AuthRequest request) {
        AuthResponse response = authService.login(request.getEmail(), request.getPassword());
        if (response.getError()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Validate JWT token
     * POST /api/auth/validate?token=...
     */
    @PostMapping("/validate")
    @Operation(summary = "Validate JWT token", description = "Check if a JWT token is valid and not expired")
    @ApiResponse(responseCode = "200", description = "Token is valid")
    @ApiResponse(responseCode = "400", description = "Token is invalid or expired")
    public ResponseEntity<?> validateToken(@RequestParam String token) {
        TokenValidationResponse response = authService.validateToken(token);
        return ResponseEntity.ok(response);
    }

    /**
     * Refresh JWT token
     * POST /api/auth/refresh
     * Header: Authorization: Bearer <token>
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh JWT token", description = "Generate a new JWT token using a valid token")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Token refreshed successfully")
    @ApiResponse(responseCode = "401", description = "Invalid or expired token")
    public ResponseEntity<?> refreshToken(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body("Invalid authorization header");
        }

        String token = authHeader.substring(7);
        AuthResponse response = authService.refreshToken(token);
        if (response.getError()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Get user profile
     * GET /api/auth/profile?userId=...
     */
    @GetMapping("/profile")
    @Operation(summary = "Get user profile", description = "Retrieve user profile information by user ID")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Profile retrieved successfully")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<?> getProfile(@RequestParam Long userId) {
        try {
            UserProfileResponse response = authService.getUserProfile(userId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Update user profile
     * PUT /api/auth/profile?userId=...&name=...&phone=...
     */
    @PutMapping("/profile")
    @Operation(summary = "Update user profile", description = "Update user profile information (name and/or phone)")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Profile updated successfully")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<?> updateProfile(
            @RequestParam Long userId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phone) {
        try {
            UserProfileResponse response = authService.updateUserProfile(userId, name, phone);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Change password
     * POST /api/auth/change-password?userId=...&oldPassword=...&newPassword=...
     */
    @PostMapping("/change-password")
    @Operation(summary = "Change user password", description = "Change user password with old password verification")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Password changed successfully")
    @ApiResponse(responseCode = "400", description = "Invalid old password or user not found")
    public ResponseEntity<?> changePassword(
            @RequestParam Long userId,
            @RequestParam String oldPassword,
            @RequestParam String newPassword) {
        AuthResponse response = authService.changePassword(userId, oldPassword, newPassword);
        if (response.getError()) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }
}
