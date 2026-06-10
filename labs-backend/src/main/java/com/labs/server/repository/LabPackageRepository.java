package com.labs.server.repository;

import com.labs.server.entity.LabPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LabPackageRepository extends JpaRepository<LabPackage, UUID> {

    // LEFT JOIN FETCH + DISTINCT — without it the LAZY @OneToMany returns an
    // uninitialised proxy that Jackson serialises as []. Same pattern as
    // HealthPackageRepository.
    @Query("SELECT DISTINCT p FROM LabPackage p " +
           "LEFT JOIN FETCH p.items " +
           "WHERE p.hospitalId = :hospitalId " +
           "ORDER BY p.category ASC, p.name ASC")
    List<LabPackage> findByHospitalId(@Param("hospitalId") UUID hospitalId);

    @Query("SELECT DISTINCT p FROM LabPackage p " +
           "LEFT JOIN FETCH p.items " +
           "WHERE p.hospitalId = :hospitalId AND p.active = true " +
           "ORDER BY p.category ASC, p.name ASC")
    List<LabPackage> findActiveByHospitalId(@Param("hospitalId") UUID hospitalId);
}
