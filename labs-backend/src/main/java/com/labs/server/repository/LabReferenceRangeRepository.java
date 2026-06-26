package com.labs.server.repository;

import com.labs.server.entity.LabReferenceRange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LabReferenceRangeRepository extends JpaRepository<LabReferenceRange, UUID> {

    List<LabReferenceRange> findByHospitalIdOrderByTestNameAscMinAgeYearsAsc(UUID hospitalId);

    long countByHospitalId(UUID hospitalId);

    // ── Phase 3 — FK lookups ─────────────────────────────────────────────
    List<LabReferenceRange> findByLabTestIdOrderBySexAscMinAgeYearsAsc(Long labTestId);

    long countByLabTestId(Long labTestId);

    /**
     * Aggregate range counts per lab_test_id for the hospital — used by the
     * Test Catalog list view to show "N ranges" per row in one query.
     * Returns Object[]{labTestId (Long), count (Long)}.
     */
    @Query("""
        SELECT r.labTestId, COUNT(r)
        FROM LabReferenceRange r
        WHERE r.hospitalId = :hospitalId AND r.labTestId IS NOT NULL
        GROUP BY r.labTestId
    """)
    List<Object[]> countByHospitalGroupedByLabTestId(@Param("hospitalId") UUID hospitalId);

    /**
     * Lookup the candidate bands for a given test/sex/age tuple. Filters down
     * to active rows only and asks the DB to apply the age window so the
     * caller doesn't have to load every row.
     *
     * Multiple rows can come back when the catalogue has overlapping bands
     * (e.g. "Hemoglobin" with both an age-stratified and a sex-stratified
     * band) — the service picks the most specific one.
     */
    @Query("""
        SELECT r FROM LabReferenceRange r
        WHERE r.hospitalId = :hospitalId
          AND LOWER(r.testName) = LOWER(:testName)
          AND r.isActive = true
          AND (r.sex = :sex OR r.sex = 'ANY')
          AND (r.minAgeYears IS NULL OR r.minAgeYears <= :ageYears)
          AND (r.maxAgeYears IS NULL OR r.maxAgeYears >= :ageYears)
          AND (r.effectiveTo IS NULL OR r.effectiveTo >= CURRENT_DATE)
          AND (r.specialState IS NULL OR r.specialState = :specialState)
        """)
    List<LabReferenceRange> findCandidates(
            @Param("hospitalId") UUID hospitalId,
            @Param("testName") String testName,
            @Param("sex") String sex,
            @Param("ageYears") int ageYears,
            @Param("specialState") String specialState);
}
