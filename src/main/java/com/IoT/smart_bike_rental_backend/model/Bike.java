package com.IoT.smart_bike_rental_backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class Bike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String bikeId;

    private String status;

    @Column(unique = true)
    private String qrCode;

    @ManyToOne
    @JoinColumn(name = "current_user_id", nullable = true)
    private User currentUser;

    private LocalDateTime lastUpdated;
}
