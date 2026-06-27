package com.labs.server.repository;

import com.labs.server.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    List<User> findByHospital_IdOrderByFirstName(UUID hospitalId);

    /**
     * Used by {@link com.labs.server.security.JwtFilter} to gate authentication on
     * the user existing AND being currently active. Mirrors HMS's posture (HMS
     * does {@code findByEmail} on every request); labs uses id because our JWT
     * claim is {@code sub=<userId UUID>}, not email.
     */
    Optional<User> findByIdAndIsActiveTrue(UUID id);
}
