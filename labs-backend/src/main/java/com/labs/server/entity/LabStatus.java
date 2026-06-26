package com.labs.server.entity;

public enum LabStatus {
    PENDING_COLLECTION(1),
    AWAITING_REPORT(2),
    REPORT_GENERATED(3),
    BILLED(4),
    /**
     * Phase 7 — tech is actively running the analyser on this order.
     * Conceptually sits between AWAITING_REPORT and REPORT_GENERATED:
     *   AWAITING_REPORT  → /start →  IN_PROGRESS  → /report →  REPORT_GENERATED
     * Stored at id=5 so existing rows (which use 1..4) never need renumbering.
     */
    IN_PROGRESS(5);

    public final int id;

    LabStatus(int id) { this.id = id; }

    public static LabStatus fromId(int id) {
        for (LabStatus s : values()) {
            if (s.id == id) return s;
        }
        throw new IllegalArgumentException("Unknown LabStatus id: " + id);
    }
}
