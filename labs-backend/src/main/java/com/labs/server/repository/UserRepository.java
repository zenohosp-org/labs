package com.labs.server.repository;

import com.labs.server.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    List<User> findByHospital_IdOrderByFirstName(UUID hospitalId);
}
