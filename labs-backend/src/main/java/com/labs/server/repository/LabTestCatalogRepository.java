package com.labs.server.repository;

import com.labs.server.entity.LabTestCatalog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LabTestCatalogRepository extends JpaRepository<LabTestCatalog, Long> {

    long countByHospitalId(UUID hospitalId);

    List<LabTestCatalog> findByHospitalIdOrderByCategoryAscDisplayOrderAscNameAsc(UUID hospitalId);

    List<LabTestCatalog> findByHospitalIdAndActiveTrueOrderByCategoryAscDisplayOrderAscNameAsc(UUID hospitalId);

    List<LabTestCatalog> findByHospitalIdAndParentPanelCodeOrderByDisplayOrderAsc(UUID hospitalId, String parentPanelCode);

    Optional<LabTestCatalog> findByHospitalIdAndTestCode(UUID hospitalId, String testCode);

    /**
     * Search by name / test_code / aliases — used by the package + range
     * editor pickers. Active rows only; case-insensitive substring match.
     * Caller passes Pageable for top-N suggestions.
     */
    @Query("""
        SELECT t FROM LabTestCatalog t
        WHERE t.hospitalId = :hospitalId
          AND t.active = true
          AND (
                LOWER(t.name)      LIKE LOWER(CONCAT('%', :q, '%'))
             OR LOWER(t.testCode)  LIKE LOWER(CONCAT('%', :q, '%'))
             OR LOWER(COALESCE(t.aliases, '')) LIKE LOWER(CONCAT('%', :q, '%'))
             OR LOWER(COALESCE(t.loincCode, '')) LIKE LOWER(CONCAT('%', :q, '%'))
          )
        ORDER BY
          CASE WHEN LOWER(t.testCode) = LOWER(:q) THEN 0
               WHEN LOWER(t.name)     = LOWER(:q) THEN 1
               ELSE 2 END,
          t.isPanel DESC,
          t.name ASC
    """)
    List<LabTestCatalog> searchByHospital(@Param("hospitalId") UUID hospitalId,
                                          @Param("q") String q,
                                          Pageable page);
}
