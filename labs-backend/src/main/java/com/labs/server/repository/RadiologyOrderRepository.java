package com.labs.server.repository;

import com.labs.server.entity.RadiologyOrder;
import com.labs.server.entity.RadiologyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RadiologyOrderRepository extends JpaRepository<RadiologyOrder, Long> {

    // Tenant-scoped lookups. Order ids are BIGSERIAL and therefore trivially
    // enumerable, so every by-id / by-patient / by-admission read and mutation
    // resolves through one of these: a foreign tenant's row simply doesn't
    // match, and the caller gets "not found" instead of another hospital's data.
    Optional<RadiologyOrder> findByIdAndHospitalId(Long id, UUID hospitalId);

    List<RadiologyOrder> findByPatientIdAndHospitalIdOrderByCreatedAtDesc(Integer patientId, UUID hospitalId);

    List<RadiologyOrder> findByAdmissionIdAndHospitalIdOrderByCreatedAtDesc(UUID admissionId, UUID hospitalId);

    List<RadiologyOrder> findByHospitalIdOrderByCreatedAtDesc(UUID hospitalId);

    List<RadiologyOrder> findByHospitalIdAndStatusOrderByCreatedAtDesc(UUID hospitalId, RadiologyStatus status);

    // "Completed report" set — REPORT_GENERATED + BILLED. Auto-billing flips
    // priced orders straight to BILLED inside the same transaction; without
    // the IN-clause variant, auto-billed reports vanish from the reports list.
    List<RadiologyOrder> findByHospitalIdAndStatusInOrderByCreatedAtDesc(
            UUID hospitalId, Collection<RadiologyStatus> statuses);

    long countByHospitalIdAndStatus(UUID hospitalId, RadiologyStatus status);

    long countByHospitalIdAndStatusIn(UUID hospitalId, Collection<RadiologyStatus> statuses);
}
