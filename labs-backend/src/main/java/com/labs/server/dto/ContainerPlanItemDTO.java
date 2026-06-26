package com.labs.server.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * A single physical tube the phlebotomist should draw. Aggregated from
 * multiple orders that share the same container type. The bulk-collect
 * action creates ONE LabSpecimen row per plan item.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContainerPlanItemDTO {
    /** EDTA | CITRATE | HEPARIN | PLAIN | FLUORIDE | URINE_CUP | STOOL_CUP | SWAB | OTHER. */
    private String containerType;
    /** Optional — set when implied additive differs from container type. */
    private String additive;
    /** Aggregated min volume across orders sharing this tube (mL). */
    private BigDecimal volumeMl;
    /** Whether ANY order in this tube requires fasting. */
    private Boolean fastingRequired;
    /** OrderIds this tube serves. */
    private List<Long> servesOrderIds;
    /** Human-readable list of test names for the phlebotomist's reference. */
    private List<String> servesTestNames;
}
