package com.labs.server.repository;

import com.labs.server.entity.LabTestCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
