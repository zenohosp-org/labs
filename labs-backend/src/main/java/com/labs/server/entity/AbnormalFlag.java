package com.labs.server.entity;

/**
 * HL7 v2 OBX-8 abnormal-flag vocabulary.
 *
 *  N   — Normal (within reference range).
 *  L   — Below reference low.
 *  H   — Above reference high.
 *  LL  — Panic low — crossed critical_low band, triggers panic-call workflow.
 *  HH  — Panic high — crossed critical_high band, triggers panic-call workflow.
 *  A   — Abnormal (used for non-numeric / qualitative tests, e.g. "positive").
 *  AA  — Critically abnormal qualitative result.
 */
public enum AbnormalFlag {
    N, L, H, LL, HH, A, AA;

    /** Whether a flag warrants a panic call. */
    public boolean isCritical() {
        return this == LL || this == HH || this == AA;
    }
}
