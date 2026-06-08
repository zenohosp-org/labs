package com.labs.server.entity;

public enum LabPriority {
    ROUTINE(1),
    URGENT(2),
    STAT(3);

    public final int id;

    LabPriority(int id) { this.id = id; }

    public static LabPriority fromId(int id) {
        for (LabPriority p : values()) {
            if (p.id == id) return p;
        }
        throw new IllegalArgumentException("Unknown LabPriority id: " + id);
    }
}
