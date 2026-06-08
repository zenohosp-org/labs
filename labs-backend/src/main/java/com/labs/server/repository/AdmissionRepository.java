package com.labs.server.repository;

import com.labs.server.entity.Admission;
import com.labs.server.entity.AdmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdmissionRepository extends JpaRepository<Admission, UUID> {

    List<Admission> findByPatient_IdOrderByAdmissionDateDesc(Integer patientId);

    /** Used by checkup auto-bill to route IPD vs OPD invoice creation. */
    Optional<Admission> findByPatient_IdAndStatus(Integer patientId, AdmissionStatus status);
}
