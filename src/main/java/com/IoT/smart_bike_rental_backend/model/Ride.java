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

    /** Duration in minutes — calculated at ride end. */
    private Long durationMinutes;

    /**
     * Payment states:
     *   PENDING            – ride created, not yet ended
     *   PENDING_PAYMENT    – ride ended, Chapa checkout issued
     *   COMPLETED          – Chapa webhook confirmed success
     *   PAYMENT_FAILED     – Chapa reported failure or verification mismatch
     *   ADMIN_CLOSED       – force-ended by admin
     */
    @Column(name = "payment_status")
    private String paymentStatus = "PENDING";

    /** Chapa transaction reference, stored on the Ride for webhook lookup. */
    @Column(name = "chapa_tx_ref", unique = true)
    private String chapaTxRef;

    @Column(name = "chapa_auth_id")
    private String chapaAuthId;

    @Column(name = "chapa_charge_id")
    private String chapaChargeId;

    @Column(name = "is_active")
    private boolean active;

    private Double startLatitude;
    private Double startLongitude;
    private Double endLatitude;
    private Double endLongitude;

    @PrePersist
    protected void onCreate() {
        if (startTime == null) startTime = LocalDateTime.now();
    }
}
