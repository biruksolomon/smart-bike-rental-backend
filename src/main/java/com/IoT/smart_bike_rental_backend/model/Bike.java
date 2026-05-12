package com.IoT.smart_bike_rental_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class Bike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String bikeId;

    // Bike type: BMX, Phoenix, Mountain, Road, Hybrid, etc.
    @Column(nullable = false)
    private String bikeType = "Mountain";

    // Bike size: 16, 18, 20, 24, 26, 27.5, 29 (in inches)
    @Column(nullable = false)
    private String bikeSize = "26";

    // Status: LOCKED, IN_USE, MAINTENANCE, UNAVAILABLE
    private String status = "LOCKED";

    @Column(unique = true)
    private String qrCode;

    @ManyToOne
    @JoinColumn(name = "current_user_id", nullable = true)
    @JsonIgnoreProperties({"password", "passwordResetCode", "passwordResetCodeExpiry"})
    private User currentUser;

    private LocalDateTime lastUpdated;

    // Battery level percentage (0-100)
    private Integer batteryLevel;

    // GPS location
    private Double latitude;
    private Double longitude;

    // Bike condition/health status
    @Column(name = "is_usable")
    private Boolean isUsable = true;

    @PrePersist
    protected void onCreate() {
        if (lastUpdated == null) {
            lastUpdated = LocalDateTime.now();
        }
        if (status == null) {
            status = "LOCKED";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        lastUpdated = LocalDateTime.now();
    }

    // Helper method to check if bike is available for rental
    public boolean isAvailable() {
        return "LOCKED".equals(status) && Boolean.TRUE.equals(isUsable);
    }
}
