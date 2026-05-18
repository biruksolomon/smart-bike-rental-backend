package com.IoT.smart_bike_rental_backend.repository;

import com.IoT.smart_bike_rental_backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    List<User> findByRole(String role);
    List<User> findByIsActive(Boolean isActive);
    List<User> findByRoleAndIsActive(String role, Boolean isActive);
}
