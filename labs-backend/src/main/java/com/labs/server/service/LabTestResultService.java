package com.labs.server.service;

import com.labs.server.context.AuthContext;
import com.labs.server.dto.*;
import com.labs.server.entity.LabOrder;
import com.labs.server.entity.LabService;
import com.labs.server.entity.LabTestResult;
import com.labs.server.entity.ResultStatus;
import com.labs.server.repository.LabOrderRepository;
import com.labs.server.repository.LabTestResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Per-analyte result lifecycle.
 *
 *   create  → PRELIMINARY      (tech entered a value, flagged but not signed)
 *   verify  → FINAL            (tech sign-off — value is releasable)
 *   authorise (orthogonal)     (pathologist sign-off; needed when
 *                               LabService.requiresAuthorisation = true)
 *   amend   → new CORRECTED row pointing at original (original survives)
 *   cancel  → CANCELLED        (sample rejected after entry, etc.)
 *
 * On every create:
 *   1) Resolve metadata from {@link LabService} when a row exists for
 *      (hospitalId, testCode) — unit, method, loinc, analyte name.
 *   2) Match a reference range via {@link LabReferenceRangeService} using
 *      the patient's sex/age and the optional specialState. Snapshot
 *      low/high/critical into the row so changing the catalogue later
 *      doesn't retroactively re-flag history.
 *   3) Derive abnormal_flag (HL7 OBX-8) and panic_flag from the snapshot.
 *   4) Look up the patient's most recent FINAL result for the same test_code
 *      and compute delta_from_previous.
 *   5) Audit CREATE; auditing on every state transition is mandatory for
 *      NABL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LabTestResultService {

    private final LabTestResultRepository repository;
    private final LabOrderRepository orderRepository;
    private final LabCatalogService catalogService;
    private final LabReferenceRangeService referenceRangeService;
    private final AuditService auditService;
    private final ObjectProvider<AuthContext> authContextProvider;

    // ── reads ───────────────────────────────────────────────────────────

    public List<LabTestResultDTO> listForOrder(Long labOrderId) {
        return repository.findByLabOrderIdOrderByCreatedAtAsc(labOrderId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public LabTestResultDTO get(Long id) {
        return toDTO(load(id));
    }

    // ── writes ──────────────────────────────────────────────────────────

    @Transactional
    public List<LabTestResultDTO> createBulk(Long labOrderId, BulkResultRequest req) {
        if (req == null || req.getResults() == null || req.getResults().isEmpty()) {
            return List.of();
        }
        return req.getResults().stream()
                .map(r -> create(labOrderId, r))
                .collect(Collectors.toList());
    }

    @Transactional
    public LabTestResultDTO create(Long labOrderId, CreateTestResultRequest req) {
        if (req == null || req.getTestCode() == null || req.getTestCode().isBlank()) {
            throw new RuntimeException("testCode is required");
        }
        if (req.getValueNumeric() == null && (req.getValueText() == null || req.getValueText().isBlank())) {
            throw new RuntimeException("either valueNumeric or valueText must be supplied");
        }

        LabOrder order = orderRepository.findById(labOrderId)
                .orElseThrow(() -> new RuntimeException("Lab order not found: " + labOrderId));
        UUID hospitalId = order.getHospital().getId();

        Optional<LabService> catalog = catalogService.findByCode(hospitalId, req.getTestCode());
        String analyteName = pickAnalyteName(req, catalog);
        String unit        = pickUnit(req, catalog);
        String method      = pickMethod(req, catalog);
        String loinc       = catalog.map(LabService::getLoincCode).orElse(null);

        // Reference range match — snapshot into the result so the flag is
        // stable even if the catalogue is edited later.
        ResultFlags flags = computeFlags(order, req.getTestCode(), req.getValueNumeric());

        // Delta vs prior FINAL result for the same patient + test_code.
        BigDecimal delta = computeDelta(order, req.getTestCode(), req.getValueNumeric());

        String enteredBy = resolveDisplayName(req.getEnteredByName());
        UUID enteredByUid = resolveUserId(req.getEnteredByUserId());

        LabTestResult result = LabTestResult.builder()
                .labOrderId(labOrderId)
                .specimenId(req.getSpecimenId())
                .hospitalId(hospitalId)
                .testCode(req.getTestCode())
                .analyteName(analyteName)
                .loincCode(loinc)
                .valueNumeric(req.getValueNumeric())
                .valueText(req.getValueText())
                .unit(unit)
                .method(method)
                .instrumentId(req.getInstrumentId())
                .reagentLot(req.getReagentLot())
                .referenceLow(flags.refLow)
                .referenceHigh(flags.refHigh)
                .referenceText(flags.refText)
                .abnormalFlag(flags.abnormalFlag)
                .panicFlag(flags.panic)
                .deltaFromPrevious(delta)
                .resultStatus(ResultStatus.PRELIMINARY)
                .enteredByUserId(enteredByUid)
                .enteredByName(enteredBy)
                .enteredAt(LocalDateTime.now())
                .comments(req.getComments())
                .build();

        LabTestResult saved = repository.save(result);
        auditService.record("LabTestResult", saved.getId().toString(), "CREATE",
                hospitalId, null, saved);
        return toDTO(saved);
    }

    @Transactional
    public LabTestResultDTO verify(Long resultId, VerifyResultRequest req) {
        LabTestResult r = load(resultId);
        if (r.getResultStatus() != ResultStatus.PRELIMINARY) {
            throw new RuntimeException("Only PRELIMINARY results can be verified (current: " + r.getResultStatus() + ")");
        }
        LabTestResult before = cloneShallow(r);

        r.setResultStatus(ResultStatus.FINAL);
        r.setVerifiedByUserId(resolveUserId(req != null ? req.getVerifiedByUserId() : null));
        r.setVerifiedByName(resolveDisplayName(req != null ? req.getVerifiedByName() : null));
        r.setVerifiedAt(LocalDateTime.now());
        if (req != null && req.getComments() != null && !req.getComments().isBlank()) {
            r.setComments(appendComment(r.getComments(), "[VERIFY] " + req.getComments()));
        }

        LabTestResult saved = repository.save(r);
        auditService.record("LabTestResult", saved.getId().toString(), "VERIFY",
                saved.getHospitalId(), before, saved);
        return toDTO(saved);
    }

    @Transactional
    public LabTestResultDTO authorise(Long resultId, AuthoriseResultRequest req) {
        LabTestResult r = load(resultId);
        if (r.getResultStatus() != ResultStatus.FINAL && r.getResultStatus() != ResultStatus.CORRECTED) {
            throw new RuntimeException("Only FINAL/CORRECTED results can be authorised (current: " + r.getResultStatus() + ")");
        }
        if (r.getAuthorisedAt() != null) {
            throw new RuntimeException("Result is already authorised");
        }
        LabTestResult before = cloneShallow(r);

        r.setAuthorisedByUserId(resolveUserId(req != null ? req.getAuthorisedByUserId() : null));
        r.setAuthorisedByName(resolveDisplayName(req != null ? req.getAuthorisedByName() : null));
        r.setAuthorisedAt(LocalDateTime.now());
        if (req != null && req.getComments() != null && !req.getComments().isBlank()) {
            r.setComments(appendComment(r.getComments(), "[AUTHORISE] " + req.getComments()));
        }

        LabTestResult saved = repository.save(r);
        auditService.record("LabTestResult", saved.getId().toString(), "AUTHORISE",
                saved.getHospitalId(), before, saved);
        return toDTO(saved);
    }

    /**
     * Insert a NEW row that supersedes a FINAL one. Original keeps its status —
     * NABL requires every prior value to remain visible.
     */
    @Transactional
    public LabTestResultDTO amend(Long resultId, AmendResultRequest req) {
        if (req == null || req.getReasonCode() == null || req.getReasonCode().isBlank()) {
            throw new RuntimeException("amendment reasonCode is required");
        }
        if (req.getValueNumeric() == null && (req.getValueText() == null || req.getValueText().isBlank())) {
            throw new RuntimeException("amendment must supply a new valueNumeric or valueText");
        }

        LabTestResult original = load(resultId);
        if (original.getResultStatus() != ResultStatus.FINAL
                && original.getResultStatus() != ResultStatus.CORRECTED) {
            throw new RuntimeException("Only FINAL / CORRECTED results can be amended");
        }

        LabOrder order = orderRepository.findById(original.getLabOrderId())
                .orElseThrow(() -> new RuntimeException("Originating lab order missing"));

        ResultFlags flags = computeFlags(order, original.getTestCode(), req.getValueNumeric());
        BigDecimal delta = computeDelta(order, original.getTestCode(), req.getValueNumeric());

        String enteredBy = resolveDisplayName(req.getAmendedByName());
        UUID enteredByUid = resolveUserId(req.getAmendedByUserId());

        LabTestResult amendment = LabTestResult.builder()
                .labOrderId(original.getLabOrderId())
                .specimenId(original.getSpecimenId())
                .hospitalId(original.getHospitalId())
                .testCode(original.getTestCode())
                .analyteName(original.getAnalyteName())
                .loincCode(original.getLoincCode())
                .valueNumeric(req.getValueNumeric())
                .valueText(req.getValueText())
                .unit(req.getUnit() != null ? req.getUnit() : original.getUnit())
                .method(req.getMethod() != null ? req.getMethod() : original.getMethod())
                .instrumentId(original.getInstrumentId())
                .reagentLot(original.getReagentLot())
                .referenceLow(flags.refLow)
                .referenceHigh(flags.refHigh)
                .referenceText(flags.refText)
                .abnormalFlag(flags.abnormalFlag)
                .panicFlag(flags.panic)
                .deltaFromPrevious(delta)
                .resultStatus(ResultStatus.CORRECTED)
                .enteredByUserId(enteredByUid)
                .enteredByName(enteredBy)
                .enteredAt(LocalDateTime.now())
                .verifiedByUserId(enteredByUid)
                .verifiedByName(enteredBy)
                .verifiedAt(LocalDateTime.now())
                .amendmentOfId(original.getId())
                .amendmentReasonCode(req.getReasonCode())
                .amendmentReasonNotes(req.getReasonNotes())
                .comments(req.getComments())
                .build();

        LabTestResult saved = repository.save(amendment);
        auditService.record("LabTestResult", saved.getId().toString(), "AMEND",
                saved.getHospitalId(), original, saved,
                req.getReasonCode(), req.getReasonNotes());
        return toDTO(saved);
    }

    @Transactional
    public LabTestResultDTO cancel(Long resultId, String reason) {
        LabTestResult r = load(resultId);
        if (r.getResultStatus() == ResultStatus.CANCELLED) {
            throw new RuntimeException("Result already cancelled");
        }
        LabTestResult before = cloneShallow(r);
        r.setResultStatus(ResultStatus.CANCELLED);
        if (reason != null && !reason.isBlank()) {
            r.setComments(appendComment(r.getComments(), "[CANCEL] " + reason));
        }
        LabTestResult saved = repository.save(r);
        auditService.record("LabTestResult", saved.getId().toString(), "CANCEL",
                saved.getHospitalId(), before, saved, "CANCELLED", reason);
        return toDTO(saved);
    }

    @Transactional
    public LabTestResultDTO recordPanicCall(Long resultId, PanicCallRequest req) {
        LabTestResult r = load(resultId);
        if (!Boolean.TRUE.equals(r.getPanicFlag())) {
            throw new RuntimeException("Result is not flagged as panic; no call required");
        }
        LabTestResult before = cloneShallow(r);
        r.setPanicCalledAt(LocalDateTime.now());
        r.setPanicCalledTo(req != null ? req.getCalledTo() : null);
        if (req != null && req.getAcknowledgedBy() != null && !req.getAcknowledgedBy().isBlank()) {
            r.setPanicAcknowledgedBy(req.getAcknowledgedBy());
            r.setPanicAcknowledgedAt(LocalDateTime.now());
        }
        LabTestResult saved = repository.save(r);
        auditService.record("LabTestResult", saved.getId().toString(), "PANIC_CALL",
                saved.getHospitalId(), before, saved);
        return toDTO(saved);
    }

    // ── helpers ─────────────────────────────────────────────────────────

    /** Snapshot of reference info needed at result creation. */
    private record ResultFlags(BigDecimal refLow, BigDecimal refHigh, String refText,
                               String abnormalFlag, boolean panic) {}

    private ResultFlags computeFlags(LabOrder order, String testCode, BigDecimal value) {
        if (value == null) return new ResultFlags(null, null, null, null, false);

        int ageYears = patientAgeYears(order);
        String sex = patientSex(order);

        // First try the explicit test_code match (most precise — uses LOINC linkage when set).
        Optional<RangeMatchDTO> match = referenceRangeService.match(
                order.getHospital().getId(), testCode, sex, ageYears, value, null);

        if (match.isEmpty()) {
            // Fallback to analyte display name (legacy reference_ranges keyed on free-text).
            Optional<LabService> catalog = catalogService.findByCode(order.getHospital().getId(), testCode);
            String displayName = catalog.map(LabService::getName).orElse(testCode);
            match = referenceRangeService.match(order.getHospital().getId(), displayName, sex, ageYears, value, null);
        }

        if (match.isEmpty()) return new ResultFlags(null, null, null, null, false);

        RangeMatchDTO m = match.get();
        String hl7Flag = m.getAbnormalFlag();
        boolean panic  = Boolean.TRUE.equals(m.getPanic());
        return new ResultFlags(m.getMinValue(), m.getMaxValue(), m.getRangeText(), hl7Flag, panic);
    }

    private BigDecimal computeDelta(LabOrder order, String testCode, BigDecimal value) {
        if (value == null || order.getPatient() == null) return null;
        List<LabTestResult> prior = repository.findPriorFinalForDelta(
                order.getHospital().getId(),
                order.getPatient().getId(),
                testCode,
                order.getId());
        if (prior.isEmpty()) return null;
        LabTestResult last = prior.get(0);
        if (last.getValueNumeric() == null) return null;
        return value.subtract(last.getValueNumeric());
    }

    private int patientAgeYears(LabOrder order) {
        try {
            if (order.getPatient() != null && order.getPatient().getDob() != null) {
                return Period.between(order.getPatient().getDob(), java.time.LocalDate.now()).getYears();
            }
        } catch (Exception ignored) { }
        return 30; // safe default for catalogues that have an adult-only band
    }

    private String patientSex(LabOrder order) {
        if (order.getPatient() == null || order.getPatient().getGender() == null) return "ANY";
        String g = order.getPatient().getGender().toUpperCase();
        if (g.startsWith("M")) return "MALE";
        if (g.startsWith("F")) return "FEMALE";
        return "ANY";
    }

    private String pickAnalyteName(CreateTestResultRequest req, Optional<LabService> catalog) {
        if (req.getAnalyteName() != null && !req.getAnalyteName().isBlank()) return req.getAnalyteName();
        return catalog.map(LabService::getName).orElse(req.getTestCode());
    }

    private String pickUnit(CreateTestResultRequest req, Optional<LabService> catalog) {
        if (req.getUnit() != null && !req.getUnit().isBlank()) return req.getUnit();
        return catalog.map(LabService::getDefaultUnit).orElse(null);
    }

    private String pickMethod(CreateTestResultRequest req, Optional<LabService> catalog) {
        if (req.getMethod() != null && !req.getMethod().isBlank()) return req.getMethod();
        return catalog.map(LabService::getDefaultMethod).orElse(null);
    }

    private String resolveDisplayName(String passed) {
        if (passed != null && !passed.isBlank()) return passed;
        try {
            AuthContext ctx = authContextProvider.getIfAvailable();
            if (ctx != null && ctx.getEmail() != null) return ctx.getEmail();
        } catch (Exception ignored) { }
        return "System";
    }

    private UUID resolveUserId(UUID passed) {
        if (passed != null) return passed;
        try {
            AuthContext ctx = authContextProvider.getIfAvailable();
            if (ctx != null && ctx.getUserId() != null) {
                return UUID.fromString(ctx.getUserId());
            }
        } catch (Exception ignored) { }
        return null;
    }

    private String appendComment(String existing, String addition) {
        if (existing == null || existing.isBlank()) return addition;
        return existing + "\n" + addition;
    }

    private LabTestResult load(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Result not found: " + id));
    }

    /** Snapshot used as the "before" image for audit. Excludes nothing — diff is computed downstream. */
    private LabTestResult cloneShallow(LabTestResult r) {
        return LabTestResult.builder()
                .id(r.getId())
                .labOrderId(r.getLabOrderId())
                .specimenId(r.getSpecimenId())
                .hospitalId(r.getHospitalId())
                .testCode(r.getTestCode())
                .analyteName(r.getAnalyteName())
                .loincCode(r.getLoincCode())
                .valueNumeric(r.getValueNumeric())
                .valueText(r.getValueText())
                .unit(r.getUnit())
                .method(r.getMethod())
                .instrumentId(r.getInstrumentId())
                .reagentLot(r.getReagentLot())
                .referenceLow(r.getReferenceLow())
                .referenceHigh(r.getReferenceHigh())
                .referenceText(r.getReferenceText())
                .abnormalFlag(r.getAbnormalFlag())
                .panicFlag(r.getPanicFlag())
                .deltaFromPrevious(r.getDeltaFromPrevious())
                .deltaCheckFlag(r.getDeltaCheckFlag())
                .resultStatus(r.getResultStatus())
                .enteredByUserId(r.getEnteredByUserId())
                .enteredByName(r.getEnteredByName())
                .enteredAt(r.getEnteredAt())
                .verifiedByUserId(r.getVerifiedByUserId())
                .verifiedByName(r.getVerifiedByName())
                .verifiedAt(r.getVerifiedAt())
                .authorisedByUserId(r.getAuthorisedByUserId())
                .authorisedByName(r.getAuthorisedByName())
                .authorisedAt(r.getAuthorisedAt())
                .amendmentOfId(r.getAmendmentOfId())
                .amendmentReasonCode(r.getAmendmentReasonCode())
                .amendmentReasonNotes(r.getAmendmentReasonNotes())
                .panicCalledAt(r.getPanicCalledAt())
                .panicCalledTo(r.getPanicCalledTo())
                .panicAcknowledgedBy(r.getPanicAcknowledgedBy())
                .panicAcknowledgedAt(r.getPanicAcknowledgedAt())
                .comments(r.getComments())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    private LabTestResultDTO toDTO(LabTestResult r) {
        return LabTestResultDTO.builder()
                .id(r.getId())
                .labOrderId(r.getLabOrderId())
                .specimenId(r.getSpecimenId())
                .hospitalId(r.getHospitalId())
                .testCode(r.getTestCode())
                .analyteName(r.getAnalyteName())
                .loincCode(r.getLoincCode())
                .valueNumeric(r.getValueNumeric())
                .valueText(r.getValueText())
                .unit(r.getUnit())
                .method(r.getMethod())
                .instrumentId(r.getInstrumentId())
                .reagentLot(r.getReagentLot())
                .referenceLow(r.getReferenceLow())
                .referenceHigh(r.getReferenceHigh())
                .referenceText(r.getReferenceText())
                .abnormalFlag(r.getAbnormalFlag())
                .panicFlag(r.getPanicFlag())
                .deltaFromPrevious(r.getDeltaFromPrevious())
                .deltaCheckFlag(r.getDeltaCheckFlag())
                .resultStatus(r.getResultStatus() != null ? r.getResultStatus().name() : null)
                .enteredByUserId(r.getEnteredByUserId())
                .enteredByName(r.getEnteredByName())
                .enteredAt(r.getEnteredAt())
                .verifiedByUserId(r.getVerifiedByUserId())
                .verifiedByName(r.getVerifiedByName())
                .verifiedAt(r.getVerifiedAt())
                .authorisedByUserId(r.getAuthorisedByUserId())
                .authorisedByName(r.getAuthorisedByName())
                .authorisedAt(r.getAuthorisedAt())
                .amendmentOfId(r.getAmendmentOfId())
                .amendmentReasonCode(r.getAmendmentReasonCode())
                .amendmentReasonNotes(r.getAmendmentReasonNotes())
                .panicCalledAt(r.getPanicCalledAt())
                .panicCalledTo(r.getPanicCalledTo())
                .panicAcknowledgedBy(r.getPanicAcknowledgedBy())
                .panicAcknowledgedAt(r.getPanicAcknowledgedAt())
                .comments(r.getComments())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
