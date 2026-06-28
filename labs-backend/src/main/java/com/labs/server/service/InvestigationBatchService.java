package com.labs.server.service;

import com.labs.server.dto.BatchInvestigationRequest;
import com.labs.server.dto.BatchInvestigationResponse;
import com.labs.server.dto.CreateLabOrderRequest;
import com.labs.server.dto.CreateRadiologyOrderRequest;
import com.labs.server.entity.Hospital;
import com.labs.server.entity.IdempotencyKey;
import com.labs.server.entity.LabOrder;
import com.labs.server.entity.LabService;
import com.labs.server.entity.RadiologyOrder;
import com.labs.server.repository.HospitalRepository;
import com.labs.server.repository.IdempotencyKeyRepository;
import com.labs.server.repository.LabOrderRepository;
import com.labs.server.repository.LabServiceRepository;
import com.labs.server.repository.RadiologyOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Phase 10 — orchestrates POST /api/investigations/batch.
 *
 * Algorithm:
 *   1. If Idempotency-Key was supplied and we have a cached response for
 *      (hospitalId, key) → return it. HMS retries hit this path and never
 *      spawn duplicates.
 *   2. Otherwise: allocate ONE requisition_number, route each test by its
 *      catalog discipline, create the order via the relevant single-order
 *      service (LabService.createOrder / RadiologyService.createOrder),
 *      stamp the requisition_number on the saved row.
 *   3. Everything runs in one @Transactional — if any test fails validation
 *      the whole batch rolls back. No partial requisitions.
 *   4. On success, persist the (hospital, key) → (requisition, ids) mapping
 *      so step 1 short-circuits future retries.
 *
 * Hard boundary: this service NEVER touches invoice_items. Per-order
 * billing happens unchanged in LabBillingService when the report finalises.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvestigationBatchService {

    private static final Set<String> LAB_DISCIPLINES = new HashSet<>(Arrays.asList(
            "PATHOLOGY", "CYTOLOGY", "HISTOPATHOLOGY", "MICROBIOLOGY", "IMMUNOLOGY"));
    private static final String RADIOLOGY_DISCIPLINE = "RADIOLOGY";

    private final HospitalRepository hospitalRepository;
    private final LabServiceRepository labServiceRepository;
    private final LabOrderRepository labOrderRepository;
    private final RadiologyOrderRepository radiologyOrderRepository;
    private final IdempotencyKeyRepository idempotencyRepository;
    private final RequisitionNumberService requisitionNumberService;
    private final com.labs.server.service.LabService labService;
    private final RadiologyService radiologyService;

    @Transactional
    public BatchInvestigationResponse create(BatchInvestigationRequest req,
                                              String idempotencyKey,
                                              String createdByName) {
        validateRequest(req);

        // Step 1 — idempotency short-circuit.
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotencyKey> cached = idempotencyRepository
                    .findByHospitalIdAndIdempotencyKey(req.getHospitalId(), idempotencyKey);
            if (cached.isPresent()) {
                IdempotencyKey hit = cached.get();
                log.info("Idempotent retry — returning cached requisition {} for key {}",
                        hit.getRequisitionNumber(), idempotencyKey);
                return BatchInvestigationResponse.builder()
                        .requisitionNumber(hit.getRequisitionNumber())
                        .labOrderIds(toList(hit.getLabOrderIds()))
                        .radiologyOrderIds(toList(hit.getRadiologyOrderIds()))
                        .idempotent(true)
                        .build();
            }
        }

        // Step 2 — allocate requisition + route each test. The allocation runs
        // in REQUIRES_NEW so the sequence advances even if this batch rolls
        // back (we never want to reuse a requisition number).
        Hospital hospital = hospitalRepository.findById(req.getHospitalId())
                .orElseThrow(() -> new RuntimeException("Hospital not found: " + req.getHospitalId()));
        String requisitionNumber = requisitionNumberService.allocate(hospital);

        List<Long> labIds = new ArrayList<>();
        List<Long> radIds = new ArrayList<>();

        for (int i = 0; i < req.getTests().size(); i++) {
            BatchInvestigationRequest.BatchTest t = req.getTests().get(i);
            if (t.getLabServiceId() == null) {
                throw new RuntimeException("tests[" + i + "].labServiceId is required");
            }
            LabService catalog = labServiceRepository.findById(t.getLabServiceId())
                    .orElseThrow(() -> new RuntimeException(
                            "tests[" + req.getTests().indexOf(t) + "] lab service not found: "
                                    + t.getLabServiceId()));
            if (!req.getHospitalId().equals(catalog.getHospitalId())) {
                throw new RuntimeException("tests[" + i + "] lab service " + t.getLabServiceId()
                        + " does not belong to hospital " + req.getHospitalId());
            }
            String disc = catalog.getDiscipline();

            if (RADIOLOGY_DISCIPLINE.equals(disc)) {
                CreateRadiologyOrderRequest single = new CreateRadiologyOrderRequest();
                single.setHospitalId(req.getHospitalId());
                single.setPatientId(req.getPatientId());
                single.setAdmissionId(req.getAdmissionId());
                single.setPriority(req.getPriority());
                single.setScheduledDate(t.getScheduledDate());
                single.setServiceName(t.getServiceName());
                single.setSpecializationName(t.getSpecializationName());
                single.setPrice(t.getPrice());
                single.setGstRate(t.getGstRate());
                single.setLabServiceId(t.getLabServiceId());
                Long radId = radiologyService.createOrder(single, createdByName).getId();
                // Stamp the requisition_number atomically — still inside our @Transactional.
                RadiologyOrder row = radiologyOrderRepository.findById(radId)
                        .orElseThrow(() -> new RuntimeException("Created radiology order vanished: " + radId));
                row.setRequisitionNumber(requisitionNumber);
                radiologyOrderRepository.save(row);
                radIds.add(radId);
            } else if (LAB_DISCIPLINES.contains(disc)) {
                CreateLabOrderRequest single = new CreateLabOrderRequest();
                single.setHospitalId(req.getHospitalId());
                single.setPatientId(req.getPatientId());
                single.setAdmissionId(req.getAdmissionId());
                single.setPriority(req.getPriority());
                single.setScheduledDate(t.getScheduledDate());
                single.setServiceName(t.getServiceName());
                single.setSpecializationName(t.getSpecializationName());
                single.setSampleType(t.getSampleType());
                single.setPrice(t.getPrice());
                single.setGstRate(t.getGstRate());
                single.setLabServiceId(t.getLabServiceId());
                Long labId = labService.createOrder(single, createdByName).getId();
                LabOrder row = labOrderRepository.findById(labId)
                        .orElseThrow(() -> new RuntimeException("Created lab order vanished: " + labId));
                row.setRequisitionNumber(requisitionNumber);
                labOrderRepository.save(row);
                labIds.add(labId);
            } else {
                throw new RuntimeException("tests[" + i + "] lab service " + t.getLabServiceId()
                        + " has unsupported discipline=" + disc);
            }
        }

        // Step 4 — record the dedupe entry. UNIQUE PK on (hospital_id,
        // idempotency_key) means a concurrent retry hitting the catch below
        // also short-circuits to the cached row.
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                idempotencyRepository.save(IdempotencyKey.builder()
                        .hospitalId(req.getHospitalId())
                        .idempotencyKey(idempotencyKey)
                        .requisitionNumber(requisitionNumber)
                        .labOrderIds(labIds.toArray(new Long[0]))
                        .radiologyOrderIds(radIds.toArray(new Long[0]))
                        .build());
            } catch (DataIntegrityViolationException race) {
                // Concurrent retry won the PK race — that's fine, our orders
                // already landed with the requisition_number. Returning our
                // requisition number here is correct; the racing call will
                // return THEIR cached row on its next read.
                log.warn("Idempotency PK race for {} — concurrent retry; both responses valid", idempotencyKey);
            }
        }

        return BatchInvestigationResponse.builder()
                .requisitionNumber(requisitionNumber)
                .labOrderIds(labIds)
                .radiologyOrderIds(radIds)
                .idempotent(false)
                .build();
    }

    private void validateRequest(BatchInvestigationRequest req) {
        if (req == null) throw new RuntimeException("request body required");
        if (req.getHospitalId() == null) throw new RuntimeException("hospitalId required");
        if (req.getPatientId() == null) throw new RuntimeException("patientId required");
        if (req.getTests() == null || req.getTests().isEmpty())
            throw new RuntimeException("tests[] must include at least one test");
        if (req.getTests().size() > 50)
            throw new RuntimeException("Batch too large — max 50 tests per requisition");
    }

    private static List<Long> toList(Long[] arr) {
        if (arr == null) return List.of();
        return Arrays.asList(arr);
    }
}
