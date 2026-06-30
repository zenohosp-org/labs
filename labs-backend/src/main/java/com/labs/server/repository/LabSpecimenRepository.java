package com.labs.server.repository;

import com.labs.server.entity.LabSpecimen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LabSpecimenRepository extends JpaRepository<LabSpecimen, Long> {

    List<LabSpecimen> findByLabOrderIdOrderByCreatedAtAsc(Long labOrderId);

    long countByLabOrderId(Long labOrderId);

    Optional<LabSpecimen> findByBarcode(String barcode);

    boolean existsByBarcode(String barcode);

    // ── Phase 6 — collection / receiving console counters ───────────────

    /** Specimens drawn within a window, scoped to hospital. */
    long countByHospitalIdAndCollectedAtBetween(UUID hospitalId, LocalDateTime from, LocalDateTime to);

    /** Specimens rejected within a window (rejection_reason set + rejected). */
    long countByHospitalIdAndRejectedTrueAndRejectedAtBetween(UUID hospitalId, LocalDateTime from, LocalDateTime to);

    /**
     * Collected but not yet received within a window — drives the
     * "awaiting receive" tile on the collection dashboard.
     */
    @Query("""
        SELECT COUNT(s) FROM LabSpecimen s
        WHERE s.hospitalId = :hospitalId
          AND s.collectedAt BETWEEN :from AND :to
          AND s.receivedAt IS NULL
          AND s.rejected = false
    """)
    long countCollectedNotReceived(@Param("hospitalId") UUID hospitalId,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);

    /**
     * List view for the Collections page — every specimen collected within
     * the window for a hospital, newest first. Includes rejected rows so the
     * UI can render them with a red pill (caller filters if needed).
     */
    @Query("""
        SELECT s FROM LabSpecimen s
        WHERE s.hospitalId = :hospitalId
          AND s.collectedAt BETWEEN :from AND :to
        ORDER BY s.collectedAt DESC
    """)
    List<LabSpecimen> findCollectedInRange(@Param("hospitalId") UUID hospitalId,
                                            @Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to);
}
