package com.IoT.smart_bike_rental_backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String email;

    private String name;
    private String phone;
    private String password;

    @Column(name = "role")
    private String role = "USER";

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "password_reset_code")
    private String passwordResetCode;

    @Column(name = "password_reset_code_expiry")
    private LocalDateTime passwordResetCodeExpiry;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
