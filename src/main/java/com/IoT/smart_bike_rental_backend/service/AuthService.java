package com.IoT.smart_bike_rental_backend.service;

import com.IoT.smart_bike_rental_backend.dto.AuthRequest;
import com.IoT.smart_bike_rental_backend.dto.AuthResponse;
import com.IoT.smart_bike_rental_backend.dto.TokenValidationResponse;
import com.IoT.smart_bike_rental_backend.dto.UserProfileResponse;
import com.IoT.smart_bike_rental_backend.model.User;
import com.IoT.smart_bike_rental_backend.repository.UserRepository;
import com.IoT.smart_bike_rental_backend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

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
}