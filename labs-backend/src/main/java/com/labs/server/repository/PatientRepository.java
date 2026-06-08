package com.labs.server.repository;

import com.labs.server.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, Integer> {

    @Query("""
        SELECT p FROM Patient p
        WHERE p.hospital.id = :hospitalId
          AND (
              LOWER(p.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(p.lastName)  LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(p.uhid)      LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(p.phone)     LIKE LOWER(CONCAT('%', :q, '%'))
          )
        ORDER BY p.firstName
        """)
    List<Patient> search(@Param("hospitalId") UUID hospitalId, @Param("q") String q);

    Optional<Patient> findByHospital_IdAndUhid(UUID hospitalId, String uhid);
}
