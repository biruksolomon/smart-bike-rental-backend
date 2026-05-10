package com.IoT.smart_bike_rental_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ride")
@Data
public class Ride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    @JsonIgnoreProperties({"password", "passwordResetCode", "passwordResetCodeExpiry"})
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bike_id", referencedColumnName = "id")
    @JsonIgnoreProperties({"currentUser"})
    private Bike bike;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @Column(precision = 10, scale = 2)
    private BigDecimal cost;

    // Duration in minutes (calculated field)
    private Long durationMinutes;

    // Payment status for the ride
    @Column(name = "payment_status")
    private String paymentStatus = "PENDING";

    @Column(name = "is_active")
    private boolean active;

    // Start location (optional - for GPS tracking)
    private Double startLatitude;
    private Double startLongitude;

    // End location (optional - for GPS tracking)
    private Double endLatitude;
    private Double endLongitude;

    @PrePersist
    protected void onCreate() {
        if (startTime == null) {
            startTime = LocalDateTime.now();
        }
    }
}
