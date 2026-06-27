import { useEffect, useMemo, useState } from "react";
import {
    Plus,
    CheckCircle2,
    ShieldCheck,
    Pencil,
    PhoneCall,
    Loader2,
    Trash2,
    ArrowDown,
    ArrowUp,
    AlertOctagon,
    Check,
} from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import { useNotification } from "@/context/NotificationContext";
import { resultApi, labServiceApi } from "@/api/labsClient";
import { Alert, Badge, Button } from "@/components/ui";
import AmendResultModal from "@/components/modals/AmendResultModal";

const STATUS_TONE = {
    PENDING: "neutral",
    PRELIMINARY: "warning",
    FINAL: "success",
    CORRECTED: "info",
    CANCELLED: "danger",
};

const FLAG_META = {
    LL: { tone: "danger", icon: AlertOctagon, label: "PANIC LOW" },
    L: { tone: "warning", icon: ArrowDown, label: "LOW" },
    N: { tone: "success", icon: Check, label: "NORMAL" },
    H: { tone: "warning", icon: ArrowUp, label: "HIGH" },
    HH: { tone: "danger", icon: AlertOctagon, label: "PANIC HIGH" },
    A: { tone: "warning", icon: AlertOctagon, label: "ABNORMAL" },
    AA: { tone: "danger", icon: AlertOctagon, label: "CRIT. ABN." },
};

/**
 * Resolve which catalogue panel the per-analyte UI should expand for this order.
 *
 * Precedence:
 *   1. If the order carries a labServiceId FK (Phase 8.1 V14), look that row up
 *      directly — exact match, no fuzzy guesswork.
 *   2. Fall back to name-match for legacy free-text orders.
 *
 * Filter: skip RADIOLOGY discipline + non-NUMERIC value-type rows. They never
 * map to a per-analyte panel; the modal will show the narrative findings UI
 * instead.
 */
function findPanel(catalog, order) {
    if (!catalog?.length || !order) return null;

    const acceptable = (c) =>
        c.isPanel
        && c.discipline !== "RADIOLOGY"
        && (c.valueType == null || c.valueType === "NUMERIC");

    // 1. FK-direct lookup (V14 catalog-linked orders).
    if (order.labServiceId) {
        const direct = catalog.find((c) => c.id === order.labServiceId);
        if (direct && acceptable(direct)) return direct;
        // If the FK resolves but the row isn't an orderable panel (e.g. it's a
        // single analyte or a radiology row), short-circuit: no panel expansion.
        if (direct) return null;
    }

    // 2. Legacy name-match for orders pre-dating V14.
    const want = order.serviceName?.trim().toLowerCase();
    if (!want) return null;
    const exact =
        catalog.find((c) => acceptable(c) && c.testCode?.toLowerCase() === want) ||
        catalog.find((c) => acceptable(c) && c.name?.toLowerCase() === want);
    if (exact) return exact;
    return (
        catalog.find((c) => acceptable(c) && want.includes(c.testCode.toLowerCase())) ||
        catalog.find((c) => acceptable(c) && c.name?.toLowerCase().includes(want)) ||
        catalog.find((c) => acceptable(c) && want.includes(c.name?.toLowerCase() ?? "")) ||
        null
    );
}

/**
 * Phase 8.1 — does this order belong on the per-analyte panel UX at all?
 * Returns false for RADIOLOGY discipline or any non-NUMERIC value type — those
 * use the narrative-findings textarea instead.
 */
function shouldUseNarrativeUx(order) {
    if (!order) return false;
    if (order.labServiceDiscipline === "RADIOLOGY") return true;
    if (order.labServiceValueType && order.labServiceValueType !== "NUMERIC") return true;
    return false;
}

/**
 * Per-analyte result entry — the new analytical surface that replaces (or
 * coexists with) the legacy findings textarea.
 *
 * Workflow:
 *   1. On mount we fetch existing rows + a best-guess panel from the
 *      hospital test catalogue based on order.serviceName ("CBC", "LFT").
 *   2. The table shows existing results first, then any panel analytes
 *      that don't have a row yet (the tech just types values + hits Save).
 *   3. Saved rows live in PRELIMINARY until the tech clicks Verify → FINAL.
 *   4. FINAL rows show an Authorise button (pathologist sign-off) and an
 *      Amend button. Amend never overwrites — it inserts a new CORRECTED
 *      row pointing at the original via amendmentOfId.
 *   5. Panic-flagged rows surface a Phone call button to record the
 *      mandatory NABL communication + acknowledgement.
 */
