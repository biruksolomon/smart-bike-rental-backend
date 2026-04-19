package com.IoT.smart_bike_rental_backend.repository;

import com.IoT.smart_bike_rental_backend.model.Ride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface Riderepository extends JpaRepository<Ride, Long> {
   Optional<Ride> findByBikeIdAndActiveTrue(Long bikeId);
   List<Ride> findByUserIdOrderByStartTimeDesc(Long userId);
}

