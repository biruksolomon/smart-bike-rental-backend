package com.IoT.smart_bike_rental_backend.config;

import com.IoT.smart_bike_rental_backend.model.User;
import com.IoT.smart_bike_rental_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Creates the default admin user on first startup.
 * Safe to run on every restart — skips creation if the admin already exists.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email:admin@smartbike.com}")
    private String adminEmail;

    @Value("${admin.password:Admin@123}")
    private String adminPassword;

    @Value("${admin.name:System Administrator}")
    private String adminName;

    @Value("${admin.phone:+1234567890}")
    private String adminPhone;

    @Override
    public void run(String... args) {
        Optional<User> existing = userRepository.findByEmail(adminEmail);

        if (existing.isPresent()) {
            User admin = existing.get();
            if (!"ADMIN".equals(admin.getRole())) {
                admin.setRole("ADMIN");
                userRepository.save(admin);
                log.info("Promoted existing user {} to ADMIN role", adminEmail);
            } else {
                log.info("Admin user already exists: {}", adminEmail);
            }
            return;
        }

        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setName(adminName);
        admin.setPhone(adminPhone);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRole("ADMIN");
        admin.setIsActive(true);
        admin.setCreatedAt(LocalDateTime.now());
        admin.setUpdatedAt(LocalDateTime.now());

        userRepository.save(admin);

        log.info("============================================");
        log.info("Default Admin User Created Successfully!");
        log.info("Email: {}", adminEmail);
        log.info("Password: {}", adminPassword);
        log.info("Role: ADMIN");
        log.info("============================================");
        log.warn("IMPORTANT: Change the default admin password in production!");
    }
}
