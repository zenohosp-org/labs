import { useState, useEffect } from "react";
import { useNotification } from "@/context/NotificationContext";
import { labApi, referenceRangeApi } from "@/api/labsClient";
import { X, FileText, Activity, Plus, ArrowDown, ArrowUp, Check } from "lucide-react";

const FLAG_META = {
    LOW: { cls: "is-amber", icon: ArrowDown, label: "LOW" },
    HIGH: { cls: "is-rose", icon: ArrowUp, label: "HIGH" },
    NORMAL: { cls: "is-emerald", icon: Check, label: "NORMAL" },
};

/**
 * Pathology counterpart of the radiology WriteReportModal. Identical
 * UX — free-text findings + observation with a "Quick Value Match" panel
 * on top that auto-flags a measured value against the configured
 * reference band. Only the API target differs (labApi.generateReport).
 */
function LabWriteReportModal({ order, onClose, onSaved }) {
    const { notify } = useNotification();
    const [findings, setFindings] = useState("");
    const [observation, setObservation] = useState("");
    const [saving, setSaving] = useState(false);

    const [matchTest, setMatchTest] = useState(order?.serviceName ?? "");
    const [matchSex, setMatchSex] = useState("ANY");
    const [matchAge, setMatchAge] = useState("");
    const [matchValue, setMatchValue] = useState("");
    const [matchResult, setMatchResult] = useState(null);
    const [matching, setMatching] = useState(false);

    useEffect(() => {
        if (!matchTest.trim() || matchValue === "") {
            setMatchResult(null);
            return;
        }
        const handle = setTimeout(async () => {
            setMatching(true);
            try {
                const res = await referenceRangeApi.match({
                    testName: matchTest.trim(),
                    sex: matchSex,
                    ageYears: matchAge === "" ? 30 : Number(matchAge),
                    value: matchValue,
                });
                setMatchResult(res ?? { _empty: true });
            } catch {
                setMatchResult({ _error: true });
            } finally {
                setMatching(false);
            }
        }, 400);
        return () => clearTimeout(handle);
    }, [matchTest, matchSex, matchAge, matchValue]);

    const insertMatchLine = () => {
        if (!matchResult || matchResult._empty || matchResult._error) return;
        const flag = matchResult.flag ? ` [${matchResult.flag}` : "";
        const range = matchResult.rangeText ? ` — normal ${matchResult.rangeText}` : "";
        const closer = matchResult.flag ? "]" : "";
        const line = `${matchTest}: ${matchValue}${matchResult.unit ? " " + matchResult.unit : ""}${flag}${range}${closer}\n`;
        setFindings((prev) => (prev ? `${prev}${prev.endsWith("\n") ? "" : "\n"}${line}` : line));
        setMatchValue("");
        setMatchResult(null);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!findings.trim()) {
            notify("Findings are required", "error");
            return;
        }
        setSaving(true);
        try {
            await labApi.generateReport(order.id, findings, observation);
            notify("Report generated", "success");
            onSaved();
        } catch {
            notify("Failed to generate report", "error");
        } finally {
            setSaving(false);
        }
    };

    const flag = matchResult?.flag;
    const flagMeta = flag ? FLAG_META[flag] : null;

    return (
        <div className="hms-rad-modal-overlay">
            <div className="hms-rad-modal">
                <div className="hms-rad-modal__hdr">
                    <div className="hms-rad-modal__hdr-left">
                        <div>
                            <h2 className="hms-rad-modal__title">
                                <FileText className="w-4 h-4" /> Write Report
                            </h2>
                            <p className="hms-rad-modal__sub">
                                {order.patientName} · {order.serviceName}
                            </p>
                        </div>
                    </div>
                    <button onClick={onClose} className="hms-rad-modal__close">
                        <X className="w-4 h-4" />
                    </button>
                </div>

                <form onSubmit={handleSubmit} className="hms-rad-modal__body">
                    <div className="hms-rad-info-bar is-soft">
                        <Activity className="w-4 h-4 hms-rad-info-bar__icon" />
                        <div className="flex flex-col gap-2 w-full">
                            <span className="text-12 text-gray-600">
                                Enter a measured value to auto-match the configured reference band
                                — flagged LOW / NORMAL / HIGH. Catalogue edits live under Settings → Reference Ranges.
                            </span>

                            <div className="hms-rad-grid">
                                <div>
                                    <label className="hms-rad-label">Test</label>
                                    <input
                                        className="hms-rad-input"
                                        value={matchTest}
                                        onChange={(e) => setMatchTest(e.target.value)}
                                        placeholder="e.g. Hemoglobin"
                                    />
                                </div>
                                <div>
                                    <label className="hms-rad-label">Value</label>
                                    <input
                                        type="number"
                                        step="any"
                                        className="hms-rad-input"
                                        value={matchValue}
                                        onChange={(e) => setMatchValue(e.target.value)}
                                        placeholder="e.g. 10.2"
                                    />
                                </div>
                            </div>
                            <div className="hms-rad-grid">
                                <div>
                                    <label className="hms-rad-label">Sex</label>
                                    <select
                                        className="hms-rad-input"
                                        value={matchSex}
                                        onChange={(e) => setMatchSex(e.target.value)}
                                    >
                                        <option value="ANY">Any</option>
                                        <option value="MALE">Male</option>
                                        <option value="FEMALE">Female</option>
                                    </select>
                                </div>
                                <div>
                                    <label className="hms-rad-label">Age (years)</label>
                                    <input
                                        type="number"
                                        min="0"
                                        className="hms-rad-input"
                                        value={matchAge}
                                        onChange={(e) => setMatchAge(e.target.value)}
                                        placeholder="default 30"
                                    />
                                </div>
                            </div>

                            {matching && (
                                <span className="text-12 text-gray-500">Looking up band…</span>
                            )}
                            {matchResult && !matching && matchResult._empty && (
                                <span className="text-12 text-gray-500">
                                    No band configured for this test / age / sex. Add one from
                                    Settings → Reference Ranges to enable auto-flagging.
                                </span>
                            )}
                            {matchResult && !matching && matchResult._error && (
                                <span className="text-12 text-rose-600">
                                    Couldn't look up the band. Try again.
                                </span>
                            )}
                            {matchResult && !matching && !matchResult._empty && !matchResult._error && (
                                <div className="flex items-center gap-2 flex-wrap">
                                    {flagMeta && (
                                        <span className={`hms-rad-chip ${flagMeta.cls}`}>
                                            <flagMeta.icon className="w-3 h-3" /> {flagMeta.label}
                                        </span>
                                    )}
                                    <span className="text-13 text-gray-700">
                                        Normal: <strong>{matchResult.rangeText}</strong>
                                    </span>
                                    <button
                                        type="button"
                                        onClick={insertMatchLine}
                                        className="hms-rad-row__view-btn"
                                    >
                                        <Plus className="w-3 h-3" /> Insert into Findings
                                    </button>
                                </div>
                            )}
                        </div>
                    </div>

                    <div>
                        <label className="hms-rad-label-small">Findings *</label>
                        <textarea
                            rows={6}
                            className="hms-rad-textarea"
                            placeholder="Enter findings — values, ranges, observations…"
                            value={findings}
                            onChange={(e) => setFindings(e.target.value)}
                            autoFocus
                        />
                    </div>
                    <div>
                        <label className="hms-rad-label-small">Observation / Impression</label>
                        <textarea
                            rows={3}
                            className="hms-rad-textarea"
                            placeholder="e.g. Within normal limits…"
                            value={observation}
                            onChange={(e) => setObservation(e.target.value)}
                        />
                    </div>
                    <div className="hms-rad-modal__foot">
                        <button type="button" onClick={onClose} className="hms-btn-secondary">
                            Cancel
                        </button>
                        <button type="submit" disabled={saving} className="hms-btn-primary">
                            {saving ? "Generating…" : "Generate Report"}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}

export { LabWriteReportModal as default };
