package com.labs.server.repository;

import com.labs.server.entity.ReportTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReportTemplateRepository extends JpaRepository<ReportTemplate, Long> {

    List<ReportTemplate> findByHospitalIdOrderByIsDefaultDescNameAsc(UUID hospitalId);

    long countByHospitalId(UUID hospitalId);

    /** Default template for a hospital (active + is_default=true). Caller falls back when absent. */
    Optional<ReportTemplate> findFirstByHospitalIdAndIsDefaultTrueAndActiveTrueOrderByIdAsc(UUID hospitalId);

    /** Discipline-specific template lookup; the service falls back to default when null. */
    Optional<ReportTemplate> findFirstByHospitalIdAndDisciplineAndActiveTrueOrderByIsDefaultDescIdAsc(
            UUID hospitalId, String discipline);
}
