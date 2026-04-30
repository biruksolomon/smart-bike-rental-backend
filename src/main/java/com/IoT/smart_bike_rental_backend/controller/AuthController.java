package com.IoT.smart_bike_rental_backend.controller;

import com.IoT.smart_bike_rental_backend.dto.AuthRequest;
import com.IoT.smart_bike_rental_backend.dto.AuthResponse;
import com.IoT.smart_bike_rental_backend.dto.TokenValidationResponse;
import com.IoT.smart_bike_rental_backend.dto.UserProfileResponse;
import com.IoT.smart_bike_rental_backend.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Register a new user
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest request) {
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
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
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