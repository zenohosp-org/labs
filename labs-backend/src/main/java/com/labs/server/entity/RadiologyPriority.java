package com.labs.server.entity;

public enum RadiologyPriority {
    ROUTINE(1),
    URGENT(2),
    STAT(3);

    public final int id;

    RadiologyPriority(int id) { this.id = id; }

    public static RadiologyPriority fromId(int id) {
        for (RadiologyPriority s : values()) {
            if (s.id == id) return s;
        }
        throw new IllegalArgumentException("Unknown RadiologyPriority id: " + id);
    }
}
