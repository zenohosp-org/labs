package com.labs.server.util;

import com.labs.server.entity.Hospital;

/**
 * Per-hospital ID prefix used in every generated record number. Mirrors HMS
 * {@code service.HospitalIdPrefix} so labs-generated invoice numbers slot into
 * the global numbering scheme.
 *
 * Format: "{4-digit-code}-" — e.g., "1001-".
 * Returns "" if the hospital is null or has no numericCode assigned yet.
 */
public final class HospitalIdPrefix {

    private HospitalIdPrefix() {}

    public static String of(Hospital hospital) {
        if (hospital == null) return "";
        String code = hospital.getNumericCode();
        return (code != null && !code.isBlank()) ? code + "-" : "";
    }
}
