package com.labs.server.repository;

import com.labs.server.entity.HealthPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface HealthPackageRepository extends JpaRepository<HealthPackage, UUID> {

    // LEFT JOIN FETCH + DISTINCT — without it the LAZY @OneToMany returns an
    // uninitialised proxy that Jackson serialises as []. Bug surface: package
    // cards showing "0 tests" despite tests being saved.
    @Query("SELECT DISTINCT p FROM HealthPackage p " +
           "LEFT JOIN FETCH p.tests " +
           "WHERE p.hospital.id = :hospitalId " +
           "ORDER BY p.category ASC, p.name ASC")
    List<HealthPackage> findByHospitalId(@Param("hospitalId") UUID hospitalId);

    @Query("SELECT DISTINCT p FROM HealthPackage p " +
           "LEFT JOIN FETCH p.tests " +
           "WHERE p.hospital.id = :hospitalId AND p.active = true " +
           "ORDER BY p.category ASC, p.name ASC")
    List<HealthPackage> findActiveByHospitalId(@Param("hospitalId") UUID hospitalId);
}
