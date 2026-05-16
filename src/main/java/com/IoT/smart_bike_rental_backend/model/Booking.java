package com.IoT.smart_bike_rental_backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Booking model - represents a booking before the ride starts
 *
 * Flow: User scans QR -> Creates Booking -> Returns bookingId ->
 *       User clicks Start -> Creates Ride using bookingId -> Returns rideId
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
    private String status; // PENDING, CONFIRMED, RIDE_STARTED, CANCELLED

    @Column(name = "qr_code")
    private String qrCode;

    @Column(name = "notes")
    private String notes;
}
