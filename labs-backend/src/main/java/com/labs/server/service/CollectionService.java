package com.labs.server.service;

import com.labs.server.context.AuthContext;
import com.labs.server.dto.BulkCollectRequest;
import com.labs.server.dto.BulkCollectResultDTO;
import com.labs.server.dto.CollectedSpecimenRowDTO;
import com.labs.server.dto.CollectionStatsDTO;
import com.labs.server.dto.ContainerPlanItemDTO;
import com.labs.server.dto.LabSpecimenDTO;
import com.labs.server.dto.PatientCollectionPlanDTO;
import com.labs.server.entity.LabOrder;
import com.labs.server.entity.LabPriority;
import com.labs.server.entity.LabSpecimen;
import com.labs.server.entity.LabStatus;
import com.labs.server.entity.LabService;
import com.labs.server.entity.Patient;
import com.labs.server.repository.LabOrderRepository;
import com.labs.server.repository.LabSpecimenRepository;
import com.labs.server.repository.LabServiceRepository;
import com.labs.server.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Phase 6 — front-of-house collection console.
 *
 * Three jobs:
 *   1. buildQueue(hospitalId) — group every PENDING_COLLECTION order by
 *      patient, resolve each order's container type via the catalog (or
 *      sample-type keyword fallback), aggregate into a tube plan. The
 *      phlebotomist sees patients sorted by clinical urgency (STAT first)
 *      and within each card the exact tubes to draw.
 *   2. bulkCollect(req) — ONE patient pickup → atomically marks every
 *      named order PENDING_COLLECTION → AWAITING_REPORT, creates the
 *      requested specimens (one per tube), wires audit log. If anything
 *      fails the whole txn rolls back.
 *   3. getStats(hospitalId) — counter dashboard tiles.
 *
 * Read-only methods stream the entire day's queue in one shot — fine at
 * SMB scale (a 200-bed hospital has &lt;200 pending lab orders at any moment).
 * If we ever need pagination, the buildQueue signature gains a from/to.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectionService {

    private static final List<String> PRIORITY_ORDER =
            List.of(LabPriority.STAT.name(), LabPriority.URGENT.name(), LabPriority.ROUTINE.name());

    // Keyword → container vocab (matches LabSpecimenService.deriveContainerTypeFromSampleType)
    private static final Map<String, String> CONTAINER_KEYWORDS = new LinkedHashMap<>() {{
        put("edta", "EDTA");
        put("cbc", "EDTA");           // CBC implies EDTA
        put("citrate", "CITRATE");
        put("heparin", "HEPARIN");
        put("fluoride", "FLUORIDE");
        put("glucose", "FLUORIDE");
        put("urine", "URINE_CUP");
        put("stool", "STOOL_CUP");
        put("swab", "SWAB");
        put("serum", "PLAIN");
        put("plain", "PLAIN");
        put("clot", "PLAIN");
    }};

    private final LabOrderRepository orderRepository;
    private final LabSpecimenRepository specimenRepository;
    private final LabServiceRepository catalogRepository;
    private final PatientRepository patientRepository;
    private final LabSpecimenService specimenService;
    private final AuditService auditService;
    private final ObjectProvider<AuthContext> authContextProvider;

    // ── Queue construction ─────────────────────────────────────────────

    public List<PatientCollectionPlanDTO> buildQueue(UUID hospitalId) {
        List<LabOrder> pending = orderRepository
                .findByHospitalIdAndStatusOrderByCreatedAtDesc(hospitalId, LabStatus.PENDING_COLLECTION);

        // Patient-id → mutable plan accumulator
        Map<Integer, PatientAccumulator> byPatient = new LinkedHashMap<>();

        for (LabOrder o : pending) {
            if (o.getPatient() == null) continue;
            Integer pid = o.getPatient().getId();
            PatientAccumulator acc = byPatient.computeIfAbsent(pid, k -> new PatientAccumulator(o.getPatient()));
            acc.orders.add(o);
        }

        List<PatientCollectionPlanDTO> out = new ArrayList<>();
        for (PatientAccumulator acc : byPatient.values()) {
            out.add(toDTO(acc, hospitalId));
        }

        // Sort: STAT first, then URGENT, then ROUTINE; within priority oldest-first
        out.sort(Comparator
                .comparingInt((PatientCollectionPlanDTO p) -> {
                    int idx = PRIORITY_ORDER.indexOf(p.getHighestPriority());
                    return idx < 0 ? PRIORITY_ORDER.size() : idx;
                })
                .thenComparing(PatientCollectionPlanDTO::getEarliestPendingAt,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }

    /**
     * Build the same container plan for a single patient without changing
     * status. Lets the BulkCollectModal preview the tubes before the
     * phlebotomist commits.
     */
    public Optional<PatientCollectionPlanDTO> getPlanForPatient(UUID hospitalId, Integer patientId) {
        return buildQueue(hospitalId).stream()
                .filter(p -> patientId.equals(p.getPatientId()))
                .findFirst();
    }

    // ── Bulk collect ────────────────────────────────────────────────────

    @Transactional
    public BulkCollectResultDTO bulkCollect(BulkCollectRequest req) {
        if (req == null || req.getOrderIds() == null || req.getOrderIds().isEmpty()) {
            throw new RuntimeException("orderIds is required");
        }
        if (req.getPatientId() == null) {
            throw new RuntimeException("patientId is required");
        }
        if (req.getHospitalId() == null) {
            throw new RuntimeException("hospitalId is required");
        }
        if (req.getTubes() == null || req.getTubes().isEmpty()) {
            throw new RuntimeException("tubes is required — call /api/collection/queue first to get the plan");
        }

        String collectedByName = resolveDisplayName(req.getCollectedByName());
        UUID collectedByUserId = req.getCollectedByUserId() != null
                ? req.getCollectedByUserId() : resolveUserId();
        LocalDateTime collectedAt = req.getCollectedAt() != null ? req.getCollectedAt() : LocalDateTime.now();

        // ── 1) Validate every order belongs to (hospital, patient) + still PENDING_COLLECTION
        List<LabOrder> orders = orderRepository.findAllById(req.getOrderIds());
        if (orders.size() != req.getOrderIds().size()) {
            throw new RuntimeException("One or more orderIds not found");
        }
        for (LabOrder o : orders) {
            if (!req.getHospitalId().equals(o.getHospital().getId())) {
                throw new RuntimeException("Order " + o.getId() + " does not belong to this hospital");
            }
            if (o.getPatient() == null || !req.getPatientId().equals(o.getPatient().getId())) {
                throw new RuntimeException("Order " + o.getId() + " does not belong to patient " + req.getPatientId());
            }
            if (o.getStatus() != LabStatus.PENDING_COLLECTION) {
                throw new RuntimeException("Order " + o.getId() + " is not in PENDING_COLLECTION (was " + o.getStatus() + ")");
            }
        }

        // ── 2) Move every order to AWAITING_REPORT + stamp collectedAt + ensure accession
        List<Long> collectedOrderIds = new ArrayList<>();
        for (LabOrder o : orders) {
            LabStatus previous = o.getStatus();
            o.setStatus(LabStatus.AWAITING_REPORT);
            o.setCollectedAt(collectedAt);
            specimenService.ensureOrderHasAccession(o);
            LabOrder saved = orderRepository.save(o);
            auditService.record("LabOrder", saved.getId().toString(), "STATUS_CHANGE",
                    saved.getHospital().getId(),
                    Map.of("status", previous.name()),
                    Map.of("status", LabStatus.AWAITING_REPORT.name(),
                            "collectedAt", collectedAt.toString(),
                            "via", "BULK_COLLECT"));
            collectedOrderIds.add(saved.getId());
        }

        // ── 3) Materialise tubes — one specimen row per Tube, linked to the
        //     first order it serves (the legacy lab_specimen.lab_order_id is
        //     1:N; we deliberately denormalise here by creating one specimen
        //     per (order, tube) pair so EVERY order has at least one
        //     specimen row in its chain of custody. The notes column records
        //     the shared-tube relationship.)
        List<LabSpecimenDTO> createdSpecimens = new ArrayList<>();
        for (BulkCollectRequest.Tube tube : req.getTubes()) {
            List<Long> servesIds = (tube.getServesOrderIds() != null && !tube.getServesOrderIds().isEmpty())
                    ? tube.getServesOrderIds()
                    : collectedOrderIds;

            String sharedNote = (servesIds.size() > 1)
                    ? "Shared tube for orders " + servesIds
                    : null;

            for (Long oid : servesIds) {
                LabOrder o = orders.stream().filter(x -> x.getId().equals(oid)).findFirst().orElse(null);
                if (o == null) continue;

                LabSpecimen specimen = LabSpecimen.builder()
                        .labOrderId(oid)
                        .hospitalId(req.getHospitalId())
                        .containerType(tube.getContainerType())
                        .additive(tube.getAdditive())
                        .volumeMl(tube.getVolumeMl())
                        .barcode(tube.getBarcode() != null && !tube.getBarcode().isBlank()
                                ? tube.getBarcode()
                                : null) // generated by service if null
                        .collectedAt(collectedAt)
                        .collectedByName(collectedByName)
                        .collectedByUserId(collectedByUserId)
                        .collectionSite(req.getCollectionSite())
                        .notes(req.getNotes() != null && !req.getNotes().isBlank()
                                ? req.getNotes() + (sharedNote != null ? " · " + sharedNote : "")
                                : sharedNote)
                        .build();

                // Re-use LabSpecimenService.create for barcode generation + audit
                createdSpecimens.add(
                        specimenService.create(oid,
                                com.labs.server.dto.CreateSpecimenRequest.builder()
                                        .containerType(specimen.getContainerType())
                                        .additive(specimen.getAdditive())
                                        .volumeMl(specimen.getVolumeMl())
                                        .barcode(specimen.getBarcode())
                                        .collectedAt(collectedAt)
                                        .collectedByName(collectedByName)
                                        .collectedByUserId(collectedByUserId)
                                        .collectionSite(req.getCollectionSite())
                                        .notes(specimen.getNotes())
                                        .build())
                );
            }
        }

        return BulkCollectResultDTO.builder()
                .patientId(req.getPatientId())
                .collectedOrderIds(collectedOrderIds)
                .createdSpecimens(createdSpecimens)
                .tubeCount(req.getTubes().size())
                .orderCount(collectedOrderIds.size())
                .build();
    }

    // ── Collected specimens log ────────────────────────────────────────

    /**
     * Date-windowed list of every specimen collected for the hospital. Joins
     * specimen → order → patient in three batched lookups (no N+1) so a
     * 500-row day still renders in one round-trip. Sort: newest collected_at
     * first.
     *
     * Used by the Collections page as a read-only audit log of what the lab
     * has actually received. Distinct from {@link #buildQueue} which lists
     * orders that have NOT been collected yet.
     */
    public List<CollectedSpecimenRowDTO> log(UUID hospitalId, LocalDateTime from, LocalDateTime to) {
        if (hospitalId == null) return List.of();

        List<LabSpecimen> specimens = specimenRepository.findCollectedInRange(hospitalId, from, to);
        if (specimens.isEmpty()) return List.of();

        // Batch-load the parent orders to avoid N+1 lookups when the bench is busy.
        java.util.Set<Long> orderIds = specimens.stream()
                .map(LabSpecimen::getLabOrderId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, LabOrder> ordersById = orderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(LabOrder::getId, o -> o));

        // Batch-load patients via the order list — every order carries a patient FK.
        java.util.Set<Integer> patientIds = ordersById.values().stream()
                .map(o -> o.getPatient() != null ? o.getPatient().getId() : null)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, Patient> patientsById = patientRepository.findAllById(patientIds).stream()
                .collect(Collectors.toMap(Patient::getId, p -> p));

        List<CollectedSpecimenRowDTO> rows = new ArrayList<>(specimens.size());
        for (LabSpecimen s : specimens) {
            LabOrder order = ordersById.get(s.getLabOrderId());
            Patient patient = (order != null && order.getPatient() != null)
                    ? patientsById.get(order.getPatient().getId())
                    : null;
            rows.add(toRow(s, order, patient));
        }
        return rows;
    }

    private CollectedSpecimenRowDTO toRow(LabSpecimen s, LabOrder order, Patient patient) {
        String patientName = patient == null ? null
                : patient.getFirstName()
                + (patient.getLastName() != null ? " " + patient.getLastName() : "");
        return CollectedSpecimenRowDTO.builder()
                .specimenId(s.getId())
                .barcode(s.getBarcode())
                .qrPayload(s.getQrPayload())
                .labOrderId(s.getLabOrderId())
                .serviceName(order != null ? order.getServiceName() : null)
                .accessionNumber(order != null ? order.getAccessionNumber() : null)
                .orderStatus(order != null && order.getStatus() != null
                        ? order.getStatus().name() : null)
                .priority(order != null && order.getPriority() != null
                        ? order.getPriority().name() : null)
                .containerType(s.getContainerType())
                .additive(s.getAdditive())
                .volumeMl(s.getVolumeMl())
                .patientId(patient != null ? patient.getId() : null)
                .patientName(patientName)
                .patientUhid(patient != null ? patient.getUhid() : null)
                .collectedAt(s.getCollectedAt())
                .collectedByUserId(s.getCollectedByUserId())
                .collectedByName(s.getCollectedByName())
                .receivedAt(s.getReceivedAt())
                .accessionedAt(s.getAccessionedAt())
                .rejected(s.getRejected())
                .rejectedAt(s.getRejectedAt())
                .rejectionReasonCode(s.getRejectionReasonCode())
                .rejectionNotes(s.getRejectionNotes())
                .createdAt(s.getCreatedAt())
                .build();
    }

    // ── Stats ──────────────────────────────────────────────────────────

    public CollectionStatsDTO getStats(UUID hospitalId) {
        long pendingOrders = orderRepository
                .countByHospitalIdAndStatus(hospitalId, LabStatus.PENDING_COLLECTION);

        List<LabOrder> pending = orderRepository
                .findByHospitalIdAndStatusOrderByCreatedAtDesc(hospitalId, LabStatus.PENDING_COLLECTION);

        long pendingPatients = pending.stream()
                .map(o -> o.getPatient() != null ? o.getPatient().getId() : null)
                .filter(java.util.Objects::nonNull)
                .distinct().count();

        long pendingStat = pending.stream()
                .filter(o -> o.getPriority() == LabPriority.STAT).count();
        long pendingUrgent = pending.stream()
                .filter(o -> o.getPriority() == LabPriority.URGENT).count();

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        long collectedToday = specimenRepository
                .countByHospitalIdAndCollectedAtBetween(hospitalId, startOfDay, now);
        long rejectedToday = specimenRepository
                .countByHospitalIdAndRejectedTrueAndRejectedAtBetween(hospitalId, startOfDay, now);
        long awaitingReceive = specimenRepository
                .countCollectedNotReceived(hospitalId, startOfDay, now);

        return CollectionStatsDTO.builder()
                .pendingPatients(pendingPatients)
                .pendingOrders(pendingOrders)
                .pendingStat(pendingStat)
                .pendingUrgent(pendingUrgent)
                .collectedToday(collectedToday)
                .rejectedToday(rejectedToday)
                .awaitingReceiveToday(awaitingReceive)
                .build();
    }

    // ── helpers ────────────────────────────────────────────────────────

    private PatientCollectionPlanDTO toDTO(PatientAccumulator acc, UUID hospitalId) {
        // Resolve container per order (catalog → keyword → OTHER)
        Map<String, Tube> byContainer = new TreeMap<>();   // ordered for stable UI
        List<PatientCollectionPlanDTO.OrderRef> orderRefs = new ArrayList<>();

        LocalDateTime earliest = null;
        String highestPriority = LabPriority.ROUTINE.name();

        for (LabOrder o : acc.orders) {
            CatalogResolution res = resolveCatalog(hospitalId, o);
            String container = res.container;
            BigDecimal minVol = res.volumeMl;
            Boolean fasting = res.fastingRequired;

            Tube tube = byContainer.computeIfAbsent(container, c -> new Tube(c));
            tube.servesOrderIds.add(o.getId());
            tube.servesTestNames.add(o.getServiceName());
            if (minVol != null) {
                tube.volumeMl = (tube.volumeMl == null || minVol.compareTo(tube.volumeMl) > 0)
                        ? minVol : tube.volumeMl;
            }
            if (Boolean.TRUE.equals(fasting)) tube.fastingRequired = true;

            orderRefs.add(PatientCollectionPlanDTO.OrderRef.builder()
                    .id(o.getId())
                    .accessionNumber(o.getAccessionNumber())
                    .serviceName(o.getServiceName())
                    .priority(o.getPriority() != null ? o.getPriority().name() : null)
                    .sampleType(o.getSampleType())
                    .referredByName(o.getReferredByName())
                    .specializationName(o.getSpecializationName())
                    .price(o.getPrice())
                    .createdAt(o.getCreatedAt())
                    .resolvedContainer(container)
                    .fastingRequired(fasting)
                    .hospitalId(hospitalId)
                    .build());

            if (earliest == null || (o.getCreatedAt() != null && o.getCreatedAt().isBefore(earliest))) {
                earliest = o.getCreatedAt();
            }
            if (o.getPriority() != null) {
                int currentIdx = PRIORITY_ORDER.indexOf(highestPriority);
                int candidateIdx = PRIORITY_ORDER.indexOf(o.getPriority().name());
                if (candidateIdx >= 0 && (currentIdx < 0 || candidateIdx < currentIdx)) {
                    highestPriority = o.getPriority().name();
                }
            }
        }

        // Sort orders inside the patient card by priority then createdAt
        orderRefs.sort(Comparator
                .comparingInt((PatientCollectionPlanDTO.OrderRef r) -> {
                    int idx = PRIORITY_ORDER.indexOf(r.getPriority());
                    return idx < 0 ? PRIORITY_ORDER.size() : idx;
                })
                .thenComparing(PatientCollectionPlanDTO.OrderRef::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        List<ContainerPlanItemDTO> plan = byContainer.values().stream()
                .map(Tube::toDTO)
                .collect(Collectors.toList());

        Patient p = acc.patient;
        return PatientCollectionPlanDTO.builder()
                .patientId(p.getId())
                .patientUhid(p.getUhid())
                .patientName(((p.getFirstName() != null ? p.getFirstName() : "")
                        + (p.getLastName() != null ? " " + p.getLastName() : "")).trim())
                .patientPhone(p.getPhone())
                .patientDob(p.getDob())
                .patientSex(p.getGender())
                .ageYears(ageFromDob(p.getDob()))
                .earliestPendingAt(earliest)
                .highestPriority(highestPriority)
                .orders(orderRefs)
                .containerPlan(plan)
                .build();
    }

    /**
     * Catalog lookup precedence:
     *   1. exact case-insensitive name == order.serviceName
     *   2. exact case-insensitive test_code == order.serviceName
     *   3. case-insensitive substring match of catalog.name in order.serviceName
     *   4. sample-type keyword fallback
     *   5. OTHER
     */
    private CatalogResolution resolveCatalog(UUID hospitalId, LabOrder order) {
        Optional<LabService> cat = catalogRepository.findByHospitalIdAndTestCode(hospitalId,
                safeCode(order.getServiceName()));
        if (cat.isEmpty() && order.getServiceName() != null) {
            // Pull a small candidate set via search and pick the best name match
            var candidates = catalogRepository.searchByHospital(
                    hospitalId, order.getServiceName().trim(),
                    org.springframework.data.domain.PageRequest.of(0, 5));
            cat = candidates.stream()
                    .filter(c -> c.getName() != null
                            && c.getName().equalsIgnoreCase(order.getServiceName().trim()))
                    .findFirst()
                    .or(() -> candidates.stream().findFirst());
        }

        if (cat.isPresent()) {
            LabService c = cat.get();
            String container = c.getDefaultContainerType() != null
                    ? c.getDefaultContainerType()
                    : deriveFromSampleType(order.getSampleType());
            return new CatalogResolution(container, c.getDefaultAdditive(),
                    c.getDefaultVolumeMl(), c.getFastingRequired());
        }
        return new CatalogResolution(deriveFromSampleType(order.getSampleType()),
                null, null, false);
    }

    private String safeCode(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }

    private String deriveFromSampleType(String sampleType) {
        if (sampleType == null) return "OTHER";
        String s = sampleType.toLowerCase();
        for (var e : CONTAINER_KEYWORDS.entrySet()) {
            if (s.contains(e.getKey())) return e.getValue();
        }
        return "OTHER";
    }

    private Integer ageFromDob(LocalDate dob) {
        if (dob == null) return null;
        try { return Period.between(dob, LocalDate.now()).getYears(); }
        catch (Exception e) { return null; }
    }

    private String resolveDisplayName(String passed) {
        if (passed != null && !passed.isBlank()) return passed;
        try {
            AuthContext ctx = authContextProvider.getIfAvailable();
            if (ctx != null && ctx.getEmail() != null) return ctx.getEmail();
        } catch (Exception ignored) {}
        return "Collection Desk";
    }

    private UUID resolveUserId() {
        try {
            AuthContext ctx = authContextProvider.getIfAvailable();
            if (ctx != null && ctx.getUserId() != null) {
                return UUID.fromString(ctx.getUserId());
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ── inner accumulators ─────────────────────────────────────────────

    private static class PatientAccumulator {
        final Patient patient;
        final List<LabOrder> orders = new ArrayList<>();
        PatientAccumulator(Patient p) { this.patient = p; }
    }

    private static class Tube {
        final String containerType;
        BigDecimal volumeMl;
        boolean fastingRequired;
        final List<Long> servesOrderIds = new ArrayList<>();
        final List<String> servesTestNames = new ArrayList<>();
        Tube(String c) { this.containerType = c; }
        ContainerPlanItemDTO toDTO() {
            return ContainerPlanItemDTO.builder()
                    .containerType(containerType)
                    .volumeMl(volumeMl)
                    .fastingRequired(fastingRequired)
                    .servesOrderIds(servesOrderIds)
                    .servesTestNames(servesTestNames)
                    .build();
        }
    }

    private record CatalogResolution(String container, String additive, BigDecimal volumeMl, Boolean fastingRequired) {}
}
