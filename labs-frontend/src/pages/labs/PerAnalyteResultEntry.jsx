import { useEffect, useMemo, useState } from "react";
import { Plus, Loader2, Trash2 } from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import { useNotification } from "@/context/NotificationContext";
import { resultApi, labServiceApi, referenceRangeApi, equipmentApi } from "@/api/labsClient";
import { Alert, Button } from "@/components/ui";
import { FLAG_META } from "@/utils/resultFlags";

/** "GE Venue Fit Ultrasound (AST-0231)" — falls back gracefully if code is missing. */
function equipmentLabel(asset) {
    if (!asset) return null;
    return asset.assetCode ? `${asset.assetName} (${asset.assetCode})` : asset.assetName;
}

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
 * Build a Map<labServiceId, range> picking the best matching reference range
 * for each analyte. Today we don't carry patient sex / age on the order DTO,
 * so we default to the "adult-broad" band:
 *   1. prefer sex=ANY rows
 *   2. otherwise prefer the row that covers the typical adult window
 *      (minAge ≤ 18 and maxAge ≥ 50)
 *   3. fall back to the widest age window
 *
 * Deterministic tiebreaker: lower minAgeYears wins. When a future migration
 * adds patient demographics to LabOrderDTO this becomes a real lookup; for
 * now this gives the most useful default for the typical adult lab order.
 */
function indexRangesByService(ranges) {
    const groups = new Map();
    for (const r of ranges || []) {
        if (!r.isActive || r.labServiceId == null) continue;
        if (!groups.has(r.labServiceId)) groups.set(r.labServiceId, []);
        groups.get(r.labServiceId).push(r);
    }
    const score = (r) => {
        const isAny = r.sex === "ANY" || r.sex == null;
        const coversAdult =
            (r.minAgeYears == null || r.minAgeYears <= 18)
            && (r.maxAgeYears == null || r.maxAgeYears >= 50);
        const ageWidth = (r.maxAgeYears ?? 200) - (r.minAgeYears ?? 0);
        return (isAny ? 1000 : 0) + (coversAdult ? 100 : 0) + Math.min(ageWidth, 200);
    };
    const out = new Map();
    for (const [svcId, list] of groups) {
        list.sort((a, b) => score(b) - score(a) || (a.minAgeYears ?? 0) - (b.minAgeYears ?? 0));
        out.set(svcId, list[0]);
    }
    return out;
}

/**
 * Compute the abnormal flag for a numeric value against a reference range.
 *   LL = panic-low (below criticalLow)
 *   L  = below normal range
 *   N  = within normal range
 *   H  = above normal range
 *   HH = panic-high (above criticalHigh)
 * Returns null when value is blank / non-numeric / range missing.
 */
