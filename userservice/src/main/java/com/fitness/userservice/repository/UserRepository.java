package com.fitness.userservice.repository;

import com.fitness.userservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email); // Change from `User` to `Optional<User>`

    boolean existsByKeycloakId(String keycloakId);
    Optional<User> findByKeycloakId(String keycloakId); // Add this method
}