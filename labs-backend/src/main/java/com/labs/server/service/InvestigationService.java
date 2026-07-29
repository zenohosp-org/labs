package com.labs.server.service;

import com.labs.server.dto.InvestigationSummaryDTO;
import com.labs.server.entity.Invoice;
import com.labs.server.entity.LabOrder;
import com.labs.server.entity.RadiologyOrder;
import com.labs.server.repository.InvoiceRepository;
import com.labs.server.repository.LabOrderRepository;
import com.labs.server.repository.RadiologyOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Combined lab + radiology projection. Powers HMS's IPD Labs tab and the
 * consultation-view Labs panel without forcing HMS to make two round-trips
 * and merge in JS. Both source tables flow through one shape ({@link
 * InvestigationSummaryDTO}) tagged with {@code kind}.
 *
 * Sort order matches what HMS already shows on a single-list view: newest
 * createdAt first, regardless of source.
 */
@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class InvestigationService {

    private final LabOrderRepository labOrderRepository;
    private final RadiologyOrderRepository radiologyOrderRepository;
    private final InvoiceRepository invoiceRepository;

    /**
     * hospitalId comes from the caller's validated JWT, never a request param.
     * Admission and patient ids are supplied by the client and are enumerable,
     * so both source queries are tenant-scoped: a foreign admission/patient
     * yields an empty list rather than another hospital's clinical history.
     * The invoice lookups below are keyed off the order ids returned here, so
     * they inherit that scope and need no separate guard.
     */
    public List<InvestigationSummaryDTO> getByAdmission(UUID admissionId, UUID hospitalId) {
        List<LabOrder> labs =
                labOrderRepository.findByAdmissionIdAndHospitalIdOrderByCreatedAtDesc(admissionId, hospitalId);
        List<RadiologyOrder> rads =
                radiologyOrderRepository.findByAdmissionIdAndHospitalIdOrderByCreatedAtDesc(admissionId, hospitalId);
        return merge(labs, rads);
    }

    public List<InvestigationSummaryDTO> getByPatient(Integer patientId, UUID hospitalId) {
        List<LabOrder> labs =
                labOrderRepository.findByPatientIdAndHospitalIdOrderByCreatedAtDesc(patientId, hospitalId);
        List<RadiologyOrder> rads =
                radiologyOrderRepository.findByPatientIdAndHospitalIdOrderByCreatedAtDesc(patientId, hospitalId);
        return merge(labs, rads);
    }

    private List<InvestigationSummaryDTO> merge(List<LabOrder> labs, List<RadiologyOrder> rads) {
        Map<Long, Invoice> labInvoiceById = batchInvoicesForLab(labs);
        Map<Long, Invoice> radInvoiceById = batchInvoicesForRadiology(rads);

        List<InvestigationSummaryDTO> out = new ArrayList<>(labs.size() + rads.size());
        for (LabOrder l : labs) out.add(toDTO(l, labInvoiceById.get(l.getId())));
        for (RadiologyOrder r : rads) out.add(toDTO(r, radInvoiceById.get(r.getId())));
        out.sort(Comparator.comparing(
                InvestigationSummaryDTO::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    private Map<Long, Invoice> batchInvoicesForLab(List<LabOrder> labs) {
        if (labs.isEmpty()) return Map.of();
        Set<Long> ids = labs.stream().map(LabOrder::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Invoice> byId = new HashMap<>();
        if (ids.isEmpty()) return byId;
        for (Object[] tuple : invoiceRepository.findInvoicesForLabOrders(ids)) {
            byId.putIfAbsent((Long) tuple[0], (Invoice) tuple[1]);
        }
        return byId;
    }

    private Map<Long, Invoice> batchInvoicesForRadiology(List<RadiologyOrder> rads) {
        if (rads.isEmpty()) return Map.of();
        Set<Long> ids = rads.stream().map(RadiologyOrder::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Invoice> byId = new HashMap<>();
        if (ids.isEmpty()) return byId;
        for (Object[] tuple : invoiceRepository.findInvoicesForRadiologyOrders(ids)) {
            byId.putIfAbsent((Long) tuple[0], (Invoice) tuple[1]);
        }
        return byId;
    }

    private InvestigationSummaryDTO toDTO(LabOrder o, Invoice inv) {
        String patientName = o.getPatient().getFirstName()
                + (o.getPatient().getLastName() != null ? " " + o.getPatient().getLastName() : "");
        InvestigationSummaryDTO.InvestigationSummaryDTOBuilder b = InvestigationSummaryDTO.builder()
                .kind("LAB")
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
                .priority(o.getPriority() != null ? o.getPriority().name() : null)
                .status(o.getStatus() != null ? o.getStatus().name() : null)
                .scheduledDate(o.getScheduledDate())
                .accessionNumber(o.getAccessionNumber())
                .sampleType(o.getSampleType())
                .collectedAt(o.getCollectedAt())
                .receivedAt(o.getReceivedAt())
                .startedAt(o.getStartedAt())
                .reportedAt(o.getReportedAt())
                .cancelledAt(o.getCancelledAt())
                .cancellationReason(o.getCancellationReason())
                .findings(o.getFindings())
                .observation(o.getObservation())
                .reportId(o.getReportId())
                .createdByName(o.getCreatedByName())
                .createdAt(o.getCreatedAt())
                .requisitionNumber(o.getRequisitionNumber());
        applyInvoice(b, inv);
        return b.build();
    }

    private InvestigationSummaryDTO toDTO(RadiologyOrder o, Invoice inv) {
        String patientName = o.getPatient().getFirstName()
                + (o.getPatient().getLastName() != null ? " " + o.getPatient().getLastName() : "");
        InvestigationSummaryDTO.InvestigationSummaryDTOBuilder b = InvestigationSummaryDTO.builder()
                .kind("RADIOLOGY")
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
                .priority(o.getPriority() != null ? o.getPriority().name() : null)
                .status(o.getStatus() != null ? o.getStatus().name() : null)
                .scheduledDate(o.getScheduledDate())
                .scannedAt(o.getScannedAt())
                .receivedAt(o.getReceivedAt())
                .startedAt(o.getStartedAt())
                .reportedAt(o.getReportedAt())
                .cancelledAt(o.getCancelledAt())
                .cancellationReason(o.getCancellationReason())
                .findings(o.getFindings())
                .observation(o.getObservation())
                .reportId(o.getReportId())
                .createdByName(o.getCreatedByName())
                .createdAt(o.getCreatedAt())
                .requisitionNumber(o.getRequisitionNumber());
        applyInvoice(b, inv);
        return b.build();
    }

    private void applyInvoice(InvestigationSummaryDTO.InvestigationSummaryDTOBuilder b, Invoice inv) {
        if (inv == null) return;
        b.invoiceStatus(inv.getStatus() != null ? inv.getStatus().name() : null)
                .invoiceNumber(inv.getInvoiceNumber())
                .invoiceId(inv.getId())
                .invoicePaid(inv.getPaidAmount())
                .invoiceTotal(inv.getTotal());
    }
}
