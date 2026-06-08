package com.labs.server.repository;

import com.labs.server.entity.HospitalService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HospitalServiceRepository extends JpaRepository<HospitalService, UUID> {

    List<HospitalService> findByHospitalIdOrderByName(UUID hospitalId);
}
