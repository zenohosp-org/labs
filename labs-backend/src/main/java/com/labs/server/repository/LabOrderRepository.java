package com.labs.server.repository;

import com.labs.server.entity.LabOrder;
import com.labs.server.entity.LabStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LabOrderRepository extends JpaRepository<LabOrder, Long> {

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
