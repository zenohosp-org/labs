package com.labs.server.entity;

public enum RadiologyStatus {
    PENDING_SCAN(1),
    AWAITING_REPORT(2),
    REPORT_GENERATED(3),
    BILLED(4),
    /**
     * Phase 7 — tech has started the modality run (study in progress).
     * Conceptually sits between AWAITING_REPORT and REPORT_GENERATED:
     *   AWAITING_REPORT  → /start →  IN_PROGRESS  → /report →  REPORT_GENERATED
     * Stored at id=5 so existing rows (1..4) never need renumbering.
     */
    IN_PROGRESS(5),

    /** Phase 9 — same semantics as LabStatus.CANCELLED. Terminal once set. */
    CANCELLED(6);

    public final int id;

    RadiologyStatus(int id) { this.id = id; }

    public static RadiologyStatus fromId(int id) {
        for (RadiologyStatus s : values()) {
            if (s.id == id) return s;
        }
        throw new IllegalArgumentException("Unknown RadiologyStatus id: " + id);
    }
}
