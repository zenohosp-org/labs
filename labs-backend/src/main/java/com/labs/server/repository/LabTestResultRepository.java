package com.labs.server.repository;

import com.labs.server.entity.LabTestResult;
import com.labs.server.entity.ResultStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LabTestResultRepository extends JpaRepository<LabTestResult, Long> {

    List<LabTestResult> findByLabOrderIdOrderByCreatedAtAsc(Long labOrderId);

    List<LabTestResult> findByLabOrderIdAndResultStatusOrderByCreatedAtAsc(Long labOrderId, ResultStatus status);

    long countByLabOrderId(Long labOrderId);

    long countByLabOrderIdAndResultStatus(Long labOrderId, ResultStatus status);

    /**
     * Most recent FINAL result for the same patient + test_code (used by delta
     * check). We don't store patient_id on lab_test_result — join through
     * lab_orders so the source of truth stays single.
     *
     * Excludes the current order so an in-progress entry doesn't compare
     * against itself.
     */
    @Query("""
        SELECT r FROM LabTestResult r
        WHERE r.hospitalId = :hospitalId
          AND r.testCode   = :testCode
          AND r.resultStatus IN (com.labs.server.entity.ResultStatus.FINAL,
                                 com.labs.server.entity.ResultStatus.CORRECTED)
          AND r.labOrderId IN (
              SELECT o.id FROM LabOrder o
              WHERE o.patient.id = :patientId
                AND o.id <> :excludeOrderId
          )
        ORDER BY r.verifiedAt DESC NULLS LAST, r.createdAt DESC
    """)
    List<LabTestResult> findPriorFinalForDelta(
            @Param("hospitalId") UUID hospitalId,
            @Param("patientId")  Integer patientId,
            @Param("testCode")   String testCode,
            @Param("excludeOrderId") Long excludeOrderId);

    /**
     * Active row for an order + test_code (i.e. not superseded by an amendment).
     * Used to decide whether create-result should INSERT a new row or update an
     * existing PENDING row.
     */
    Optional<LabTestResult> findFirstByLabOrderIdAndTestCodeAndAmendmentOfIdIsNullOrderByCreatedAtDesc(
            Long labOrderId, String testCode);
}
