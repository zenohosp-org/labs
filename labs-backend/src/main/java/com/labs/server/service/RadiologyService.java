package com.labs.server.service;

import com.labs.server.context.AuthContext;
import com.labs.server.dto.CreateRadiologyOrderRequest;
import com.labs.server.dto.RadiologyOrderDTO;
import com.labs.server.dto.RadiologyReportRequest;
import com.labs.server.entity.Admission;
import com.labs.server.entity.Hospital;
import com.labs.server.entity.Patient;
import com.labs.server.entity.RadiologyOrder;
import com.labs.server.entity.RadiologyPriority;
import com.labs.server.entity.RadiologyStatus;
import com.labs.server.repository.AdmissionRepository;
import com.labs.server.repository.HospitalRepository;
import com.labs.server.repository.LabServiceRepository;
import com.labs.server.repository.PatientRepository;
import com.labs.server.repository.RadiologyOrderRepository;
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
 * Mirror of HMS {@code RadiologyService}. Same lifecycle, same auto-bill
 * seam, same {@code COMPLETED} (REPORT_GENERATED + BILLED) reports filter.
 */
@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class RadiologyService {

    private static final String AMBIGUOUS_FREE = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    // Surface for the "completed reports" view so auto-billed orders
    // (REPORT_GENERATED → BILLED in the same transaction) stay visible.
    private static final List<RadiologyStatus> COMPLETED_STATUSES =
            List.of(RadiologyStatus.REPORT_GENERATED, RadiologyStatus.BILLED);

    private final RadiologyOrderRepository orderRepository;
    private final HospitalRepository hospitalRepository;
    private final PatientRepository patientRepository;
    private final AdmissionRepository admissionRepository;
    private final com.labs.server.repository.InvoiceRepository invoiceRepository;
    // Phase 8.1 — catalog resolution at order-create time.
    private final LabServiceRepository labServiceRepository;

    @Lazy
    private final RadiologyBillingService billingService;

    // Phase 7 — audit + HIPAA actor capture
    private final AuditService auditService;
    private final ObjectProvider<AuthContext> authContextProvider;

    public List<RadiologyOrderDTO> getOrders(UUID hospitalId, String status) {
        List<RadiologyOrder> rows;
        if (status != null && !status.isBlank()) {
            if ("COMPLETED".equalsIgnoreCase(status)) {
                rows = orderRepository
                        .findByHospitalIdAndStatusInOrderByCreatedAtDesc(hospitalId, COMPLETED_STATUSES);
            } else {
                rows = orderRepository.findByHospitalIdAndStatusOrderByCreatedAtDesc(
                        hospitalId, RadiologyStatus.valueOf(status));
            }
        } else {
            rows = orderRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId);
        }
        return toDTOs(rows);
    }

    public long countCompletedReports(UUID hospitalId) {
        return orderRepository.countByHospitalIdAndStatusIn(hospitalId, COMPLETED_STATUSES);
    }

    public List<RadiologyOrderDTO> getByPatient(Integer patientId) {
        return toDTOs(orderRepository.findByPatientIdOrderByCreatedAtDesc(patientId));
    }

    public List<RadiologyOrderDTO> getByAdmission(UUID admissionId) {
        return toDTOs(orderRepository.findByAdmissionIdOrderByCreatedAtDesc(admissionId));
    }

    public RadiologyOrderDTO getOrder(Long id) {
        return toDTO(orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Radiology order not found")));
    }

    public long countByStatus(UUID hospitalId, String status) {
        return orderRepository.countByHospitalIdAndStatus(hospitalId, RadiologyStatus.valueOf(status));
    }

    @Transactional
    public RadiologyOrderDTO createOrder(CreateRadiologyOrderRequest req, String createdByName) {
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

        // Phase 8.1 — catalog resolution + snapshot when labServiceId is sent.
        com.labs.server.entity.LabService catalogRow = null;
        String mappingStatus = null;
        if (req.getLabServiceId() != null) {
            catalogRow = labServiceRepository.findById(req.getLabServiceId())
                    .orElseThrow(() -> new RuntimeException(
                            "Lab service not found: " + req.getLabServiceId()));
            if (!req.getHospitalId().equals(catalogRow.getHospitalId())) {
                throw new RuntimeException(
                        "Lab service " + req.getLabServiceId()
                                + " does not belong to hospital " + req.getHospitalId());
            }
            // Radiology pipeline accepts only RADIOLOGY discipline.
            if (!"RADIOLOGY".equals(catalogRow.getDiscipline())) {
                throw new RuntimeException(
                        "Lab service " + req.getLabServiceId() + " discipline="
                                + catalogRow.getDiscipline()
                                + " is not valid for the radiology pipeline (use /api/lab)");
            }
            mappingStatus = "matched";
        }

        // Snapshot precedence: request value wins; catalog fills when omitted.
        String serviceName        = req.getServiceName()        != null ? req.getServiceName()        : (catalogRow == null ? null : catalogRow.getName());
        String specializationName = req.getSpecializationName() != null ? req.getSpecializationName() : (catalogRow == null ? null : catalogRow.getDiscipline());
        java.math.BigDecimal price   = req.getPrice()   != null ? req.getPrice()   : (catalogRow == null ? null : catalogRow.getPrice());
        java.math.BigDecimal gstRate = req.getGstRate() != null ? req.getGstRate() : (catalogRow == null ? null : catalogRow.getGstRate());

        RadiologyOrder order = RadiologyOrder.builder()
                .hospital(hospital)
                .patient(patient)
                .admission(admission)
                .serviceName(serviceName)
                .specializationName(specializationName)
                .referredByName(createdByName)
                .technicianId(req.getTechnicianId())
                .technicianName(req.getTechnicianName())
                .priority(req.getPriority() != null
                        ? RadiologyPriority.valueOf(req.getPriority())
                        : RadiologyPriority.ROUTINE)
                .status(RadiologyStatus.PENDING_SCAN)
                .scheduledDate(req.getScheduledDate())
                .price(price)
                .gstRate(gstRate)
                .labServiceId(req.getLabServiceId())
                .serviceNameMappingStatus(mappingStatus)
                .createdByName(createdByName)
                .build();

        RadiologyOrder saved = orderRepository.save(order);
        // Phase 8.1 parity gap fix — audit was missing on radiology createOrder.
        auditService.record("RadiologyOrder", saved.getId().toString(), "CREATE",
                hospital.getId(), null, saved);
        return toDTO(saved);
    }

    @Transactional
    public RadiologyOrderDTO markScanned(Long id) {
        RadiologyOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        // Phase 7 — accept PENDING_SCAN (legacy direct-mark flow) or
        // IN_PROGRESS (new lifecycle, tech started the modality run first).
        if (order.getStatus() != RadiologyStatus.PENDING_SCAN
                && order.getStatus() != RadiologyStatus.IN_PROGRESS) {
            throw new RuntimeException("Order is not in PENDING_SCAN or IN_PROGRESS state — current: " + order.getStatus());
        }
        RadiologyStatus previous = order.getStatus();
        Actor actor = resolveActor();
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(RadiologyStatus.AWAITING_REPORT);
        order.setScannedAt(now);
        order.setScannedByUserId(actor.userId);
        order.setScannedByName(actor.name);
        RadiologyOrder saved = orderRepository.save(order);

        auditService.record("RadiologyOrder", saved.getId().toString(), "STATUS_CHANGE",
                saved.getHospital().getId(),
                Map.of("status", previous.name()),
                Map.of("status", RadiologyStatus.AWAITING_REPORT.name(),
                        "scannedAt", now.toString(),
                        "scannedByName", actor.name != null ? actor.name : ""));
        return toDTO(saved);
    }

    /**
     * Phase 7 — tech started the modality run.
     * PENDING_SCAN → IN_PROGRESS, stamps started_at + actor.
     */
    @Transactional
    public RadiologyOrderDTO markStarted(Long id) {
        RadiologyOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (order.getStatus() != RadiologyStatus.PENDING_SCAN) {
            throw new RuntimeException("Order is not in PENDING_SCAN state — current: " + order.getStatus());
        }
        RadiologyStatus previous = order.getStatus();
        Actor actor = resolveActor();
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(RadiologyStatus.IN_PROGRESS);
        order.setStartedAt(now);
        order.setStartedByUserId(actor.userId);
        order.setStartedByName(actor.name);
        RadiologyOrder saved = orderRepository.save(order);

        auditService.record("RadiologyOrder", saved.getId().toString(), "START",
                saved.getHospital().getId(),
                Map.of("status", previous.name()),
                Map.of("status", RadiologyStatus.IN_PROGRESS.name(),
                        "startedAt", now.toString(),
                        "startedByName", actor.name != null ? actor.name : ""));
        return toDTO(saved);
    }

    /**
     * Phase 9 — Mark Completed for radiology. AWAITING_REPORT or IN_PROGRESS
     * → REPORT_GENERATED, gated on findings text presence (radiology has no
     * per-analyte rows). Auto-bills via the same seam as generateReport.
     */
    @Transactional
    public RadiologyOrderDTO markCompleted(Long id) {
        RadiologyOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (order.getStatus() != RadiologyStatus.AWAITING_REPORT
                && order.getStatus() != RadiologyStatus.IN_PROGRESS) {
            throw new RuntimeException(
                    "Order is not in AWAITING_REPORT or IN_PROGRESS state — current: "
                            + order.getStatus());
        }
        boolean hasFindings = order.getFindings() != null && !order.getFindings().isBlank();
        if (!hasFindings) {
            throw new RuntimeException(
                    "No report data — click Write Report to enter findings first.");
        }
        RadiologyStatus previous = order.getStatus();
        Actor actor = resolveActor();
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(RadiologyStatus.REPORT_GENERATED);
        order.setReportedAt(now);
        order.setReportedByUserId(actor.userId);
        order.setReportedByName(actor.name);
        if (order.getReportId() == null) {
            order.setReportId(generateReportId());
        }
        RadiologyOrder saved = orderRepository.save(order);

        try {
            billingService.billRadiologyOrder(saved);
        } catch (Exception e) {
            LoggerFactory.getLogger(RadiologyService.class)
                    .warn("Auto-bill failed for radiology order {}: {}", saved.getId(), e.getMessage());
        }

        auditService.record("RadiologyOrder", saved.getId().toString(), "REPORT_GENERATED",
                saved.getHospital().getId(),
                Map.of("status", previous.name()),
                Map.of("status", RadiologyStatus.REPORT_GENERATED.name(),
                        "reportId", saved.getReportId()));
        return toDTO(saved);
    }

    /** Phase 9 — soft cancel. Same semantics as LabService.cancelOrder. */
    @Transactional
    public RadiologyOrderDTO cancelOrder(Long id, String reason) {
        RadiologyOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        RadiologyStatus cur = order.getStatus();
        if (cur != RadiologyStatus.PENDING_SCAN
                && cur != RadiologyStatus.AWAITING_REPORT
                && cur != RadiologyStatus.IN_PROGRESS) {
            throw new RuntimeException(
                    "Cannot cancel — order is already " + cur + " (terminal state).");
        }
        Actor actor = resolveActor();
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(RadiologyStatus.CANCELLED);
        order.setCancelledAt(now);
        order.setCancelledByUserId(actor.userId);
        order.setCancelledByName(actor.name);
        if (reason != null && !reason.isBlank()) {
            order.setCancellationReason(reason.trim());
        }
        RadiologyOrder saved = orderRepository.save(order);

        auditService.record("RadiologyOrder", saved.getId().toString(), "CANCEL",
                saved.getHospital().getId(),
                Map.of("status", cur.name()),
                Map.of("status", RadiologyStatus.CANCELLED.name(),
                        "reason", reason != null ? reason : ""));
        return toDTO(saved);
    }

    @Transactional
    public RadiologyOrderDTO generateReport(Long id, RadiologyReportRequest req) {
        RadiologyOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (order.getStatus() != RadiologyStatus.AWAITING_REPORT) {
            throw new RuntimeException("Order is not in AWAITING_REPORT state — current: " + order.getStatus());
        }
        RadiologyStatus previous = order.getStatus();
        Actor actor = resolveActor();
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(RadiologyStatus.REPORT_GENERATED);
        order.setFindings(req.getFindings());
        order.setObservation(req.getObservation());
        order.setReportedAt(now);
        order.setReportedByUserId(actor.userId);
        order.setReportedByName(actor.name);
        order.setReportId(generateReportId());
        RadiologyOrder saved = orderRepository.save(order);

        try {
            billingService.billRadiologyOrder(saved);
        } catch (Exception e) {
            LoggerFactory.getLogger(RadiologyService.class)
                    .warn("Auto-bill failed for radiology order {}: {}", saved.getId(), e.getMessage());
        }

        auditService.record("RadiologyOrder", saved.getId().toString(), "REPORT_GENERATED",
                saved.getHospital().getId(),
                Map.of("status", previous.name()),
                Map.of("status", RadiologyStatus.REPORT_GENERATED.name(),
                        "reportedAt", now.toString(),
                        "reportedByName", actor.name != null ? actor.name : "",
                        "reportId", saved.getReportId()));
        return toDTO(saved);
    }

    /**
     * Phase 7 — resolves {userId, displayName} from the current JWT for
     * HIPAA actor stamping.
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
        } catch (Exception ignored) { }
        return new Actor(null, "System");
    }

    private record Actor(UUID userId, String name) { }

    private String generateReportId() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            sb.append(AMBIGUOUS_FREE.charAt(ThreadLocalRandom.current().nextInt(AMBIGUOUS_FREE.length())));
        }
        return sb.toString();
    }

    /**
     * List variant that pre-fetches all linked invoices in one query — kills
     * the N+1 we'd otherwise hit when serialising a page of orders.
     * Falls back to the single-row toDTO for empty lists so the wire shape
     * stays identical.
     */
    private List<RadiologyOrderDTO> toDTOs(List<RadiologyOrder> rows) {
        if (rows == null || rows.isEmpty()) return java.util.Collections.emptyList();
        java.util.Set<Long> ids = rows.stream()
                .map(RadiologyOrder::getId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        // Group by radiology_order_id → most recent invoice. The query orders
        // by createdAt DESC so the first hit per id wins on putIfAbsent.
        java.util.Map<Long, com.labs.server.entity.Invoice> byOrder = new java.util.HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] tuple : invoiceRepository.findInvoicesForRadiologyOrders(ids)) {
                Long orderId = (Long) tuple[0];
                com.labs.server.entity.Invoice inv = (com.labs.server.entity.Invoice) tuple[1];
                byOrder.putIfAbsent(orderId, inv);
            }
        }
        return rows.stream()
                .map(o -> toDTO(o, byOrder.get(o.getId())))
                .collect(Collectors.toList());
    }

    /** Single-row entry point used by createOrder/markScanned/generateReport/getOrder. */
    private RadiologyOrderDTO toDTO(RadiologyOrder o) {
        com.labs.server.entity.Invoice linked = invoiceRepository
                .findByRadiologyOrderId(o.getId())
                .stream().findFirst().orElse(null);
        return toDTO(o, linked);
    }

    private RadiologyOrderDTO toDTO(RadiologyOrder o, com.labs.server.entity.Invoice linkedInvoice) {
        String patientName = o.getPatient().getFirstName()
                + (o.getPatient().getLastName() != null ? " " + o.getPatient().getLastName() : "");

        RadiologyOrderDTO.RadiologyOrderDTOBuilder b = RadiologyOrderDTO.builder()
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
                .price(o.getPrice())
                .scannedAt(o.getScannedAt())
                .scannedByUserId(o.getScannedByUserId())
                .scannedByName(o.getScannedByName())
                .receivedAt(o.getReceivedAt())
                .receivedByUserId(o.getReceivedByUserId())
                .receivedByName(o.getReceivedByName())
                .startedAt(o.getStartedAt())
                .startedByUserId(o.getStartedByUserId())
                .startedByName(o.getStartedByName())
                .reportedAt(o.getReportedAt())
                .reportedByUserId(o.getReportedByUserId())
                .reportedByName(o.getReportedByName())
                .cancelledAt(o.getCancelledAt())
                .cancelledByUserId(o.getCancelledByUserId())
                .cancelledByName(o.getCancelledByName())
                .cancellationReason(o.getCancellationReason())
                .findings(o.getFindings())
                .observation(o.getObservation())
                .reportId(o.getReportId())
                .createdByName(o.getCreatedByName())
                .createdAt(o.getCreatedAt());

        if (linkedInvoice != null) {
            b.invoiceStatus(linkedInvoice.getStatus() != null ? linkedInvoice.getStatus().name() : null)
             .invoiceNumber(linkedInvoice.getInvoiceNumber())
             .invoiceId(linkedInvoice.getId())
             .invoicePaid(linkedInvoice.getPaidAmount())
             .invoiceTotal(linkedInvoice.getTotal());
        }
        return b.build();
    }
}
