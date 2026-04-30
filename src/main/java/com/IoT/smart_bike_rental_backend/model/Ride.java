package com.IoT.smart_bike_rental_backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "ride")
@Data
public class Ride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "bike_id", referencedColumnName = "id")
    private Bike bike;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private Double cost;

    @Column(name = "is_active")
    private boolean active;

    @PrePersist
    protected void onCreate() {
        if (startTime == null) {
            startTime = LocalDateTime.now();
        }
    }
}
