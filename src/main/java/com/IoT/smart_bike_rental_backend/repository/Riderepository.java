package com.IoT.smart_bike_rental_backend.repository;

import com.IoT.smart_bike_rental_backend.model.Ride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface Riderepository extends JpaRepository<Ride,Long> {

   Ride findByBikeIdAndActiveTrue(String bikeId);
}
