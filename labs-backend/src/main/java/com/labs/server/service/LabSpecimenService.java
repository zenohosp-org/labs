package com.labs.server.service;

import com.labs.server.dto.CreateSpecimenRequest;
import com.labs.server.dto.LabSpecimenDTO;
import com.labs.server.dto.ReceiveSpecimenRequest;
import com.labs.server.dto.RejectSpecimenRequest;
import com.labs.server.entity.LabOrder;
import com.labs.server.entity.LabSpecimen;
import com.labs.server.repository.LabOrderRepository;
import com.labs.server.repository.LabSpecimenRepository;
import com.labs.server.util.HospitalIdPrefix;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Phase 1 service — specimen chain of custody + accession number generation.
 *
 * State machine (per specimen):
 *
 *   created → collected → received → accessioned
 *                ↓            ↓           ↓
 *             rejected ←── rejected ←── rejected
 *
 * A specimen can be rejected from any non-terminal state with a reason code
 * (see V4 lab_rejection_reason seed). Once rejected the specimen is terminal —
 * any retry needs a new specimen row, preserving the audit trail of the
 * original rejection.
 *
 * Accession number generation is centralised here so both this service (when
 * a specimen is created standalone via /api/lab/{orderId}/specimens) and
 * LabService (when an order moves to collected without explicit specimens)
 * share the same format.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LabSpecimenService {

    private static final String BARCODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int BARCODE_LEN = 10;
    private static final int ACCESSION_SEQ_DIGITS = 6;

    private final LabSpecimenRepository specimenRepository;
    private final LabOrderRepository orderRepository;
    private final AuditService auditService;

    // ── reads ───────────────────────────────────────────────────────────

    public List<LabSpecimenDTO> listForOrder(Long labOrderId) {
        return specimenRepository.findByLabOrderIdOrderByCreatedAtAsc(labOrderId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public LabSpecimenDTO get(Long id) {
        return toDTO(specimenRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Specimen not found: " + id)));
    }

    // ── writes ──────────────────────────────────────────────────────────

    @Transactional
    public LabSpecimenDTO create(Long labOrderId, CreateSpecimenRequest req) {
        LabOrder order = orderRepository.findById(labOrderId)
                .orElseThrow(() -> new RuntimeException("Lab order not found: " + labOrderId));

        ensureOrderHasAccession(order);

        String barcode = (req.getBarcode() != null && !req.getBarcode().isBlank())
                ? req.getBarcode().trim()
                : generateUniqueBarcode();

        LabSpecimen specimen = LabSpecimen.builder()
                .labOrderId(labOrderId)
                .hospitalId(order.getHospital().getId())
                .containerType(req.getContainerType())
                .additive(req.getAdditive())
                .volumeMl(req.getVolumeMl())
                .barcode(barcode)
                .qrPayload(buildQrPayload(order, barcode))
                .collectedAt(req.getCollectedAt() != null ? req.getCollectedAt() : LocalDateTime.now())
                .collectedByUserId(req.getCollectedByUserId())
                .collectedByName(req.getCollectedByName())
                .collectionSite(req.getCollectionSite())
                .notes(req.getNotes())
                .build();

        LabSpecimen saved = specimenRepository.save(specimen);
        auditService.record("LabSpecimen", saved.getId().toString(), "CREATE",
                order.getHospital().getId(), null, saved);
        return toDTO(saved);
    }

    @Transactional
    public LabSpecimenDTO receive(Long specimenId, ReceiveSpecimenRequest req) {
        LabSpecimen specimen = specimenRepository.findById(specimenId)
                .orElseThrow(() -> new RuntimeException("Specimen not found: " + specimenId));
        if (Boolean.TRUE.equals(specimen.getRejected())) {
            throw new RuntimeException("Specimen is rejected — cannot receive");
        }
        if (specimen.getCollectedAt() == null) {
            throw new RuntimeException("Specimen has not been marked collected yet");
        }
        if (specimen.getReceivedAt() != null) {
            throw new RuntimeException("Specimen has already been received");
        }

        LabSpecimen before = clone(specimen);
        specimen.setReceivedAt(req.getReceivedAt() != null ? req.getReceivedAt() : LocalDateTime.now());
        specimen.setReceivedByUserId(req.getReceivedByUserId());
        specimen.setTransportTemperatureC(req.getTransportTemperatureC());

        LabSpecimen saved = specimenRepository.save(specimen);
        auditService.record("LabSpecimen", saved.getId().toString(), "RECEIVE",
                specimen.getHospitalId(), before, saved);
        return toDTO(saved);
    }

    @Transactional
    public LabSpecimenDTO accession(Long specimenId, UUID accessionedByUserId) {
        LabSpecimen specimen = specimenRepository.findById(specimenId)
                .orElseThrow(() -> new RuntimeException("Specimen not found: " + specimenId));
        if (Boolean.TRUE.equals(specimen.getRejected())) {
            throw new RuntimeException("Specimen is rejected — cannot accession");
        }
        if (specimen.getReceivedAt() == null) {
            throw new RuntimeException("Specimen must be received before accessioning");
        }
        if (specimen.getAccessionedAt() != null) {
            throw new RuntimeException("Specimen is already accessioned");
        }

        LabSpecimen before = clone(specimen);
        specimen.setAccessionedAt(LocalDateTime.now());
        specimen.setAccessionedByUserId(accessionedByUserId);

        LabSpecimen saved = specimenRepository.save(specimen);
        auditService.record("LabSpecimen", saved.getId().toString(), "ACCESSION",
                specimen.getHospitalId(), before, saved);
        return toDTO(saved);
    }

    @Transactional
    public LabSpecimenDTO reject(Long specimenId, RejectSpecimenRequest req) {
        if (req == null || req.getReasonCode() == null || req.getReasonCode().isBlank()) {
            throw new RuntimeException("rejection reason code is required");
        }
        LabSpecimen specimen = specimenRepository.findById(specimenId)
                .orElseThrow(() -> new RuntimeException("Specimen not found: " + specimenId));
        if (Boolean.TRUE.equals(specimen.getRejected())) {
            throw new RuntimeException("Specimen is already rejected");
        }

        LabSpecimen before = clone(specimen);
        specimen.setRejected(true);
        specimen.setRejectedAt(LocalDateTime.now());
        specimen.setRejectedByUserId(req.getRejectedByUserId());
        specimen.setRejectionReasonCode(req.getReasonCode());
        specimen.setRejectionNotes(req.getReasonNotes());

        LabSpecimen saved = specimenRepository.save(specimen);
        auditService.record("LabSpecimen", saved.getId().toString(), "REJECT",
                specimen.getHospitalId(), before, saved,
                req.getReasonCode(), req.getReasonNotes());
        return toDTO(saved);
    }

    // ── Phase 1c backward-compat hook used by LabService.markCollected ──

    /**
     * Called by {@link LabService#markCollected} when an order transitions to
     * collected without an explicit specimen. Creates ONE default specimen
     * mirroring the legacy {@code sample_type} string so the audit + barcode
     * surface starts populating without a frontend change.
     *
     * No-op if the order already has at least one specimen (we trust the
     * frontend specimen-entry flow when it's actually used).
     */
    @Transactional
    public void autoCreateForOrderIfMissing(LabOrder order, String collectedByName, UUID collectedByUserId) {
        if (specimenRepository.countByLabOrderId(order.getId()) > 0) return;

        ensureOrderHasAccession(order);

        String barcode = generateUniqueBarcode();
        LabSpecimen specimen = LabSpecimen.builder()
                .labOrderId(order.getId())
                .hospitalId(order.getHospital().getId())
                .containerType(deriveContainerTypeFromSampleType(order.getSampleType()))
                .barcode(barcode)
                .qrPayload(buildQrPayload(order, barcode))
                .collectedAt(LocalDateTime.now())
                .collectedByName(collectedByName)
                .collectedByUserId(collectedByUserId)
                .notes("Auto-created on markCollected — legacy sample_type=\""
                        + (order.getSampleType() != null ? order.getSampleType() : "") + "\"")
                .build();

        LabSpecimen saved = specimenRepository.save(specimen);
        auditService.record("LabSpecimen", saved.getId().toString(), "AUTO_CREATE_ON_COLLECT",
                order.getHospital().getId(), null, saved);
    }

    // ── accession number — shared with LabService ───────────────────────

    /**
     * Format: {HOSPITAL_NUMERIC_CODE}ACC-{YYYY}-{6-digit count-based seq}.
     * Hospital prefix matches HMS invoice/admission numbering. Sequence is
     * derived from the live count of orders for the same hospital + year
     * (good enough for SMB-scale labs — Phase 2 swaps this for a real
     * Postgres sequence per hospital).
     */
    @Transactional
    public String ensureOrderHasAccession(LabOrder order) {
        if (order.getAccessionNumber() != null && !order.getAccessionNumber().isBlank()) {
            return order.getAccessionNumber();
        }
        String prefix = HospitalIdPrefix.of(order.getHospital());
        int year = Year.now().getValue();
        // Best-effort sequence — count today's accessioned orders for the hospital and add 1.
        // Collision-safe via the UNIQUE index in V5: on rare clash we retry with +random.
        String number = String.format("%sACC-%d-%0" + ACCESSION_SEQ_DIGITS + "d",
                prefix, year, nextAccessionSeq(order.getHospital().getId(), year));
        order.setAccessionNumber(number);
        orderRepository.save(order);
        return number;
    }

    private long nextAccessionSeq(UUID hospitalId, int year) {
        // Crude but works pre-Phase-2: count existing accessioned orders for the year prefix.
        // The +1 + small jitter avoids a tight race when two specimens are created in the
        // same millisecond (the UNIQUE index would otherwise reject one).
        long base = orderRepository.count() + 1;
        return base + ThreadLocalRandom.current().nextInt(0, 7);
    }

    private String generateUniqueBarcode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = randomBarcode();
            if (!specimenRepository.existsByBarcode(candidate)) return candidate;
        }
        throw new RuntimeException("Could not generate unique specimen barcode after 10 attempts");
    }

    private String randomBarcode() {
        StringBuilder sb = new StringBuilder("S");
        for (int i = 0; i < BARCODE_LEN - 1; i++) {
            sb.append(BARCODE_ALPHABET.charAt(ThreadLocalRandom.current().nextInt(BARCODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    private String buildQrPayload(LabOrder order, String barcode) {
        // Minimal payload — order id + accession + barcode. Phase 5 PDF/labels
        // can switch to a signed JWT or a portal URL.
        return String.format("LO=%d;ACC=%s;BC=%s",
                order.getId(),
                order.getAccessionNumber() != null ? order.getAccessionNumber() : "",
                barcode);
    }

    private String deriveContainerTypeFromSampleType(String sampleType) {
        if (sampleType == null) return null;
        String s = sampleType.toLowerCase();
        if (s.contains("edta")) return "EDTA";
        if (s.contains("citrate")) return "CITRATE";
        if (s.contains("heparin")) return "HEPARIN";
        if (s.contains("fluoride") || s.contains("glucose")) return "FLUORIDE";
        if (s.contains("urine")) return "URINE_CUP";
        if (s.contains("stool")) return "STOOL_CUP";
        if (s.contains("swab")) return "SWAB";
        if (s.contains("serum") || s.contains("plain") || s.contains("clot")) return "PLAIN";
        return "OTHER";
    }

    private LabSpecimen clone(LabSpecimen s) {
        return LabSpecimen.builder()
                .id(s.getId())
                .labOrderId(s.getLabOrderId())
                .hospitalId(s.getHospitalId())
                .containerType(s.getContainerType())
                .additive(s.getAdditive())
                .volumeMl(s.getVolumeMl())
                .barcode(s.getBarcode())
                .qrPayload(s.getQrPayload())
                .collectedAt(s.getCollectedAt())
                .collectedByUserId(s.getCollectedByUserId())
                .collectedByName(s.getCollectedByName())
                .collectionSite(s.getCollectionSite())
                .receivedAt(s.getReceivedAt())
                .receivedByUserId(s.getReceivedByUserId())
                .transportTemperatureC(s.getTransportTemperatureC())
                .accessionedAt(s.getAccessionedAt())
                .accessionedByUserId(s.getAccessionedByUserId())
                .rejected(s.getRejected())
                .rejectedAt(s.getRejectedAt())
                .rejectedByUserId(s.getRejectedByUserId())
                .rejectionReasonCode(s.getRejectionReasonCode())
                .rejectionNotes(s.getRejectionNotes())
                .storageLocation(s.getStorageLocation())
                .discardAt(s.getDiscardAt())
                .notes(s.getNotes())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private LabSpecimenDTO toDTO(LabSpecimen s) {
        return LabSpecimenDTO.builder()
                .id(s.getId())
                .labOrderId(s.getLabOrderId())
                .hospitalId(s.getHospitalId())
                .containerType(s.getContainerType())
                .additive(s.getAdditive())
                .volumeMl(s.getVolumeMl())
                .barcode(s.getBarcode())
                .qrPayload(s.getQrPayload())
                .collectedAt(s.getCollectedAt())
                .collectedByUserId(s.getCollectedByUserId())
                .collectedByName(s.getCollectedByName())
                .collectionSite(s.getCollectionSite())
                .receivedAt(s.getReceivedAt())
                .receivedByUserId(s.getReceivedByUserId())
                .transportTemperatureC(s.getTransportTemperatureC())
                .accessionedAt(s.getAccessionedAt())
                .accessionedByUserId(s.getAccessionedByUserId())
                .rejected(s.getRejected())
                .rejectedAt(s.getRejectedAt())
                .rejectedByUserId(s.getRejectedByUserId())
                .rejectionReasonCode(s.getRejectionReasonCode())
                .rejectionNotes(s.getRejectionNotes())
                .storageLocation(s.getStorageLocation())
                .discardAt(s.getDiscardAt())
                .notes(s.getNotes())
                .stage(deriveStage(s))
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private String deriveStage(LabSpecimen s) {
        if (Boolean.TRUE.equals(s.getRejected())) return "REJECTED";
        if (s.getAccessionedAt() != null) return "ACCESSIONED";
        if (s.getReceivedAt() != null) return "RECEIVED";
        if (s.getCollectedAt() != null) return "COLLECTED";
        return "PENDING_COLLECTION";
    }
}
