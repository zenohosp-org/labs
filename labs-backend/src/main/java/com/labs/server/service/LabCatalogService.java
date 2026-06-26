package com.labs.server.service;

import com.labs.server.dto.CreateLabServiceRequest;
import com.labs.server.dto.LabServiceDTO;
import com.labs.server.entity.LabReferenceRange;
import com.labs.server.entity.LabService;
import com.labs.server.repository.LabReferenceRangeRepository;
import com.labs.server.repository.LabServiceRepository;
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
public class LabCatalogService {

    private final LabServiceRepository repository;
    private final LabReferenceRangeRepository rangeRepository;
    private final LabServiceSeeder seeder;          // separate bean — proxy applies, REQUIRES_NEW writable tx
    private final AuditService auditService;

    public List<LabServiceDTO> list(UUID hospitalId, boolean activeOnly) {
        if (repository.countByHospitalId(hospitalId) == 0) {
            // Goes through the Spring AOP proxy because seeder is a DIFFERENT
            // bean — so seeder.seedFor's @Transactional(REQUIRES_NEW) actually
            // opens a writable transaction and the inserts flush.
            seeder.seedFor(hospitalId);
        }
        List<LabService> rows = activeOnly
                ? repository.findByHospitalIdAndActiveTrueOrderByCategoryAscDisplayOrderAscNameAsc(hospitalId)
                : repository.findByHospitalIdOrderByCategoryAscDisplayOrderAscNameAsc(hospitalId);

        // Single query → map of labServiceId → range count. Avoids N+1 when the
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
    public List<LabServiceDTO> search(UUID hospitalId, String q, int limit) {
        if (q == null || q.isBlank()) return List.of();
        int cappedLimit = Math.min(Math.max(limit, 1), 50);
        return repository.searchByHospital(hospitalId, q.trim(), PageRequest.of(0, cappedLimit))
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * Phase 3 — list ranges that belong to a test. Tenant-checked against the
     * caller's hospital so a forged id can't expose cross-tenant data.
     */
    public List<LabReferenceRange> rangesFor(UUID hospitalId, Long labServiceId) {
        LabService test = loadForTenant(hospitalId, labServiceId);
        return rangeRepository.findByLabTestIdOrderBySexAscMinAgeYearsAsc(test.getId());
    }

    public Optional<LabService> findByCode(UUID hospitalId, String testCode) {
        if (hospitalId == null || testCode == null) return Optional.empty();
        return repository.findByHospitalIdAndTestCode(hospitalId, testCode);
    }

    /** Child analytes for a panel (CBC → 12 analytes). Empty when not a panel. */
    public List<LabServiceDTO> expandPanel(UUID hospitalId, String panelCode) {
        return repository.findByHospitalIdAndParentPanelCodeOrderByDisplayOrderAsc(hospitalId, panelCode)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public LabServiceDTO upsert(UUID hospitalId, CreateLabServiceRequest req) {
        if (req.getTestCode() == null || req.getTestCode().isBlank()) {
            throw new RuntimeException("testCode is required");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new RuntimeException("name is required");
        }

        Optional<LabService> existing = repository.findByHospitalIdAndTestCode(hospitalId, req.getTestCode());
        LabService row = existing.orElseGet(() ->
                LabService.builder().hospitalId(hospitalId).testCode(req.getTestCode()).build());

        LabService before = existing.map(this::clone).orElse(null);
        copyInto(row, req);
        LabService saved = repository.save(row);

        auditService.record("LabService", saved.getId().toString(),
                existing.isPresent() ? "UPDATE" : "CREATE",
                hospitalId, before, saved);
        return toDTO(saved);
    }

    @Transactional
    public LabServiceDTO toggle(UUID hospitalId, Long id) {
        LabService row = loadForTenant(hospitalId, id);
        LabService before = clone(row);
        row.setActive(!Boolean.TRUE.equals(row.getActive()));
        LabService saved = repository.save(row);
        auditService.record("LabService", saved.getId().toString(), "TOGGLE",
                hospitalId, before, saved);
        return toDTO(saved);
    }

    @Transactional
    public void delete(UUID hospitalId, Long id) {
        LabService row = loadForTenant(hospitalId, id);
        repository.delete(row);
        auditService.record("LabService", String.valueOf(id), "DELETE",
                hospitalId, row, null);
    }

    private LabService loadForTenant(UUID hospitalId, Long id) {
        LabService row = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Test catalog row not found: " + id));
        if (!hospitalId.equals(row.getHospitalId())) {
            throw new RuntimeException("Test catalog row does not belong to this hospital");
        }
        return row;
    }

    private void copyInto(LabService row, CreateLabServiceRequest r) {
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

    private LabService clone(LabService r) {
        return LabService.builder()
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

    public LabServiceDTO toDTO(LabService r) {
        return toDTOWithRangeCount(r, null);
    }

    private LabServiceDTO toDTOWithRangeCount(LabService r, Long rangeCount) {
        return LabServiceDTO.builder()
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
