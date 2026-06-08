package com.labs.server.entity;

public enum LabStatus {
    PENDING_COLLECTION(1),
    AWAITING_REPORT(2),
    REPORT_GENERATED(3),
    BILLED(4);

    public final int id;

    LabStatus(int id) { this.id = id; }

    public static LabStatus fromId(int id) {
        for (LabStatus s : values()) {
            if (s.id == id) return s;
        }
        throw new IllegalArgumentException("Unknown LabStatus id: " + id);
    }
}
