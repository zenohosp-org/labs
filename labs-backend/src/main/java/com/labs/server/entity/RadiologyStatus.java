package com.labs.server.entity;

public enum RadiologyStatus {
    PENDING_SCAN(1),
    AWAITING_REPORT(2),
    REPORT_GENERATED(3),
    BILLED(4);

    public final int id;

    RadiologyStatus(int id) { this.id = id; }

    public static RadiologyStatus fromId(int id) {
        for (RadiologyStatus s : values()) {
            if (s.id == id) return s;
        }
        throw new IllegalArgumentException("Unknown RadiologyStatus id: " + id);
    }
}
