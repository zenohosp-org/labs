import { AlertOctagon, ArrowDown, ArrowUp, Check } from "lucide-react";

/**
 * Shared vocabulary for per-analyte lab results.
 *
 * These are clinical signals, not styling: "PANIC HIGH" meaning one thing on
 * the entry screen and another on the report is a patient-safety problem, not
 * a cosmetic one. So the flag meta, the panic set, and the renderable-status
 * set live here once and are imported by every surface that shows a result.
 *
 * Mirrors the backend, which is the authority:
 *   FLAG_META keys       → chk_lab_result_abnormal_flag (V11)
 *   PANIC_FLAGS          → ReportPdfService.PANIC_FLAGS
 *   RENDERABLE_STATUSES  → ReportPdfService.RENDERABLE
 * Keep them in step — if a flag is added DB-side, add it here too.
 */

/** HL7 abnormal-flag vocabulary → tone, icon, human label. */
export const FLAG_META = {
    LL: { tone: "danger", icon: AlertOctagon, label: "PANIC LOW" },
    L: { tone: "warning", icon: ArrowDown, label: "LOW" },
    N: { tone: "success", icon: Check, label: "NORMAL" },
    H: { tone: "warning", icon: ArrowUp, label: "HIGH" },
    HH: { tone: "danger", icon: AlertOctagon, label: "PANIC HIGH" },
    A: { tone: "warning", icon: AlertOctagon, label: "ABNORMAL" },
    AA: { tone: "danger", icon: AlertOctagon, label: "CRIT. ABN." },
};

/** Flags that make a result critical — drive the panic banner. */
export const PANIC_FLAGS = new Set(["LL", "HH", "AA"]);

/**
 * Statuses that belong on a report. PENDING is a draft the tech hasn't
 * committed; CANCELLED is retracted. Neither is a reportable result.
 */
export const RENDERABLE_STATUSES = new Set(["FINAL", "CORRECTED", "PRELIMINARY"]);

export const isPanic = (flag) => PANIC_FLAGS.has(flag);

/**
 * Collapse a raw result list to the one row that should be shown per analyte.
 *
 * An amendment is stored as a NEW lab_test_result row for the same test_code
 * (the original stays for audit), so a naive render shows the superseded value
 * next to the corrected one — two different answers for the same analyte on a
 * clinical document. Newest createdAt wins; ties fall back to the higher id,
 * since a later insert always carries a larger identity value.
 */
export function latestPerAnalyte(rows) {
    const byCode = new Map();
    for (const r of rows ?? []) {
        const key = r.testCode ?? r.analyteName ?? String(r.id);
        const prev = byCode.get(key);
        if (!prev) {
            byCode.set(key, r);
            continue;
        }
        const newer =
            (r.createdAt ?? "") !== (prev.createdAt ?? "")
                ? (r.createdAt ?? "") > (prev.createdAt ?? "")
                : (r.id ?? 0) > (prev.id ?? 0);
        if (newer) byCode.set(key, r);
    }
    return [...byCode.values()];
}

/** The reportable rows for an order: renderable statuses, amendments collapsed. */
export function reportableResults(rows) {
    return latestPerAnalyte(
        (rows ?? []).filter((r) => RENDERABLE_STATUSES.has(r.resultStatus))
    );
}

/**
 * Reference range as a single display string, or null when unknown.
 *
 * referenceText is the authored band and wins outright — it already carries
 * its own unit ("5 – 40 U/L") and may be non-numeric ("Negative"). Only when
 * we synthesise a band from low/high do we append the unit ourselves. Mirrors
 * ReportPdfService.referenceDisplay so screen and PDF read identically.
 */
export function referenceDisplay(r) {
    if (r.referenceText?.trim()) return r.referenceText;
    const unit = r.unit ? ` ${r.unit}` : "";
    if (r.referenceLow != null && r.referenceHigh != null) {
        return `${r.referenceLow} – ${r.referenceHigh}${unit}`;
    }
    if (r.referenceLow != null) return `≥ ${r.referenceLow}${unit}`;
    if (r.referenceHigh != null) return `≤ ${r.referenceHigh}${unit}`;
    return null;
}

/**
 * The measured value as shown to a clinician.
 *
 * value_numeric is DECIMAL(18,6) and Jackson preserves the scale, so a plain
 * render prints "202.000000" on a report handed to a patient. Normalise
 * through Number to drop insignificant zeros (202.000000 → 202, 0.500000 →
 * 0.5) while leaving genuine precision intact. Falls back to the raw value if
 * it isn't finite, so we never silently swallow an unexpected payload.
 */
export function valueDisplay(r) {
    if (r.valueNumeric != null && r.valueNumeric !== "") {
        const n = Number(r.valueNumeric);
        return Number.isFinite(n) ? String(n) : String(r.valueNumeric);
    }
    if (r.valueText?.trim()) return r.valueText;
    return "—";
}
