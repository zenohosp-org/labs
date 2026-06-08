package com.labs.server.service;

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
import com.labs.server.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    // @Lazy avoids the constructor-time cycle: LabBillingService injects
    // LabOrderRepository and now LabService also depends on it.
    @Lazy
    private final LabBillingService labBillingService;

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

        LabOrder order = LabOrder.builder()
                .hospital(hospital)
                .patient(patient)
                .admission(admission)
                .serviceName(req.getServiceName())
                .specializationName(req.getSpecializationName())
                .referredByName(createdByName)
                .technicianId(req.getTechnicianId())
                .technicianName(req.getTechnicianName())
                .priority(req.getPriority() != null
                        ? LabPriority.valueOf(req.getPriority())
                        : LabPriority.ROUTINE)
                .status(LabStatus.PENDING_COLLECTION)
                .scheduledDate(req.getScheduledDate())
                .sampleType(req.getSampleType())
                .price(req.getPrice())
                .createdByName(createdByName)
                .build();

        return toDTO(orderRepository.save(order));
    }

    @Transactional
    public LabOrderDTO markCollected(Long id) {
        LabOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (order.getStatus() != LabStatus.PENDING_COLLECTION) {
            throw new RuntimeException("Order is not in PENDING_COLLECTION state");
        }
        order.setStatus(LabStatus.AWAITING_REPORT);
        order.setCollectedAt(java.time.LocalDateTime.now());
        return toDTO(orderRepository.save(order));
    }

    @Transactional
    public LabOrderDTO generateReport(Long id, LabReportRequest req) {
        LabOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (order.getStatus() != LabStatus.AWAITING_REPORT) {
            throw new RuntimeException("Order is not in AWAITING_REPORT state");
        }
        order.setStatus(LabStatus.REPORT_GENERATED);
        order.setFindings(req.getFindings());
        order.setObservation(req.getObservation());
        order.setReportedAt(java.time.LocalDateTime.now());
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
        return toDTO(saved);
    }

    private String generateReportId() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            sb.append(AMBIGUOUS_FREE.charAt(ThreadLocalRandom.current().nextInt(AMBIGUOUS_FREE.length())));
        }
        return sb.toString();
    }

    private LabOrderDTO toDTO(LabOrder o) {
        String patientName = o.getPatient().getFirstName()
                + (o.getPatient().getLastName() != null ? " " + o.getPatient().getLastName() : "");
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
                .collectedAt(o.getCollectedAt())
                .reportedAt(o.getReportedAt())
                .findings(o.getFindings())
                .observation(o.getObservation())
                .reportId(o.getReportId())
                .createdByName(o.getCreatedByName())
                .createdAt(o.getCreatedAt())
                .build();
    }
}
