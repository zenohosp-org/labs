package com.labs.server.repository;

import com.labs.server.entity.LabOrder;
import com.labs.server.entity.LabStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LabOrderRepository extends JpaRepository<LabOrder, Long> {

    // Tenant-scoped by-id lookup — a lab order in another hospital is
    // indistinguishable from one that doesn't exist, so BIGSERIAL ids can't be
    // enumerated across tenants (fixes the cross-tenant IDOR on every by-id op).
    Optional<LabOrder> findByIdAndHospitalId(Long id, UUID hospitalId);

    List<LabOrder> findByPatientIdAndHospitalIdOrderByCreatedAtDesc(Integer patientId, UUID hospitalId);

    List<LabOrder> findByAdmissionIdAndHospitalIdOrderByCreatedAtDesc(UUID admissionId, UUID hospitalId);

    List<LabOrder> findByHospitalIdOrderByCreatedAtDesc(UUID hospitalId);

    List<LabOrder> findByHospitalIdAndStatusOrderByCreatedAtDesc(UUID hospitalId, LabStatus status);

    // Mirrors radiology's main-branch "completed reports" union.
    List<LabOrder> findByHospitalIdAndStatusInOrderByCreatedAtDesc(
            UUID hospitalId, Collection<LabStatus> statuses);

    List<LabOrder> findByPatientIdOrderByCreatedAtDesc(Integer patientId);

    long countByHospitalIdAndStatus(UUID hospitalId, LabStatus status);

    long countByHospitalIdAndStatusIn(UUID hospitalId, Collection<LabStatus> statuses);

    List<LabOrder> findByAdmissionIdOrderByCreatedAtDesc(UUID admissionId);
}
