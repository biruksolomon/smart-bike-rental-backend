package com.IoT.smart_bike_rental_backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Booking — created when a user scans a QR code.
 *
 * Status flow: PENDING → RIDE_STARTED | CANCELLED
 *
 * The bookingId returned here is passed to RideService.startRide()
 * to create the Ride and send the MQTT UNLOCK command.
 */
@Entity
@Table(name = "bookings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bike_id", nullable = false)
    private Bike bike;

    @Column(name = "booking_time", nullable = false)
    private LocalDateTime bookingTime;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "qr_code")
    private String qrCode;

    @Column(name = "notes")
    private String notes;
}
