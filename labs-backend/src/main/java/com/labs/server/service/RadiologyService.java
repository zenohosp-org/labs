package com.labs.server.service;

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
import com.labs.server.repository.PatientRepository;
import com.labs.server.repository.RadiologyOrderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
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

    @Lazy
    private final RadiologyBillingService billingService;

    public List<RadiologyOrderDTO> getOrders(UUID hospitalId, String status) {
        if (status != null && !status.isBlank()) {
            if ("COMPLETED".equalsIgnoreCase(status)) {
                return orderRepository
                        .findByHospitalIdAndStatusInOrderByCreatedAtDesc(hospitalId, COMPLETED_STATUSES)
                        .stream().map(this::toDTO).collect(Collectors.toList());
            }
            RadiologyStatus rs = RadiologyStatus.valueOf(status);
            return orderRepository.findByHospitalIdAndStatusOrderByCreatedAtDesc(hospitalId, rs)
                    .stream().map(this::toDTO).collect(Collectors.toList());
        }
        return orderRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public long countCompletedReports(UUID hospitalId) {
        return orderRepository.countByHospitalIdAndStatusIn(hospitalId, COMPLETED_STATUSES);
    }

    public List<RadiologyOrderDTO> getByPatient(Integer patientId) {
        return orderRepository.findByPatientIdOrderByCreatedAtDesc(patientId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<RadiologyOrderDTO> getByAdmission(UUID admissionId) {
        return orderRepository.findByAdmissionIdOrderByCreatedAtDesc(admissionId)
                .stream().map(this::toDTO).collect(Collectors.toList());
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

        RadiologyOrder order = RadiologyOrder.builder()
                .hospital(hospital)
                .patient(patient)
                .admission(admission)
                .serviceName(req.getServiceName())
                .specializationName(req.getSpecializationName())
                .referredByName(createdByName)
                .technicianId(req.getTechnicianId())
                .technicianName(req.getTechnicianName())
                .priority(req.getPriority() != null
                        ? RadiologyPriority.valueOf(req.getPriority())
                        : RadiologyPriority.ROUTINE)
                .status(RadiologyStatus.PENDING_SCAN)
                .scheduledDate(req.getScheduledDate())
                .price(req.getPrice())
                .createdByName(createdByName)
                .build();

        return toDTO(orderRepository.save(order));
    }

    @Transactional
    public RadiologyOrderDTO markScanned(Long id) {
        RadiologyOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (order.getStatus() != RadiologyStatus.PENDING_SCAN) {
            throw new RuntimeException("Order is not in PENDING_SCAN state");
        }
        order.setStatus(RadiologyStatus.AWAITING_REPORT);
        order.setScannedAt(LocalDateTime.now());
        return toDTO(orderRepository.save(order));
    }

    @Transactional
    public RadiologyOrderDTO generateReport(Long id, RadiologyReportRequest req) {
        RadiologyOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (order.getStatus() != RadiologyStatus.AWAITING_REPORT) {
            throw new RuntimeException("Order is not in AWAITING_REPORT state");
        }
        order.setStatus(RadiologyStatus.REPORT_GENERATED);
        order.setFindings(req.getFindings());
        order.setObservation(req.getObservation());
        order.setReportedAt(LocalDateTime.now());
        order.setReportId(generateReportId());
        RadiologyOrder saved = orderRepository.save(order);

        try {
            billingService.billRadiologyOrder(saved);
        } catch (Exception e) {
            LoggerFactory.getLogger(RadiologyService.class)
                    .warn("Auto-bill failed for radiology order {}: {}", saved.getId(), e.getMessage());
        }
        return toDTO(saved);
    }

    private String generateReportId() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            sb.append(AMBIGUOUS_FREE.charAt(ThreadLocalRandom.current().nextInt(AMBIGUOUS_FREE.length())));
        }
        return sb.toString();
    }

    private RadiologyOrderDTO toDTO(RadiologyOrder o) {
        String patientName = o.getPatient().getFirstName()
                + (o.getPatient().getLastName() != null ? " " + o.getPatient().getLastName() : "");

        // Payment surface — derived live from the linked invoice (one extra
        // SELECT per order, indexed by radiology_order_id). Cheap because the
        // queue/reports views never load more than a couple of hundred rows
        // and the JOIN target is an indexed FK. Null when the order has never
        // been billed (PENDING_SCAN / AWAITING_REPORT pre-report).
        com.labs.server.entity.Invoice linkedInvoice = invoiceRepository
                .findByRadiologyOrderId(o.getId())
                .stream().findFirst().orElse(null);

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
                .reportedAt(o.getReportedAt())
                .findings(o.getFindings())
                .observation(o.getObservation())
                .reportId(o.getReportId())
                .createdByName(o.getCreatedByName())
                .createdAt(o.getCreatedAt());

        if (linkedInvoice != null) {
            b.invoiceStatus(linkedInvoice.getStatus() != null ? linkedInvoice.getStatus().name() : null)
             .invoiceNumber(linkedInvoice.getInvoiceNumber())
             .invoicePaid(linkedInvoice.getPaidAmount())
             .invoiceTotal(linkedInvoice.getTotal());
        }
        return b.build();
    }
}
