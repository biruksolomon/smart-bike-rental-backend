package com.IoT.smart_bike_rental_backend.repository;

import com.IoT.smart_bike_rental_backend.model.Bike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface Bikerepository extends JpaRepository<Bike, Long> {
    Optional<Bike> findByBikeId(String bikeId);
    Optional<Bike> findByQrCode(String qrCode);
}

