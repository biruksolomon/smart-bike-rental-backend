package com.IoT.smart_bike_rental_backend.repository;

import com.IoT.smart_bike_rental_backend.model.Bike;
import com.IoT.smart_bike_rental_backend.model.Ride;
import com.IoT.smart_bike_rental_backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface Riderepository extends JpaRepository<Ride, Long> {

   // Find active ride by bike entity
   Optional<Ride> findByBikeAndActiveTrue(Bike bike);

   // Find active ride by user entity
   Optional<Ride> findByUserAndActiveTrue(User user);

   // Find ride history for a user ordered by start time
   List<Ride> findByUserIdOrderByStartTimeDesc(Long userId);

   // Find all active rides
   List<Ride> findByActiveTrue();

   // Find rides by bike
   List<Ride> findByBikeOrderByStartTimeDesc(Bike bike);

   // Find all active rides (endTime is null)
   List<Ride> findByEndTimeIsNull();

   // Find rides by user entity
   List<Ride> findByUser(User user);

   // Find active rides by user (no end time)
   List<Ride> findByUserAndEndTimeIsNull(User user);

   // Find rides by bike entity
   List<Ride> findByBike(Bike bike);
}
