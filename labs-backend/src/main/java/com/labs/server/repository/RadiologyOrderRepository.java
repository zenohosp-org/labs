package com.labs.server.repository;

import com.labs.server.entity.RadiologyOrder;
import com.labs.server.entity.RadiologyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RadiologyOrderRepository extends JpaRepository<RadiologyOrder, Long> {

    List<RadiologyOrder> findByHospitalIdOrderByCreatedAtDesc(UUID hospitalId);

    List<RadiologyOrder> findByHospitalIdAndStatusOrderByCreatedAtDesc(UUID hospitalId, RadiologyStatus status);

    // "Completed report" set — REPORT_GENERATED + BILLED. Auto-billing flips
    // priced orders straight to BILLED inside the same transaction; without
    // the IN-clause variant, auto-billed reports vanish from the reports list.
    List<RadiologyOrder> findByHospitalIdAndStatusInOrderByCreatedAtDesc(
            UUID hospitalId, Collection<RadiologyStatus> statuses);

    List<RadiologyOrder> findByPatientIdOrderByCreatedAtDesc(Integer patientId);

    long countByHospitalIdAndStatus(UUID hospitalId, RadiologyStatus status);

    long countByHospitalIdAndStatusIn(UUID hospitalId, Collection<RadiologyStatus> statuses);

    List<RadiologyOrder> findByAdmissionIdOrderByCreatedAtDesc(UUID admissionId);
}
