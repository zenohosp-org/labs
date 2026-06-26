package com.labs.server.service;

import com.labs.server.dto.CumulativeResultDTO;
import com.labs.server.entity.LabOrder;
import com.labs.server.entity.LabTestResult;
import com.labs.server.entity.ResultStatus;
import com.labs.server.repository.LabOrderRepository;
import com.labs.server.repository.LabTestResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the cumulative report — for every analyte in the given order,
 * pulls the patient's prior FINAL / CORRECTED values for the same
 * test_code and returns them newest-first as a time series.
 *
 * Used by:
 *   - the per-order report viewer for inline trend charts
 *   - the PDF renderer when {@code includeCumulative=true} (Phase 5
 *     defaults to off; staff can opt in per render)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CumulativeReportService {

    private final LabTestResultRepository resultRepository;
    private final LabOrderRepository orderRepository;

    /**
     * Cumulative series keyed by test_code, including the values FROM this
     * order (so the trend includes the current point). Newest first.
     */
    public List<CumulativeResultDTO> forOrder(Long labOrderId) {
        LabOrder order = orderRepository.findById(labOrderId)
                .orElseThrow(() -> new RuntimeException("Lab order not found: " + labOrderId));
        if (order.getPatient() == null) return List.of();

        List<LabTestResult> currentRows = resultRepository.findByLabOrderIdOrderByCreatedAtAsc(labOrderId);
        if (currentRows.isEmpty()) return List.of();

        UUID hospitalId = order.getHospital().getId();
        Integer patientId = order.getPatient().getId();
        Map<String, CumulativeResultDTO> byCode = new LinkedHashMap<>();

        for (LabTestResult r : currentRows) {
            if (byCode.containsKey(r.getTestCode())) continue;

            CumulativeResultDTO dto = CumulativeResultDTO.builder()
                    .testCode(r.getTestCode())
                    .analyteName(r.getAnalyteName())
                    .loincCode(r.getLoincCode())
                    .unit(r.getUnit())
                    .referenceLow(r.getReferenceLow())
                    .referenceHigh(r.getReferenceHigh())
                    .referenceText(r.getReferenceText())
                    .points(new ArrayList<>())
                    .build();

            // current order's point first
            dto.getPoints().add(toPoint(r));

            // prior FINAL/CORRECTED from same patient + test_code (excluding this order)
            List<LabTestResult> prior = resultRepository.findPriorFinalForDelta(
                    hospitalId, patientId, r.getTestCode(), labOrderId);
            for (LabTestResult p : prior) {
                if (p.getValueNumeric() == null && (p.getValueText() == null || p.getValueText().isBlank())) continue;
                dto.getPoints().add(toPoint(p));
            }

            dto.getPoints().sort(Comparator
                    .comparing(CumulativeResultDTO.Point::getAt, Comparator.nullsLast(Comparator.reverseOrder())));

            byCode.put(r.getTestCode(), dto);
        }

        // Only return series that have more than 1 point (a single point isn't a "trend")
        List<CumulativeResultDTO> trends = new ArrayList<>();
        for (CumulativeResultDTO dto : byCode.values()) {
            if (dto.getPoints().size() >= 2) trends.add(dto);
        }
        return trends;
    }

    private CumulativeResultDTO.Point toPoint(LabTestResult r) {
        LocalDateTime at = r.getVerifiedAt() != null ? r.getVerifiedAt()
                : (r.getEnteredAt() != null ? r.getEnteredAt() : r.getCreatedAt());
        return CumulativeResultDTO.Point.builder()
                .resultId(r.getId())
                .labOrderId(r.getLabOrderId())
                .value(r.getValueNumeric())
                .abnormalFlag(r.getAbnormalFlag())
                .at(at)
                .resultStatus(r.getResultStatus() != null ? r.getResultStatus().name() : null)
                .build();
    }
}
