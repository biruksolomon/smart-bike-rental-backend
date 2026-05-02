package com.IoT.smart_bike_rental_backend.service;

import com.IoT.smart_bike_rental_backend.dto.AuthRequest;
import com.IoT.smart_bike_rental_backend.dto.AuthResponse;
import com.IoT.smart_bike_rental_backend.dto.PasswordResetConfirm;
import com.IoT.smart_bike_rental_backend.dto.PasswordResetRequest;
import com.IoT.smart_bike_rental_backend.dto.TokenValidationResponse;
import com.IoT.smart_bike_rental_backend.dto.UserProfileResponse;
import com.IoT.smart_bike_rental_backend.model.User;
import com.IoT.smart_bike_rental_backend.repository.UserRepository;
import com.IoT.smart_bike_rental_backend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public AuthResponse register(AuthRequest request) {
        // Check if user already exists
        Optional<User> existingUser = userRepository.findByEmail(request.getEmail());
        if (existingUser.isPresent()) {
            return AuthResponse.builder()
                    .error(true)
                    .message("User with this email already exists")
                    .build();
        }

        // Validate required fields
        if (request.getEmail() == null || request.getPassword() == null || request.getName() == null) {
            return AuthResponse.builder()
                    .error(true)
                    .message("Email, password, and name are required")
                    .build();
        }

        // Create new user
        User user = new User();
        user.setEmail(request.getEmail());
        user.setName(request.getName());
        user.setPhone(request.getPhone());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole("USER");
        user.setIsActive(true);

        User savedUser = userRepository.save(user);

        // Generate token
        String token = jwtTokenProvider.generateToken(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getName(),
                savedUser.getRole()
        );

        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .name(savedUser.getName())
                .role(savedUser.getRole())
                .message("User registered successfully")
                .error(false)
                .build();
    }

    public AuthResponse login(String email, String password) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            return AuthResponse.builder()
                    .error(true)
                    .message("Invalid email or password")
                    .build();
        }

        User user = userOptional.get();

        // Verify password
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return AuthResponse.builder()
                    .error(true)
                    .message("Invalid email or password")
                    .build();
        }

        // Check if user is active
        if (!user.getIsActive()) {
            return AuthResponse.builder()
                    .error(true)
                    .message("User account is inactive")
                    .build();
        }

        // Generate token
        String token = jwtTokenProvider.generateToken(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole()
        );

        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .message("Login successful")
                .error(false)
                .build();
    }

    public TokenValidationResponse validateToken(String token) {
        try {
            boolean isValid = jwtTokenProvider.validateToken(token);
            if (!isValid || jwtTokenProvider.isTokenExpired(token)) {
                return TokenValidationResponse.builder()
                        .valid(false)
                        .message("Token is invalid or expired")
                        .build();
            }

            Long userId = Long.parseLong(jwtTokenProvider.getUserIdFromToken(token));
            String email = jwtTokenProvider.getEmailFromToken(token);

            return TokenValidationResponse.builder()
                    .valid(true)
                    .message("Token is valid")
                    .userId(userId)
                    .email(email)
                    .build();
        } catch (Exception e) {
            return TokenValidationResponse.builder()
                    .valid(false)
                    .message("Token is invalid")
                    .build();
        }
    }

    public AuthResponse refreshToken(String token) {
        try {
            if (!jwtTokenProvider.validateToken(token) || jwtTokenProvider.isTokenExpired(token)) {
                return AuthResponse.builder()
                        .error(true)
                        .message("Token is invalid or expired")
                        .build();
            }

            Long userId = Long.parseLong(jwtTokenProvider.getUserIdFromToken(token));
            Optional<User> userOptional = userRepository.findById(userId);

            if (userOptional.isEmpty()) {
                return AuthResponse.builder()
                        .error(true)
                        .message("User not found")
                        .build();
            }

            User user = userOptional.get();

            // Generate new token
            String newToken = jwtTokenProvider.generateToken(
                    user.getId(),
                    user.getEmail(),
                    user.getName(),
                    user.getRole()
            );

            return AuthResponse.builder()
                    .token(newToken)
                    .type("Bearer")
                    .userId(user.getId())
                    .email(user.getEmail())
                    .name(user.getName())
                    .role(user.getRole())
                    .message("Token refreshed successfully")
                    .error(false)
                    .build();
        } catch (Exception e) {
            return AuthResponse.builder()
                    .error(true)
                    .message("Failed to refresh token")
                    .build();
        }
    }

    public UserProfileResponse getUserProfile(Long userId) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }

        User user = userOptional.get();
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

    public UserProfileResponse updateUserProfile(Long userId, String name, String phone) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }

        User user = userOptional.get();
        if (name != null && !name.isEmpty()) {
            user.setName(name);
        }
        if (phone != null && !phone.isEmpty()) {
            user.setPhone(phone);
        }

        User updatedUser = userRepository.save(user);
        return UserProfileResponse.builder()
                .id(updatedUser.getId())
                .email(updatedUser.getEmail())
                .name(updatedUser.getName())
                .phone(updatedUser.getPhone())
                .role(updatedUser.getRole())
                .isActive(updatedUser.getIsActive())
                .createdAt(updatedUser.getCreatedAt())
                .updatedAt(updatedUser.getUpdatedAt())
                .build();
    }

    public AuthResponse changePassword(Long userId, String oldPassword, String newPassword) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            return AuthResponse.builder()
                    .error(true)
                    .message("User not found")
                    .build();
        }

        User user = userOptional.get();

        // Verify old password
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return AuthResponse.builder()
                    .error(true)
                    .message("Old password is incorrect")
                    .build();
        }

        // Update password
        user.setPassword(passwordEncoder.encode(newPassword));
        User updatedUser = userRepository.save(user);

        // Generate new token
        String token = jwtTokenProvider.generateToken(
                updatedUser.getId(),
                updatedUser.getEmail(),
                updatedUser.getName(),
                updatedUser.getRole()
        );

        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .userId(updatedUser.getId())
                .email(updatedUser.getEmail())
                .name(updatedUser.getName())
                .role(updatedUser.getRole())
                .message("Password changed successfully")
                .error(false)
                .build();
    }

    /**
     * Initiate password reset by email
     * Generates a 6-digit reset code and sends it to user's email
     */
    public AuthResponse forgotPassword(PasswordResetRequest request) {
        Optional<User> userOptional = userRepository.findByEmail(request.getEmail());
        if (userOptional.isEmpty()) {
            // Security: don't reveal if email exists
            log.warn("Password reset requested for non-existent email: {}", request.getEmail());
            return AuthResponse.builder()
                    .error(false)
                    .message("If the email exists, a password reset code has been sent")
                    .build();
        }

        User user = userOptional.get();

        // Generate 6-digit reset code (valid for 15 minutes)
        String resetCode = generateResetCode();
        user.setPasswordResetCode(resetCode);
        user.setPasswordResetCodeExpiry(LocalDateTime.now().plusMinutes(15));

        userRepository.save(user);

        // Send password reset email with code
        try {
            emailService.sendPasswordResetEmail(user.getEmail(), resetCode, user.getName());
            log.info("Password reset code sent to: {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", user.getEmail(), e);
            return AuthResponse.builder()
                    .error(true)
                    .message("Failed to send reset email. Please try again later")
                    .build();
        }

        return AuthResponse.builder()
                .error(false)
                .message("If the email exists, a password reset code has been sent")
                .build();
    }

    /**
     * Reset password using reset code
     */
    public AuthResponse resetPassword(PasswordResetConfirm request) {
        // Find user by email
        Optional<User> userOptional = userRepository.findByEmail(request.getEmail());

        if (userOptional.isEmpty()) {
            return AuthResponse.builder()
                    .error(true)
                    .message("Email not found")
                    .build();
        }

        User user = userOptional.get();

        // Check if reset code exists and matches
        if (user.getPasswordResetCode() == null || !user.getPasswordResetCode().equals(request.getCode())) {
            return AuthResponse.builder()
                    .error(true)
                    .message("Invalid reset code")
                    .build();
        }

        // Check if code has expired
        if (user.getPasswordResetCodeExpiry() == null ||
                LocalDateTime.now().isAfter(user.getPasswordResetCodeExpiry())) {
            return AuthResponse.builder()
                    .error(true)
                    .message("Reset code has expired")
                    .build();
        }

        // Validate new password
        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            return AuthResponse.builder()
                    .error(true)
                    .message("Password must be at least 6 characters long")
                    .build();
        }

        // Update password and clear reset code
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordResetCode(null);
        user.setPasswordResetCodeExpiry(null);

        User updatedUser = userRepository.save(user);
        log.info("Password reset successful for user: {}", updatedUser.getEmail());

        // Generate new token for auto-login after reset
        String token = jwtTokenProvider.generateToken(
                updatedUser.getId(),
                updatedUser.getEmail(),
                updatedUser.getName(),
                updatedUser.getRole()
        );

        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .userId(updatedUser.getId())
                .email(updatedUser.getEmail())
                .name(updatedUser.getName())
                .role(updatedUser.getRole())
                .message("Password reset successfully")
                .error(false)
                .build();
    }

    /**
     * Validate password reset code
     */
    public AuthResponse validateResetCode(String email, String code) {
        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isEmpty()) {
            return AuthResponse.builder()
                    .error(true)
                    .message("Email not found")
                    .build();
        }

        User user = userOptional.get();

        // Check if reset code matches
        if (user.getPasswordResetCode() == null || !user.getPasswordResetCode().equals(code)) {
            return AuthResponse.builder()
                    .error(true)
                    .message("Invalid reset code")
                    .build();
        }

        if (user.getPasswordResetCodeExpiry() == null ||
                LocalDateTime.now().isAfter(user.getPasswordResetCodeExpiry())) {
            return AuthResponse.builder()
                    .error(true)
                    .message("Reset code has expired")
                    .build();
        }

        return AuthResponse.builder()
                .error(false)
                .message("Reset code is valid")
                .build();
    }

    /**
     * Generate a random 6-digit reset code
     */
    private String generateResetCode() {
        int code = (int) (Math.random() * 900000) + 100000;
        return String.valueOf(code);
    }
}
