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
    Optional<Ride> findByBikeAndActiveTrue(Bike bike);
    Optional<Ride> findByUserAndActiveTrue(User user);
    List<Ride>     findByUserIdOrderByStartTimeDesc(Long userId);
    List<Ride>     findByActiveTrue();
    List<Ride>     findByBikeOrderByStartTimeDesc(Bike bike);
    List<Ride>     findByEndTimeIsNull();
    List<Ride>     findByUser(User user);
    List<Ride>     findByUserAndEndTimeIsNull(User user);
    List<Ride>     findByBike(Bike bike);
    Optional<Ride> findByChapaTxRef(String chapaTxRef);
}
