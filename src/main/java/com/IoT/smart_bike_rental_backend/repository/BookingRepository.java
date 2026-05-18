package com.IoT.smart_bike_rental_backend.repository;

import com.IoT.smart_bike_rental_backend.model.Booking;
import com.IoT.smart_bike_rental_backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserAndStatus(User user, String status);
}