function computeFlag(rawValue, range) {
    if (!range) return null;
    if (rawValue === "" || rawValue == null) return null;
    const v = Number(rawValue);
    if (Number.isNaN(v)) return null;
    if (range.criticalLow != null && v < range.criticalLow) return "LL";
    if (range.criticalHigh != null && v > range.criticalHigh) return "HH";
    if (range.minValue != null && v < range.minValue) return "L";
    if (range.maxValue != null && v > range.maxValue) return "H";
    return "N";
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
 * Per-analyte result entry — the analytical surface paired with the legacy
 * findings textarea.
 *
 * Workflow (Phase 9 simplified):
 *   1. On mount we fetch existing rows + the catalogue + reference ranges
 *      in parallel, then resolve the panel for this order via FK (V14) or
 *      name match.
 *   2. The table shows existing results first, then any panel analytes
 *      that don't have a row yet (the tech just types values + hits Save).
 *      Each row carries its own reference band; the value input tints +
 *      a bold flag pill appears the moment a typed value crosses a band.
 *   3. Save drafts persists every typed value as PRELIMINARY.
 *   4. The lifecycle promotion (IN_PROGRESS → REPORT_GENERATED) happens via
 *      the queue kebab's "Mark Completed" action — no per-row sign-off
 *      ceremony lives here anymore (dropped in Phase 9).
 */
export default function PerAnalyteResultEntry({ order, onAfterChange }) {
    const { user } = useAuth();
    const { notify } = useNotification();
    const [loading, setLoading] = useState(true);
    const [rows, setRows] = useState([]);
    const [catalog, setCatalog] = useState([]);
    const [panelChildren, setPanelChildren] = useState([]);
    const [rangesByService, setRangesByService] = useState(() => new Map());
    const [draftValues, setDraftValues] = useState({}); // testCode -> string
    const [adHoc, setAdHoc] = useState([]); // [{tempId, testCode, analyteName, value}]
    const [saving, setSaving] = useState(false);
    const [equipment, setEquipment] = useState([]);
    const [selectedAssetId, setSelectedAssetId] = useState("");

    const load = async () => {
        if (!order?.id) return;
        setLoading(true);
        try {
            const data = await resultApi.listForOrder(order.id);
            setRows(data ?? []);
        } catch (err) {
            notify(err?.response?.data?.message || "Failed to load results", "error");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        load();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [order?.id]);

    // Equipment picker — fetched once; asset-manager resolves hospitalId from
    // the JWT itself. Failure is silent (proxy 502 if asset-manager is down)
    // since the picker is optional, not blocking result entry.
    useEffect(() => {
        equipmentApi.list().then(setEquipment).catch(() => setEquipment([]));
    }, []);

    useEffect(() => {
        if (!user?.hospitalId) return;
        (async () => {
            try {
                // Catalog + ranges fetched in parallel — both are per-hospital and
                // small (~50 rows each), and the per-analyte UI needs both before
                // it can render the right column.
                const [c, ranges] = await Promise.all([
                    labServiceApi.list(user.hospitalId, true),
                    referenceRangeApi.list(user.hospitalId).catch(() => []),
                ]);
                setCatalog(c ?? []);
                setRangesByService(indexRangesByService(ranges));
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
        const selectedAsset = equipment.find((e) => e.assetId === selectedAssetId);
        const instrumentId = equipmentLabel(selectedAsset) || undefined;

        const fromPanel = missingFromPanel
            .filter((p) => draftValues[p.testCode] !== undefined && draftValues[p.testCode] !== "")
            .map((p) => ({
                testCode: p.testCode,
                analyteName: p.name,
                valueNumeric: Number(draftValues[p.testCode]),
                unit: p.defaultUnit,
                method: p.defaultMethod,
                instrumentId,
            }));
        const fromAdHoc = adHoc
            .filter((a) => a.testCode && a.value !== "")
            .map((a) => ({
                testCode: a.testCode,
                analyteName: a.analyteName || a.testCode,
                valueNumeric: isNaN(Number(a.value)) ? null : Number(a.value),
                valueText: isNaN(Number(a.value)) ? a.value : null,
                instrumentId,
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
        } catch (err) {
            notify(err?.response?.data?.message || "Failed to save results", "error");
        } finally {
            setSaving(false);
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

            {equipment.length > 0 && (
                <label className="flex items-center gap-2 text-12 text-gray-600">
                    Equipment used
                    <select
                        value={selectedAssetId}
                        onChange={(e) => setSelectedAssetId(e.target.value)}
                        className="hms-analyte-input w-auto min-w-[220px]"
                    >
                        <option value="">— not specified —</option>
                        {equipment.map((a) => (
                            <option key={a.assetId} value={a.assetId}>
                                {equipmentLabel(a)}
                            </option>
                        ))}
                    </select>
                    <span className="text-11 text-gray-400">Applies to values saved below</span>
                </label>
            )}

            <div className="overflow-x-auto">
                <table className="w-full text-13 border-collapse">
                    <thead>
                        <tr className="text-left text-12 text-gray-500 border-b">
                            <th className="py-2 pr-2">Analyte</th>
                            <th className="py-2 pr-2 w-36">Value</th>
                            <th className="py-2 pr-2 w-20">Unit</th>
                            <th className="py-2 pr-2 w-28">Flag</th>
                            <th className="py-2 pr-2 w-40">Reference</th>
                            <th className="py-2 pr-2 w-24">Δ vs prev</th>
                        </tr>
                    </thead>
                    <tbody>
                        {/* Existing rows */}
                        {Array.from(existingByCode.values()).map((r) => (
                            <ResultRow key={r.id} r={r} />
                        ))}

                        {/* Panel children not yet entered */}
                        {missingFromPanel.map((p) => {
                            const range = rangesByService.get(p.id);
                            const typed = draftValues[p.testCode] ?? "";
                            const liveFlag = computeFlag(typed, range);
                            const flagMeta = liveFlag ? FLAG_META[liveFlag] : null;
                            const FlagIcon = flagMeta?.icon;
                            const refDisplay = range
                                ? (range.rangeText
                                    || `${range.minValue ?? "—"}–${range.maxValue ?? "—"}${range.unit ? " " + range.unit : ""}`)
                                : null;
                            return (
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
                                        value={typed}
                                        onChange={(e) =>
                                            setDraftValues((d) => ({ ...d, [p.testCode]: e.target.value }))
                                        }
                                        className={`hms-analyte-input ${flagMeta ? "is-" + flagMeta.tone : ""}`}
                                        placeholder="—"
                                    />
                                </td>
                                <td className="py-2 pr-2 text-12 text-gray-500">{p.defaultUnit || range?.unit || ""}</td>
                                <td className="py-2 pr-2">
                                    {flagMeta ? (
                                        <span className={`hms-analyte-flag is-${flagMeta.tone}`}>
                                            {FlagIcon && <FlagIcon className="w-3 h-3" />}
                                            {flagMeta.label}
                                        </span>
                                    ) : (
                                        <span className="text-gray-300">—</span>
                                    )}
                                </td>
                                <td className="py-2 pr-2">
                                    {refDisplay ? (
                                        <div className="hms-analyte-ref">
                                            <span className="hms-analyte-ref__band">{refDisplay}</span>
                                            {(range.criticalLow != null || range.criticalHigh != null) && (
                                                <div className="hms-analyte-ref__panic">
                                                    Panic: {range.criticalLow ?? "—"}&nbsp;/&nbsp;{range.criticalHigh ?? "—"}
                                                </div>
                                            )}
                                        </div>
                                    ) : (
                                        <span className="text-gray-300">—</span>
                                    )}
                                </td>
                                <td className="py-2 pr-2 text-gray-300">—</td>
                            </tr>
                            );
                        })}

                        {/* Ad-hoc rows for tests not in the panel */}
                        {adHoc.map((a, idx) => (
                            <tr key={a.tempId} className="border-b border-gray-100 bg-amber-50/40">
                                <td className="py-2 pr-2">
                                    <div className="hms-analyte-adhoc-name">
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
                                            className="hms-analyte-input"
                                        />
                                        <button
                                            type="button"
                                            onClick={() =>
                                                setAdHoc((arr) => arr.filter((_, i) => i !== idx))
                                            }
                                            className="hms-analyte-adhoc-remove"
                                            aria-label="Remove ad-hoc analyte"
                                            title="Remove this row"
                                        >
                                            <Trash2 size={14} />
                                        </button>
                                    </div>
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
                                        className="hms-analyte-input hms-analyte-adhoc-code"
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
                                        className="hms-analyte-input"
                                    />
                                </td>
                                <td colSpan={4} />
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
        </div>
    );
}

function ResultRow({ r }) {
    const meta = FLAG_META[r.abnormalFlag];
    const FlagIcon = meta?.icon;
    return (
        <tr className="border-b border-gray-100 hover:bg-gray-50">
            <td className="py-2 pr-2">
                <div className="font-bold text-gray-900">{r.analyteName}</div>
                {r.loincCode && <div className="text-11 text-gray-400">LOINC {r.loincCode}</div>}
                {r.instrumentId && <div className="text-11 text-gray-400">{r.instrumentId}</div>}
            </td>
            <td className="py-2 pr-2 font-mono text-gray-900">
                {r.valueNumeric ?? r.valueText ?? "—"}
            </td>
            <td className="py-2 pr-2 text-12 text-gray-500">{r.unit}</td>
            <td className="py-2 pr-2">
                {meta ? (
                    <span className={`hms-analyte-flag is-${meta.tone}`}>
                        {FlagIcon && <FlagIcon className="w-3 h-3" />}
                        {meta.label}
                    </span>
                ) : (
                    <span className="text-gray-300">—</span>
                )}
            </td>
            <td className="py-2 pr-2">
                {(r.referenceText
                    || (r.referenceLow != null && r.referenceHigh != null
                        ? `${r.referenceLow} – ${r.referenceHigh}${r.unit ? " " + r.unit : ""}`
                        : null)) ? (
                    <span className="hms-analyte-ref__band">
                        {r.referenceText
                            || `${r.referenceLow} – ${r.referenceHigh}${r.unit ? " " + r.unit : ""}`}
                    </span>
                ) : (
                    <span className="text-gray-300">—</span>
                )}
            </td>
            <td className="py-2 pr-2 font-mono text-12 text-gray-600">
                {r.deltaFromPrevious != null ? (r.deltaFromPrevious > 0 ? "+" : "") + r.deltaFromPrevious : "—"}
            </td>
        </tr>
    );
}
