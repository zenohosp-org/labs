package com.labs.server.service;

import com.labs.server.context.AuthContext;
import com.labs.server.dto.CreateLabOrderRequest;
import com.labs.server.dto.LabOrderDTO;
import com.labs.server.dto.LabReportRequest;
import com.labs.server.entity.Admission;
import com.labs.server.entity.Hospital;
import com.labs.server.entity.LabOrder;
import com.labs.server.entity.LabPriority;
import com.labs.server.entity.LabStatus;
import com.labs.server.entity.Patient;
import com.labs.server.repository.AdmissionRepository;
import com.labs.server.repository.HospitalRepository;
import com.labs.server.repository.LabOrderRepository;
import com.labs.server.repository.LabServiceRepository;
import com.labs.server.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Mirror of HMS RadiologyService on the main branch — same lifecycle, same
 * auto-bill seam, just scoped to lab orders.
 */
@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class LabService {

    private static final String AMBIGUOUS_FREE = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    // Union surfaced as "completed reports" on the reports page so auto-billed
    // orders (REPORT_GENERATED → BILLED in the same transaction) don't disappear.
    private static final List<LabStatus> COMPLETED_STATUSES =
            List.of(LabStatus.REPORT_GENERATED, LabStatus.BILLED);

    private final LabOrderRepository orderRepository;
    private final HospitalRepository hospitalRepository;
    private final PatientRepository patientRepository;
    private final AdmissionRepository admissionRepository;
    // Phase 8.1 — catalog resolution at order-create time.
    private final LabServiceRepository labServiceRepository;

    // @Lazy avoids the constructor-time cycle: LabBillingService injects
    // LabOrderRepository and now LabService also depends on it.
    @Lazy
    private final LabBillingService labBillingService;

    // Phase 1 — specimen tracking + accession number generation. @Lazy because
    // LabSpecimenService also takes LabOrderRepository, so eager wiring would
    // be a constructor-time cycle.
    @Lazy
    private final LabSpecimenService labSpecimenService;

    // Phase 0 — append-only audit trail.
    private final AuditService auditService;

    // Phase 7 — HIPAA actor capture (request-scoped, so injected via provider).
    private final ObjectProvider<AuthContext> authContextProvider;

    public List<LabOrderDTO> getOrders(UUID hospitalId, String status) {
        if (status != null && !status.isBlank()) {
            if ("COMPLETED".equalsIgnoreCase(status)) {
                return orderRepository
                        .findByHospitalIdAndStatusInOrderByCreatedAtDesc(hospitalId, COMPLETED_STATUSES)
                        .stream().map(this::toDTO).collect(Collectors.toList());
            }
            LabStatus rs = LabStatus.valueOf(status);
            return orderRepository.findByHospitalIdAndStatusOrderByCreatedAtDesc(hospitalId, rs)
                    .stream().map(this::toDTO).collect(Collectors.toList());
        }
        return orderRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public long countCompletedReports(UUID hospitalId) {
        return orderRepository.countByHospitalIdAndStatusIn(hospitalId, COMPLETED_STATUSES);
    }

    public List<LabOrderDTO> getByPatient(Integer patientId) {
        return orderRepository.findByPatientIdOrderByCreatedAtDesc(patientId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<LabOrderDTO> getByAdmission(UUID admissionId) {
        return orderRepository.findByAdmissionIdOrderByCreatedAtDesc(admissionId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public LabOrderDTO getOrder(Long id) {
        return toDTO(orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lab order not found")));
    }

    public long countByStatus(UUID hospitalId, String status) {
        return orderRepository.countByHospitalIdAndStatus(hospitalId, LabStatus.valueOf(status));
    }

    @Transactional
    public LabOrderDTO createOrder(CreateLabOrderRequest req, String createdByName) {
        Hospital hospital = hospitalRepository.findById(req.getHospitalId())
                .orElseThrow(() -> new RuntimeException("Hospital not found"));
        Patient patient = patientRepository.findById(req.getPatientId())
                .orElseThrow(() -> new RuntimeException("Patient not found"));
        if (patient.getHospital() == null
                || !req.getHospitalId().equals(patient.getHospital().getId())) {
            throw new RuntimeException("Patient does not belong to this hospital");
        }

        Admission admission = null;
        if (req.getAdmissionId() != null) {
            admission = admissionRepository.findById(req.getAdmissionId())
                    .orElseThrow(() -> new RuntimeException("Admission not found"));
            if (!admission.getPatient().getId().equals(patient.getId())) {
                throw new RuntimeException("Admission does not belong to this patient");
            }
            if (admission.getHospital() == null
                    || !req.getHospitalId().equals(admission.getHospital().getId())) {
                throw new RuntimeException("Admission does not belong to this hospital");
            }
        }

        // Phase 8.1 — when caller sends a catalogue id, resolve + snapshot.
        // Back-compat: labServiceId == null → free-text path unchanged.
        com.labs.server.entity.LabService catalogRow = null;
        String mappingStatus = null;
        if (req.getLabServiceId() != null) {
            catalogRow = labServiceRepository.findById(req.getLabServiceId())
                    .orElseThrow(() -> new RuntimeException(
                            "Lab service not found: " + req.getLabServiceId()));
            // Cross-tenant guard — catalogue row must belong to caller's hospital.
            if (!req.getHospitalId().equals(catalogRow.getHospitalId())) {
                throw new RuntimeException(
                        "Lab service " + req.getLabServiceId()
                                + " does not belong to hospital " + req.getHospitalId());
            }
            // Cross-discipline guard — lab pipeline only accepts non-radiology.
            String d = catalogRow.getDiscipline();
            if (d == null
                    || "RADIOLOGY".equals(d)
                    || (!"PATHOLOGY".equals(d)
                            && !"CYTOLOGY".equals(d)
                            && !"HISTOPATHOLOGY".equals(d))) {
                throw new RuntimeException(
                        "Lab service " + req.getLabServiceId() + " discipline=" + d
                                + " is not valid for the lab pipeline (use /api/radiology)");
            }
            mappingStatus = "matched";
        }

        // Snapshot precedence: request value wins when present (HMS may still
        // pass its own price during the pricing-authority back-compat window);
        // catalog value fills in when request omits.
        String serviceName        = req.getServiceName()        != null ? req.getServiceName()        : (catalogRow == null ? null : catalogRow.getName());
        String specializationName = req.getSpecializationName() != null ? req.getSpecializationName() : (catalogRow == null ? null : catalogRow.getDiscipline());
        String sampleType         = req.getSampleType()         != null ? req.getSampleType()         : (catalogRow == null ? null : catalogRow.getSpecimenKind());
        java.math.BigDecimal price    = req.getPrice()   != null ? req.getPrice()   : (catalogRow == null ? null : catalogRow.getPrice());
        java.math.BigDecimal gstRate  = req.getGstRate() != null ? req.getGstRate() : (catalogRow == null ? null : catalogRow.getGstRate());

        LabOrder order = LabOrder.builder()
                .hospital(hospital)
                .patient(patient)
                .admission(admission)
                .serviceName(serviceName)
                .specializationName(specializationName)
                .referredByName(createdByName)
                .technicianId(req.getTechnicianId())
                .technicianName(req.getTechnicianName())
                .priority(req.getPriority() != null
                        ? LabPriority.valueOf(req.getPriority())
                        : LabPriority.ROUTINE)
                .status(LabStatus.PENDING_COLLECTION)
                .scheduledDate(req.getScheduledDate())
                .sampleType(sampleType)
                .price(price)
                .gstRate(gstRate)
                .labServiceId(req.getLabServiceId())
                .serviceNameMappingStatus(mappingStatus)
                .createdByName(createdByName)
                .build();

        LabOrder saved = orderRepository.save(order);
        // Phase 1 — assign accession number at creation so it can be printed
        // on the requisition before the sample is even drawn.
        labSpecimenService.ensureOrderHasAccession(saved);
        auditService.record("LabOrder", saved.getId().toString(), "CREATE",
                hospital.getId(), null, saved);
        return toDTO(saved);
    }

    /**
     * Cancel a lab order. Only allowed in PENDING_COLLECTION state — once
     * the sample is in the analyser the workflow is locked. Mirrors HMS's
     * existing IPD lab-orders semantics ("Only PENDING orders can be cancelled").
     */
    @Transactional
    public void cancel(Long id) {
        LabOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (order.getStatus() != LabStatus.PENDING_COLLECTION) {
            throw new RuntimeException("Only PENDING_COLLECTION orders can be cancelled");
        }
        UUID hospitalId = order.getHospital() != null ? order.getHospital().getId() : null;
        orderRepository.delete(order);
        auditService.record("LabOrder", String.valueOf(id), "DELETE",
                hospitalId, order, null);
    }

    @Transactional
    public LabOrderDTO markCollected(Long id) {
        LabOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (order.getStatus() != LabStatus.PENDING_COLLECTION) {
            throw new RuntimeException("Order is not in PENDING_COLLECTION state");
        }
        LabStatus previous = order.getStatus();
        Actor actor = resolveActor();
        LocalDateTime now = LocalDateTime.now();

        order.setStatus(LabStatus.AWAITING_REPORT);
        order.setCollectedAt(now);
        order.setCollectedByUserId(actor.userId);
        order.setCollectedByName(actor.name);
        LabOrder saved = orderRepository.save(order);

        // Phase 1c — auto-create a default specimen row if the frontend hasn't
        // already submitted one via POST /api/lab/{id}/specimens. Keeps the
        // legacy "click Collect" flow working while the new specimen-entry UI
        // is being rolled out. No-op for orders that already have specimens.
        try {
            labSpecimenService.autoCreateForOrderIfMissing(saved, actor.name, actor.userId);
        } catch (Exception e) {
            LoggerFactory.getLogger(LabService.class)
                    .warn("Auto-specimen creation failed for lab order {}: {}", saved.getId(), e.getMessage());
        }

        auditService.record("LabOrder", saved.getId().toString(), "STATUS_CHANGE",
                saved.getHospital().getId(),
                Map.of("status", previous.name()),
                Map.of("status", LabStatus.AWAITING_REPORT.name(),
                        "collectedAt", now.toString(),
                        "collectedByName", actor.name != null ? actor.name : ""));
        return toDTO(saved);
    }

    /**
     * Phase 7 — lab receiving desk takes custody of the sample. Stamps
     * received_at + actor on the lab_orders row (denormalised projection
     * of the specimen's own chain-of-custody for fast "awaiting receive"
     * queries). Status stays AWAITING_REPORT — receive is observational,
     * not a status transition.
     */
    @Transactional
    public LabOrderDTO markReceived(Long id) {
        LabOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (order.getStatus() != LabStatus.AWAITING_REPORT) {
            throw new RuntimeException("Order is not in AWAITING_REPORT state");
        }
        if (order.getReceivedAt() != null) {
            throw new RuntimeException("Order already marked received");
        }
        Actor actor = resolveActor();
        LocalDateTime now = LocalDateTime.now();
        order.setReceivedAt(now);
        order.setReceivedByUserId(actor.userId);
        order.setReceivedByName(actor.name);
        LabOrder saved = orderRepository.save(order);

        auditService.record("LabOrder", saved.getId().toString(), "RECEIVE",
                saved.getHospital().getId(),
                null,
                Map.of("receivedAt", now.toString(),
                        "receivedByName", actor.name != null ? actor.name : ""));
        return toDTO(saved);
    }

    /**
     * Phase 7 — tech moves order from AWAITING_REPORT to IN_PROGRESS
     * (analyser run started). New HIPAA-grade timestamp + actor stamp.
     */
    @Transactional
    public LabOrderDTO markStarted(Long id) {
        LabOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (order.getStatus() != LabStatus.AWAITING_REPORT) {
            throw new RuntimeException("Order is not in AWAITING_REPORT state — current: " + order.getStatus());
        }
        LabStatus previous = order.getStatus();
        Actor actor = resolveActor();
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(LabStatus.IN_PROGRESS);
        order.setStartedAt(now);
        order.setStartedByUserId(actor.userId);
        order.setStartedByName(actor.name);
        LabOrder saved = orderRepository.save(order);

        auditService.record("LabOrder", saved.getId().toString(), "START",
                saved.getHospital().getId(),
                Map.of("status", previous.name()),
                Map.of("status", LabStatus.IN_PROGRESS.name(),
                        "startedAt", now.toString(),
                        "startedByName", actor.name != null ? actor.name : ""));
        return toDTO(saved);
    }

    @Transactional
    public LabOrderDTO generateReport(Long id, LabReportRequest req) {
        LabOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        // Phase 7 — accept either AWAITING_REPORT (legacy direct-report flow)
        // or IN_PROGRESS (new lifecycle, tech ran the analyser first).
        if (order.getStatus() != LabStatus.AWAITING_REPORT
                && order.getStatus() != LabStatus.IN_PROGRESS) {
            throw new RuntimeException("Order is not in AWAITING_REPORT or IN_PROGRESS state — current: " + order.getStatus());
        }
        LabStatus previous = order.getStatus();
        String previousFindings = order.getFindings();
        Actor actor = resolveActor();
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(LabStatus.REPORT_GENERATED);
        order.setFindings(req.getFindings());
        order.setObservation(req.getObservation());
        order.setReportedAt(now);
        order.setReportedByUserId(actor.userId);
        order.setReportedByName(actor.name);
        order.setReportId(generateReportId());
        LabOrder saved = orderRepository.save(order);

        // Auto-bill if a price was captured at order time. Routes to the
        // patient's active IPD invoice if admitted; otherwise creates a
        // standalone OPD lab invoice. No-op if price is null — staff can
        // still bill manually via the HMS billing UI.
        try {
            labBillingService.billLabOrder(saved);
        } catch (Exception e) {
            LoggerFactory.getLogger(LabService.class)
                    .warn("Auto-bill failed for lab order {}: {}", saved.getId(), e.getMessage());
        }

        auditService.record("LabOrder", saved.getId().toString(), "REPORT_GENERATED",
                saved.getHospital().getId(),
                java.util.Map.of("status", previous.name(),
                        "findings", previousFindings != null ? previousFindings : ""),
                java.util.Map.of("status", LabStatus.REPORT_GENERATED.name(),
                        "findings", req.getFindings() != null ? req.getFindings() : "",
                        "reportId", saved.getReportId()));
        return toDTO(saved);
    }

    private String generateReportId() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            sb.append(AMBIGUOUS_FREE.charAt(ThreadLocalRandom.current().nextInt(AMBIGUOUS_FREE.length())));
        }
        return sb.toString();
    }

    /**
     * Phase 7 — resolves {userId, displayName} from the current JWT for
     * HIPAA actor stamping. Falls back to ('System', null) when no JWT is
     * on the call (e.g. internal scheduler).
     */
    private Actor resolveActor() {
        try {
            AuthContext ctx = authContextProvider.getIfAvailable();
            if (ctx != null) {
                UUID uid = null;
                try { uid = ctx.getUserId() != null ? UUID.fromString(ctx.getUserId()) : null; }
                catch (IllegalArgumentException ignored) { }
                String name = ctx.getEmail();
                if (name == null) name = "System";
                return new Actor(uid, name);
            }
        } catch (Exception ignored) {
            // Auth context not request-scoped here — happens for async paths
        }
        return new Actor(null, "System");
    }

    private record Actor(UUID userId, String name) { }

    private LabOrderDTO toDTO(LabOrder o) {
        String patientName = o.getPatient().getFirstName()
                + (o.getPatient().getLastName() != null ? " " + o.getPatient().getLastName() : "");

        // Phase 8.1 — when the order carries a catalog FK, project the rows
        // discipline + valueType so the FE can pick the right report-entry UX
        // (per-analyte panel for numeric pathology; narrative findings for
        // radiology/text). One PK lookup; negligible cost vs the FE re-fetch
        // it replaces. Null for legacy free-text orders.
        String catalogDiscipline = null;
        String catalogValueType = null;
        if (o.getLabServiceId() != null) {
            try {
                var row = labServiceRepository.findById(o.getLabServiceId()).orElse(null);
                if (row != null) {
                    catalogDiscipline = row.getDiscipline();
                    catalogValueType = row.getValueType();
                }
            } catch (Exception ignored) { }
        }

        return LabOrderDTO.builder()
                .id(o.getId())
                .hospitalId(o.getHospital().getId())
                .patientId(o.getPatient().getId())
                .patientName(patientName)
                .patientUhid(o.getPatient().getUhid())
                .admissionId(o.getAdmission() != null ? o.getAdmission().getId() : null)
                .admissionNumber(o.getAdmission() != null ? o.getAdmission().getAdmissionNumber() : null)
                .serviceName(o.getServiceName())
                .specializationName(o.getSpecializationName())
                .referredByName(o.getReferredByName())
                .technicianId(o.getTechnicianId())
                .technicianName(o.getTechnicianName())
                .priority(o.getPriority().name())
                .status(o.getStatus().name())
                .scheduledDate(o.getScheduledDate())
                .billNo(o.getBillNo())
                .sampleType(o.getSampleType())
                .price(o.getPrice())
                .gstRate(o.getGstRate())
                .collectedAt(o.getCollectedAt())
                .collectedByUserId(o.getCollectedByUserId())
                .collectedByName(o.getCollectedByName())
                .receivedAt(o.getReceivedAt())
                .receivedByUserId(o.getReceivedByUserId())
                .receivedByName(o.getReceivedByName())
                .startedAt(o.getStartedAt())
                .startedByUserId(o.getStartedByUserId())
                .startedByName(o.getStartedByName())
                .reportedAt(o.getReportedAt())
                .reportedByUserId(o.getReportedByUserId())
                .reportedByName(o.getReportedByName())
                .findings(o.getFindings())
                .observation(o.getObservation())
                .reportId(o.getReportId())
                .accessionNumber(o.getAccessionNumber())
                .labServiceId(o.getLabServiceId())
                .labServiceDiscipline(catalogDiscipline)
                .labServiceValueType(catalogValueType)
                .serviceNameMappingStatus(o.getServiceNameMappingStatus())
                .createdByName(o.getCreatedByName())
                .createdAt(o.getCreatedAt())
                .build();
    }
}
