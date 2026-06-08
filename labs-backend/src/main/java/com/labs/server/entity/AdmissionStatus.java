package com.labs.server.entity;

/** Mirror of HMS AdmissionStatus — labs reads this when deciding IPD vs OPD billing routing. */
public enum AdmissionStatus {
    ADMITTED(1),
    DISCHARGED(2),
    TRANSFERRED(3),
    ABSCONDED(4);

    public final int id;

    AdmissionStatus(int id) { this.id = id; }

    public static AdmissionStatus fromId(int id) {
        for (AdmissionStatus s : values()) {
            if (s.id == id) return s;
        }
        throw new IllegalArgumentException("Unknown AdmissionStatus id: " + id);
    }
}
