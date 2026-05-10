package com.IoT.smart_bike_rental_backend.repository;

import com.IoT.smart_bike_rental_backend.model.Bike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface Bikerepository extends JpaRepository<Bike, Long> {

    // Find bike by unique bike ID (e.g., "BIKE001")
    Optional<Bike> findByBikeId(String bikeId);

    // Find bike by QR code
    Optional<Bike> findByQrCode(String qrCode);

    // Find all bikes by status
    List<Bike> findByStatus(String status);

    // Find all available bikes (LOCKED and usable)
    List<Bike> findByStatusAndIsUsableTrue(String status);

    // Find bikes that are usable
    List<Bike> findByIsUsableTrue();

    // Find bikes under maintenance
    List<Bike> findByIsUsableFalse();
}