export default function PerAnalyteResultEntry({ order, onAfterChange }) {
    const { user } = useAuth();
    const { notify } = useNotification();
    const [loading, setLoading] = useState(true);
    const [rows, setRows] = useState([]);
    const [catalog, setCatalog] = useState([]);
    const [panelChildren, setPanelChildren] = useState([]);
    const [draftValues, setDraftValues] = useState({}); // testCode -> string
    const [adHoc, setAdHoc] = useState([]); // [{tempId, testCode, analyteName, value}]
    const [saving, setSaving] = useState(false);
    const [actingId, setActingId] = useState(null);
    const [amend, setAmend] = useState(null);

    const load = async () => {
        if (!order?.id) return;
        setLoading(true);
        try {
            const data = await resultApi.listForOrder(order.id);
            setRows(data ?? []);
        } catch {
            notify("Failed to load results", "error");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        load();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [order?.id]);

    useEffect(() => {
        if (!user?.hospitalId) return;
        (async () => {
            try {
                const c = await labServiceApi.list(user.hospitalId, true);
                setCatalog(c ?? []);
                const panel = findPanel(c ?? [], order);
                if (panel) {
                    try {
                        const kids = await labServiceApi.expandPanel(panel.testCode, user.hospitalId);
                        setPanelChildren(kids ?? []);
                    } catch {
                        setPanelChildren([]);
                    }
                } else {
                    setPanelChildren([]);
                }
            } catch {
                setCatalog([]);
            }
        })();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [user?.hospitalId, order?.serviceName]);

    // Display order: existing results (newest amendments win visually), then
    // panel children not yet entered.
    const existingByCode = useMemo(() => {
        const map = new Map();
        for (const r of rows) {
            const prev = map.get(r.testCode);
            if (!prev || (r.createdAt && r.createdAt > prev.createdAt)) {
                map.set(r.testCode, r);
            }
        }
        return map;
    }, [rows]);

    const missingFromPanel = panelChildren.filter((p) => !existingByCode.has(p.testCode));

    const saveDrafts = async () => {
        const fromPanel = missingFromPanel
            .filter((p) => draftValues[p.testCode] !== undefined && draftValues[p.testCode] !== "")
            .map((p) => ({
                testCode: p.testCode,
                analyteName: p.name,
                valueNumeric: Number(draftValues[p.testCode]),
                unit: p.defaultUnit,
                method: p.defaultMethod,
            }));
        const fromAdHoc = adHoc
            .filter((a) => a.testCode && a.value !== "")
            .map((a) => ({
                testCode: a.testCode,
                analyteName: a.analyteName || a.testCode,
                valueNumeric: isNaN(Number(a.value)) ? null : Number(a.value),
                valueText: isNaN(Number(a.value)) ? a.value : null,
            }));
        const payload = [...fromPanel, ...fromAdHoc];
        if (!payload.length) {
            notify("Enter at least one value", "error");
            return;
        }
        setSaving(true);
        try {
            await resultApi.createBulk(order.id, payload);
            notify(`Saved ${payload.length} result(s) as PRELIMINARY`, "success");
            setDraftValues({});
            setAdHoc([]);
            await load();
            onAfterChange?.();
        } catch {
            notify("Failed to save results", "error");
        } finally {
            setSaving(false);
        }
    };

    const handleVerify = async (r) => {
        setActingId(r.id);
        try {
            await resultApi.verify(r.id, {});
            notify(`${r.analyteName} verified → FINAL`, "success");
            await load();
            onAfterChange?.();
        } catch {
            notify("Verify failed", "error");
        } finally {
            setActingId(null);
        }
    };

    const handleAuthorise = async (r) => {
        setActingId(r.id);
        try {
            await resultApi.authorise(r.id, {});
            notify(`${r.analyteName} authorised by pathologist`, "success");
            await load();
            onAfterChange?.();
        } catch {
            notify("Authorise failed", "error");
        } finally {
            setActingId(null);
        }
    };

    const handlePanicCall = async (r) => {
        const calledTo = prompt(`Who did you call about ${r.analyteName} (panic ${r.abnormalFlag})?`);
        if (!calledTo) return;
        const acknowledgedBy = prompt(`Who acknowledged the call? (optional)`);
        setActingId(r.id);
        try {
            await resultApi.panicCall(r.id, { calledTo, acknowledgedBy: acknowledgedBy || null });
            notify("Panic call recorded", "success");
            await load();
            onAfterChange?.();
        } catch {
            notify("Failed to record panic call", "error");
        } finally {
            setActingId(null);
        }
    };

    if (loading) {
        return (
            <div className="flex items-center justify-center py-8">
                <Loader2 className="w-5 h-5 animate-spin text-gray-400" />
            </div>
        );
    }

    const showPanelBanner = panelChildren.length > 0;
    const useNarrativeUx = shouldUseNarrativeUx(order);
    const noCatalogMatch = !useNarrativeUx && panelChildren.length === 0 && rows.length === 0;

    if (useNarrativeUx) {
        return (
            <Alert tone="info">
                <strong>{order.serviceName}</strong> is a {order.labServiceDiscipline === "RADIOLOGY"
                    ? "radiology"
                    : "narrative"}{" "}
                investigation — per-analyte entry doesn't apply. Switch to the{" "}
                <strong>Findings text (legacy)</strong> tab to enter the report.
            </Alert>
        );
    }

    return (
        <div className="flex flex-col gap-3">
            {showPanelBanner && (
                <Alert tone="info">
                    Matched lab service <strong>{order.serviceName}</strong> →{" "}
                    <strong>{panelChildren.length}</strong> analytes. Type values and hit{" "}
                    <strong>Save drafts</strong> — each saves as <em>PRELIMINARY</em> and
                    gets auto-flagged against the reference range.
                </Alert>
            )}
            {noCatalogMatch && (
                <Alert tone="warning">
                    No matching panel found for <strong>{order.serviceName}</strong> in your
                    Lab Services catalogue. Either add analytes manually below, or curate
                    the catalogue from <strong>Settings → Lab Services</strong>.
                </Alert>
            )}

            <div className="overflow-x-auto">
                <table className="w-full text-13 border-collapse">
                    <thead>
                        <tr className="text-left text-12 text-gray-500 border-b">
                            <th className="py-2 pr-2">Analyte</th>
                            <th className="py-2 pr-2 w-32">Value</th>
                            <th className="py-2 pr-2 w-20">Unit</th>
                            <th className="py-2 pr-2 w-24">Flag</th>
                            <th className="py-2 pr-2 w-32">Reference</th>
                            <th className="py-2 pr-2 w-24">Δ vs prev</th>
                            <th className="py-2 pr-2 w-28">Status</th>
                            <th className="py-2 pr-2 text-right">Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        {/* Existing rows */}
                        {Array.from(existingByCode.values()).map((r) => (
                            <ResultRow
                                key={r.id}
                                r={r}
                                acting={actingId === r.id}
                                onVerify={handleVerify}
                                onAuthorise={handleAuthorise}
                                onAmend={setAmend}
                                onPanicCall={handlePanicCall}
                            />
                        ))}

                        {/* Panel children not yet entered */}
                        {missingFromPanel.map((p) => (
                            <tr key={p.testCode} className="border-b border-gray-100 hover:bg-gray-50">
                                <td className="py-2 pr-2">
                                    <div className="font-bold text-gray-800">{p.name}</div>
                                    {p.loincCode && (
                                        <div className="text-11 text-gray-400">LOINC {p.loincCode}</div>
                                    )}
                                </td>
                                <td className="py-2 pr-2">
                                    <input
                                        type="number"
                                        step="any"
                                        value={draftValues[p.testCode] ?? ""}
                                        onChange={(e) =>
                                            setDraftValues((d) => ({ ...d, [p.testCode]: e.target.value }))
                                        }
                                        className="w-full px-2 py-1 border border-gray-200 rounded text-13"
                                        placeholder="—"
                                    />
                                </td>
                                <td className="py-2 pr-2 text-12 text-gray-500">{p.defaultUnit}</td>
                                <td className="py-2 pr-2 text-gray-300">—</td>
                                <td className="py-2 pr-2 text-gray-300">—</td>
                                <td className="py-2 pr-2 text-gray-300">—</td>
                                <td className="py-2 pr-2">
                                    <Badge tone="neutral" soft>NOT ENTERED</Badge>
                                </td>
                                <td className="py-2 pr-2 text-right text-gray-300">—</td>
                            </tr>
                        ))}

                        {/* Ad-hoc rows for tests not in the panel */}
                        {adHoc.map((a, idx) => (
                            <tr key={a.tempId} className="border-b border-gray-100 bg-amber-50/40">
                                <td className="py-2 pr-2">
                                    <input
                                        value={a.analyteName}
                                        onChange={(e) =>
                                            setAdHoc((arr) => {
                                                const next = [...arr];
                                                next[idx] = { ...next[idx], analyteName: e.target.value };
                                                return next;
                                            })
                                        }
                                        placeholder="Analyte name"
                                        className="w-full px-2 py-1 border border-gray-200 rounded text-13"
                                    />
                                    <input
                                        value={a.testCode}
                                        onChange={(e) =>
                                            setAdHoc((arr) => {
                                                const next = [...arr];
                                                next[idx] = { ...next[idx], testCode: e.target.value };
                                                return next;
                                            })
                                        }
                                        placeholder="Test code (LOINC)"
                                        className="w-full mt-1 px-2 py-1 border border-gray-200 rounded text-12 text-gray-600 font-mono"
                                    />
                                </td>
                                <td className="py-2 pr-2">
                                    <input
                                        value={a.value}
                                        onChange={(e) =>
                                            setAdHoc((arr) => {
                                                const next = [...arr];
                                                next[idx] = { ...next[idx], value: e.target.value };
                                                return next;
                                            })
                                        }
                                        placeholder="Result value"
                                        className="w-full px-2 py-1 border border-gray-200 rounded text-13"
                                    />
                                </td>
                                <td colSpan={5} />
                                <td className="py-2 pr-2 text-right">
                                    <button
                                        type="button"
                                        onClick={() =>
                                            setAdHoc((arr) => arr.filter((_, i) => i !== idx))
                                        }
                                        className="text-gray-400 hover:text-rose-600"
                                    >
                                        <Trash2 size={14} />
                                    </button>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            <div className="flex flex-wrap items-center justify-between gap-2">
                <button
                    type="button"
                    onClick={() =>
                        setAdHoc((a) => [
                            ...a,
                            { tempId: crypto.randomUUID(), testCode: "", analyteName: "", value: "" },
                        ])
                    }
                    className="hms-rad-row__view-btn"
                >
                    <Plus size={12} /> Add ad-hoc analyte
                </button>
                <Button variant="primary" onClick={saveDrafts} disabled={saving}>
                    {saving ? "Saving…" : "Save drafts (PRELIMINARY)"}
                </Button>
            </div>

            {amend && (
                <AmendResultModal
                    result={amend}
                    onClose={() => setAmend(null)}
                    onAmended={() => {
                        setAmend(null);
                        load();
                        onAfterChange?.();
                    }}
                />
            )}
        </div>
    );
}

function ResultRow({ r, acting, onVerify, onAuthorise, onAmend, onPanicCall }) {
    const meta = FLAG_META[r.abnormalFlag];
    const FlagIcon = meta?.icon;
    return (
        <tr className="border-b border-gray-100 hover:bg-gray-50">
            <td className="py-2 pr-2">
                <div className="font-bold text-gray-900">{r.analyteName}</div>
                {r.loincCode && <div className="text-11 text-gray-400">LOINC {r.loincCode}</div>}
                {r.amendmentOfId && (
                    <div className="text-11 text-amber-700 inline-flex items-center gap-1">
                        <Pencil size={10} /> Amends #{r.amendmentOfId}
                    </div>
                )}
            </td>
            <td className="py-2 pr-2 font-mono text-gray-900">
                {r.valueNumeric ?? r.valueText ?? "—"}
            </td>
            <td className="py-2 pr-2 text-12 text-gray-500">{r.unit}</td>
            <td className="py-2 pr-2">
                {meta ? (
                    <Badge tone={meta.tone} soft>
                        <FlagIcon className="w-3 h-3 inline mr-1" />
                        {meta.label}
                    </Badge>
                ) : (
                    <span className="text-gray-300">—</span>
                )}
            </td>
            <td className="py-2 pr-2 text-12 text-gray-600">
                {r.referenceText ||
                    (r.referenceLow != null && r.referenceHigh != null
                        ? `${r.referenceLow} – ${r.referenceHigh}`
                        : "—")}
            </td>
            <td className="py-2 pr-2 font-mono text-12 text-gray-600">
                {r.deltaFromPrevious != null ? (r.deltaFromPrevious > 0 ? "+" : "") + r.deltaFromPrevious : "—"}
            </td>
            <td className="py-2 pr-2">
                <Badge tone={STATUS_TONE[r.resultStatus] ?? "neutral"} soft>
                    {r.resultStatus}
                </Badge>
                {r.authorisedAt && (
                    <div className="text-11 text-emerald-700 inline-flex items-center gap-1 mt-0.5">
                        <ShieldCheck size={10} /> Authorised
                    </div>
                )}
            </td>
            <td className="py-2 pr-2 text-right">
                <div className="inline-flex gap-1 flex-wrap justify-end">
                    {r.resultStatus === "PRELIMINARY" && (
                        <Button
                            size="sm"
                            variant="primary"
                            onClick={() => onVerify(r)}
                            disabled={acting}
                        >
                            <CheckCircle2 size={12} /> Verify
                        </Button>
                    )}
                    {(r.resultStatus === "FINAL" || r.resultStatus === "CORRECTED") &&
                        !r.authorisedAt && (
                            <Button
                                size="sm"
                                variant="secondary"
                                onClick={() => onAuthorise(r)}
                                disabled={acting}
                            >
                                <ShieldCheck size={12} /> Authorise
                            </Button>
                        )}
                    {(r.resultStatus === "FINAL" || r.resultStatus === "CORRECTED") && (
                        <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => onAmend(r)}
                            disabled={acting}
                        >
                            <Pencil size={12} /> Amend
                        </Button>
                    )}
                    {r.panicFlag && !r.panicCalledAt && (
                        <Button
                            size="sm"
                            variant="danger"
                            onClick={() => onPanicCall(r)}
                            disabled={acting}
                        >
                            <PhoneCall size={12} /> Panic call
                        </Button>
                    )}
                </div>
            </td>
        </tr>
    );
}
