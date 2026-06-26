package com.labs.server.service;

import com.labs.server.dto.CreateTestCatalogRequest;
import com.labs.server.dto.LabTestCatalogDTO;
import com.labs.server.entity.LabReferenceRange;
import com.labs.server.entity.LabTestCatalog;
import com.labs.server.repository.LabReferenceRangeRepository;
import com.labs.server.repository.LabTestCatalogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CRUD for the per-hospital test catalogue plus lazy-seed of defaults on
 * first access (mirrors {@link LabReferenceRangeService} pattern).
 *
 * Used by both the admin Settings UI (CRUD) and by {@link LabTestResultService}
 * (lookup by test_code when validating per-analyte result entries).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LabTestCatalogService {

    private final LabTestCatalogRepository repository;
    private final LabReferenceRangeRepository rangeRepository;
    private final LabTestCatalogSeeder seeder;          // separate bean — proxy applies, REQUIRES_NEW writable tx
    private final AuditService auditService;

    public List<LabTestCatalogDTO> list(UUID hospitalId, boolean activeOnly) {
        if (repository.countByHospitalId(hospitalId) == 0) {
            // Goes through the Spring AOP proxy because seeder is a DIFFERENT
            // bean — so seeder.seedFor's @Transactional(REQUIRES_NEW) actually
            // opens a writable transaction and the inserts flush.
            seeder.seedFor(hospitalId);
        }
        List<LabTestCatalog> rows = activeOnly
                ? repository.findByHospitalIdAndActiveTrueOrderByCategoryAscDisplayOrderAscNameAsc(hospitalId)
                : repository.findByHospitalIdOrderByCategoryAscDisplayOrderAscNameAsc(hospitalId);

        // Single query → map of labTestId → range count. Avoids N+1 when the
        // catalogue list page renders "N ranges" per row.
        Map<Long, Long> rangeCounts = new HashMap<>();
        for (Object[] row : rangeRepository.countByHospitalGroupedByLabTestId(hospitalId)) {
            if (row[0] != null) rangeCounts.put((Long) row[0], (Long) row[1]);
        }

        return rows.stream().map(r -> toDTOWithRangeCount(r, rangeCounts.getOrDefault(r.getId(), 0L)))
                .collect(Collectors.toList());
    }

    /**
     * Phase 3 — fuzzy search over name / test_code / aliases / LOINC. Used by
     * the package + range editor pickers. Active rows only, top {@code limit}.
     */
    public List<LabTestCatalogDTO> search(UUID hospitalId, String q, int limit) {
        if (q == null || q.isBlank()) return List.of();
        int cappedLimit = Math.min(Math.max(limit, 1), 50);
        return repository.searchByHospital(hospitalId, q.trim(), PageRequest.of(0, cappedLimit))
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * Phase 3 — list ranges that belong to a test. Tenant-checked against the
     * caller's hospital so a forged id can't expose cross-tenant data.
     */
    public List<LabReferenceRange> rangesFor(UUID hospitalId, Long labTestId) {
        LabTestCatalog test = loadForTenant(hospitalId, labTestId);
        return rangeRepository.findByLabTestIdOrderBySexAscMinAgeYearsAsc(test.getId());
    }

    public Optional<LabTestCatalog> findByCode(UUID hospitalId, String testCode) {
        if (hospitalId == null || testCode == null) return Optional.empty();
        return repository.findByHospitalIdAndTestCode(hospitalId, testCode);
    }

    /** Child analytes for a panel (CBC → 12 analytes). Empty when not a panel. */
    public List<LabTestCatalogDTO> expandPanel(UUID hospitalId, String panelCode) {
        return repository.findByHospitalIdAndParentPanelCodeOrderByDisplayOrderAsc(hospitalId, panelCode)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public LabTestCatalogDTO upsert(UUID hospitalId, CreateTestCatalogRequest req) {
        if (req.getTestCode() == null || req.getTestCode().isBlank()) {
            throw new RuntimeException("testCode is required");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new RuntimeException("name is required");
        }

        Optional<LabTestCatalog> existing = repository.findByHospitalIdAndTestCode(hospitalId, req.getTestCode());
        LabTestCatalog row = existing.orElseGet(() ->
                LabTestCatalog.builder().hospitalId(hospitalId).testCode(req.getTestCode()).build());

        LabTestCatalog before = existing.map(this::clone).orElse(null);
        copyInto(row, req);
        LabTestCatalog saved = repository.save(row);

        auditService.record("LabTestCatalog", saved.getId().toString(),
                existing.isPresent() ? "UPDATE" : "CREATE",
                hospitalId, before, saved);
        return toDTO(saved);
    }

    @Transactional
    public LabTestCatalogDTO toggle(UUID hospitalId, Long id) {
        LabTestCatalog row = loadForTenant(hospitalId, id);
        LabTestCatalog before = clone(row);
        row.setActive(!Boolean.TRUE.equals(row.getActive()));
        LabTestCatalog saved = repository.save(row);
        auditService.record("LabTestCatalog", saved.getId().toString(), "TOGGLE",
                hospitalId, before, saved);
        return toDTO(saved);
    }

    @Transactional
    public void delete(UUID hospitalId, Long id) {
        LabTestCatalog row = loadForTenant(hospitalId, id);
        repository.delete(row);
        auditService.record("LabTestCatalog", String.valueOf(id), "DELETE",
                hospitalId, row, null);
    }

    private LabTestCatalog loadForTenant(UUID hospitalId, Long id) {
        LabTestCatalog row = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Test catalog row not found: " + id));
        if (!hospitalId.equals(row.getHospitalId())) {
            throw new RuntimeException("Test catalog row does not belong to this hospital");
        }
        return row;
    }

    private void copyInto(LabTestCatalog row, CreateTestCatalogRequest r) {
        row.setLoincCode(r.getLoincCode());
        row.setName(r.getName());
        row.setAliases(r.getAliases());
        row.setCategory(r.getCategory());
        row.setDiscipline(r.getDiscipline());
        row.setSpecimenKind(r.getSpecimenKind());
        row.setDefaultContainerType(r.getDefaultContainerType());
        row.setDefaultAdditive(r.getDefaultAdditive());
        row.setDefaultVolumeMl(r.getDefaultVolumeMl());
        if (r.getFastingRequired() != null) row.setFastingRequired(r.getFastingRequired());
        row.setStabilityMinutes(r.getStabilityMinutes());
        row.setDefaultMethod(r.getDefaultMethod());
        row.setDefaultUnit(r.getDefaultUnit());
        if (r.getValueType() != null) row.setValueType(r.getValueType());
        if (r.getRequiresAuthorisation() != null) row.setRequiresAuthorisation(r.getRequiresAuthorisation());
        row.setTatMinutes(r.getTatMinutes());
        if (r.getIsPanel() != null) row.setIsPanel(r.getIsPanel());
        row.setParentPanelCode(r.getParentPanelCode());
        row.setPrice(r.getPrice());
        row.setGstRate(r.getGstRate());
        row.setDisplayOrder(r.getDisplayOrder());
        row.setHospitalServiceId(r.getHospitalServiceId());
        if (r.getActive() != null) row.setActive(r.getActive());
    }

    private LabTestCatalog clone(LabTestCatalog r) {
        return LabTestCatalog.builder()
                .id(r.getId())
                .hospitalId(r.getHospitalId())
                .testCode(r.getTestCode())
                .loincCode(r.getLoincCode())
                .name(r.getName())
                .aliases(r.getAliases())
                .category(r.getCategory())
                .discipline(r.getDiscipline())
                .specimenKind(r.getSpecimenKind())
                .defaultContainerType(r.getDefaultContainerType())
                .defaultAdditive(r.getDefaultAdditive())
                .defaultVolumeMl(r.getDefaultVolumeMl())
                .fastingRequired(r.getFastingRequired())
                .stabilityMinutes(r.getStabilityMinutes())
                .defaultMethod(r.getDefaultMethod())
                .defaultUnit(r.getDefaultUnit())
                .valueType(r.getValueType())
                .requiresAuthorisation(r.getRequiresAuthorisation())
                .tatMinutes(r.getTatMinutes())
                .isPanel(r.getIsPanel())
                .parentPanelCode(r.getParentPanelCode())
                .price(r.getPrice())
                .gstRate(r.getGstRate())
                .displayOrder(r.getDisplayOrder())
                .hospitalServiceId(r.getHospitalServiceId())
                .active(r.getActive())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    public LabTestCatalogDTO toDTO(LabTestCatalog r) {
        return toDTOWithRangeCount(r, null);
    }

    private LabTestCatalogDTO toDTOWithRangeCount(LabTestCatalog r, Long rangeCount) {
        return LabTestCatalogDTO.builder()
                .id(r.getId())
                .hospitalId(r.getHospitalId())
                .testCode(r.getTestCode())
                .loincCode(r.getLoincCode())
                .name(r.getName())
                .aliases(r.getAliases())
                .category(r.getCategory())
                .discipline(r.getDiscipline())
                .specimenKind(r.getSpecimenKind())
                .defaultContainerType(r.getDefaultContainerType())
                .defaultAdditive(r.getDefaultAdditive())
                .defaultVolumeMl(r.getDefaultVolumeMl())
                .fastingRequired(r.getFastingRequired())
                .stabilityMinutes(r.getStabilityMinutes())
                .defaultMethod(r.getDefaultMethod())
                .defaultUnit(r.getDefaultUnit())
                .valueType(r.getValueType())
                .requiresAuthorisation(r.getRequiresAuthorisation())
                .tatMinutes(r.getTatMinutes())
                .isPanel(r.getIsPanel())
                .parentPanelCode(r.getParentPanelCode())
                .price(r.getPrice())
                .gstRate(r.getGstRate())
                .displayOrder(r.getDisplayOrder())
                .hospitalServiceId(r.getHospitalServiceId())
                .rangeCount(rangeCount)
                .active(r.getActive())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
