package com.labs.server.service;

import com.labs.server.dto.LabReferenceRangeRequest;
import com.labs.server.dto.RangeMatchDTO;
import com.labs.server.entity.LabReferenceRange;
import com.labs.server.repository.LabReferenceRangeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LabReferenceRangeService {

    private final LabReferenceRangeRepository repository;
    private final LabReferenceRangeSeed seedDefaults;

    /**
     * Returns the catalogue for a hospital. Lazy-seeds the defaults the first
     * time a hospital reads the list — keeps boot order simple (no global
     * CommandLineRunner that needs to enumerate hospitals).
     */
    public List<LabReferenceRange> list(UUID hospitalId) {
        if (repository.countByHospitalId(hospitalId) == 0) {
            seedFor(hospitalId);
        }
        return repository.findByHospitalIdOrderByTestNameAscMinAgeYearsAsc(hospitalId);
    }

    @Transactional
    public LabReferenceRange create(UUID hospitalId, LabReferenceRangeRequest req) {
        LabReferenceRange row = LabReferenceRange.builder()
                .hospitalId(hospitalId)
                .testName(req.getTestName())
                .category(req.getCategory())
                .sex(req.getSex() != null ? req.getSex() : "ANY")
                .minAgeYears(req.getMinAgeYears())
                .maxAgeYears(req.getMaxAgeYears())
                .minValue(req.getMinValue())
                .maxValue(req.getMaxValue())
                .unit(req.getUnit())
                .rangeText(req.getRangeText())
                .isActive(req.getIsActive() != null ? req.getIsActive() : Boolean.TRUE)
                .build();
        return repository.save(row);
    }

    @Transactional
    public LabReferenceRange update(UUID hospitalId, UUID id, LabReferenceRangeRequest req) {
        LabReferenceRange row = loadForTenant(hospitalId, id);
        row.setTestName(req.getTestName());
        row.setCategory(req.getCategory());
        row.setSex(req.getSex() != null ? req.getSex() : "ANY");
        row.setMinAgeYears(req.getMinAgeYears());
        row.setMaxAgeYears(req.getMaxAgeYears());
        row.setMinValue(req.getMinValue());
        row.setMaxValue(req.getMaxValue());
        row.setUnit(req.getUnit());
        row.setRangeText(req.getRangeText());
        if (req.getIsActive() != null) row.setIsActive(req.getIsActive());
        return repository.save(row);
    }

    @Transactional
    public void delete(UUID hospitalId, UUID id) {
        repository.delete(loadForTenant(hospitalId, id));
    }

    @Transactional
    public LabReferenceRange toggle(UUID hospitalId, UUID id) {
        LabReferenceRange row = loadForTenant(hospitalId, id);
        row.setIsActive(!Boolean.TRUE.equals(row.getIsActive()));
        return repository.save(row);
    }

    /**
     * Backward-compat shim — keeps the pre-Phase-2 signature (no specialState).
     * Equivalent to {@link #match(UUID, String, String, int, BigDecimal, String)}
     * with {@code specialState=null} (= baseline band).
     */
    public Optional<RangeMatchDTO> match(UUID hospitalId, String testName, String sex, int ageYears,
                                         BigDecimal value) {
        return match(hospitalId, testName, sex, ageYears, value, null);
    }

    /**
     * Match a measured value to the right band and produce both the legacy
     * LOW/NORMAL/HIGH {@code flag} and the HL7 OBX-8 {@code abnormalFlag}
     * (N/L/H/LL/HH). When the catalogue has overlapping bands, the most
     * specific one wins:
     *   1. specialState match beats baseline (null specialState),
     *   2. sex match beats sex=ANY,
     *   3. narrower age window beats wider.
     *
     * {@code panic=true} when the value crosses {@code criticalLow} or
     * {@code criticalHigh}; the caller's downstream panic-call workflow
     * keys off this.
     */
    public Optional<RangeMatchDTO> match(UUID hospitalId, String testName, String sex, int ageYears,
                                         BigDecimal value, String specialState) {
        if (testName == null || testName.isBlank()) return Optional.empty();
        String normalisedSex = sex != null ? sex.toUpperCase() : "ANY";
        String normalisedState = specialState != null && !specialState.isBlank()
                ? specialState.toUpperCase() : null;

        List<LabReferenceRange> candidates = repository.findCandidates(
                hospitalId, testName.trim(), normalisedSex, ageYears, normalisedState);
        if (candidates.isEmpty()) return Optional.empty();

        // Specificity ranking: specialState match (0) < baseline null (1);
        // sex specific (0) < ANY (1); narrower age window wins last.
        LabReferenceRange best = candidates.stream()
                .min(Comparator
                        .comparingInt((LabReferenceRange r) ->
                                (normalisedState != null && normalisedState.equalsIgnoreCase(r.getSpecialState())) ? 0 : 1)
                        .thenComparingInt(r -> "ANY".equalsIgnoreCase(r.getSex()) ? 1 : 0)
                        .thenComparingInt(LabReferenceRangeService::ageWindowWidth))
                .orElseThrow();

        String legacyFlag = null;
        String hl7Flag = null;
        boolean panic = false;

        if (value != null) {
            if (best.getCriticalLow() != null && value.compareTo(best.getCriticalLow()) < 0) {
                hl7Flag = "LL"; legacyFlag = "LOW"; panic = true;
            } else if (best.getCriticalHigh() != null && value.compareTo(best.getCriticalHigh()) > 0) {
                hl7Flag = "HH"; legacyFlag = "HIGH"; panic = true;
            } else if (best.getMinValue() != null && best.getMaxValue() != null) {
                if (value.compareTo(best.getMinValue()) < 0) { hl7Flag = "L"; legacyFlag = "LOW"; }
                else if (value.compareTo(best.getMaxValue()) > 0) { hl7Flag = "H"; legacyFlag = "HIGH"; }
                else { hl7Flag = "N"; legacyFlag = "NORMAL"; }
            }
        }

        return Optional.of(RangeMatchDTO.builder()
                .rangeId(best.getId())
                .testName(best.getTestName())
                .sex(best.getSex())
                .minAgeYears(best.getMinAgeYears())
                .maxAgeYears(best.getMaxAgeYears())
                .minValue(best.getMinValue())
                .maxValue(best.getMaxValue())
                .unit(best.getUnit())
                .rangeText(best.getRangeText())
                .flag(legacyFlag)
                .abnormalFlag(hl7Flag)
                .panic(panic)
                .criticalLow(best.getCriticalLow())
                .criticalHigh(best.getCriticalHigh())
                .specialState(best.getSpecialState())
                .loincCode(best.getLoincCode())
                .method(best.getMethod())
                .build());
    }

    private static int ageWindowWidth(LabReferenceRange r) {
        int min = r.getMinAgeYears() != null ? r.getMinAgeYears() : 0;
        int max = r.getMaxAgeYears() != null ? r.getMaxAgeYears() : 200;
        return max - min;
    }

    private LabReferenceRange loadForTenant(UUID hospitalId, UUID id) {
        LabReferenceRange row = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reference range not found"));
        if (!hospitalId.equals(row.getHospitalId())) {
            throw new RuntimeException("Reference range does not belong to this hospital");
        }
        return row;
    }

    @Transactional
    void seedFor(UUID hospitalId) {
        log.info("Seeding {} default lab reference ranges for hospital {}",
                seedDefaults.defaults().size(), hospitalId);
        for (LabReferenceRangeSeed.Default d : seedDefaults.defaults()) {
            repository.save(LabReferenceRange.builder()
                    .hospitalId(hospitalId)
                    .testName(d.testName())
                    .category(d.category())
                    .sex(d.sex())
                    .minAgeYears(d.minAge())
                    .maxAgeYears(d.maxAge())
                    .minValue(d.minValue())
                    .maxValue(d.maxValue())
                    .unit(d.unit())
                    .rangeText(d.rangeText())
                    .isActive(true)
                    .build());
        }
    }
}
