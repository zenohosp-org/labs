package com.labs.server.entity;

/**
 * Lifecycle of a {@link LabTestResult}.
 *
 *  PENDING       — placeholder row, no value yet (created when an order is
 *                  expanded into per-analyte slots).
 *  PRELIMINARY   — tech entered a value but hasn't verified. Visible to the
 *                  ordering doctor with a "preliminary" badge but not yet
 *                  the final report.
 *  FINAL         — tech verified. Released to the report viewer; ready for
 *                  the optional pathologist authorise step.
 *  CORRECTED     — used by amendment rows that supersede an earlier FINAL
 *                  result with a different value. The original keeps its
 *                  status; this is the new authoritative row.
 *  CANCELLED     — result was withdrawn before release (e.g. sample
 *                  rejected after entry).
 */
public enum ResultStatus {
    PENDING,
    PRELIMINARY,
    FINAL,
    CORRECTED,
    CANCELLED
}
