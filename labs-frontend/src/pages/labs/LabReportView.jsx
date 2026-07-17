import { useEffect, useMemo, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";
import { labApi, resultApi } from "@/api/labsClient";
import { fmtId } from "@/utils/idFormat";
import { ArrowLeft, Printer, Loader2, TestTube, AlertCircle, AlertOctagon, Clock } from "lucide-react";
import { fmtDateTime } from "@/utils/date";
import {
    FLAG_META,
    isPanic,
    reportableResults,
    referenceDisplay,
    valueDisplay,
} from "@/utils/resultFlags";

const PRIORITY_CLS = {
    ROUTINE: "is-routine",
    URGENT: "is-urgent",
    STAT: "is-stat",
};

/**
 * Pathology report — the document a clinician reads, and the deep-link target
 * from HMS Consultation View (/lab/reports/:id).
 *
 * This started as a mirror of RadiologyReportView. That was the wrong shape:
 * a radiology report is narrative (findings + impression), but a pathology
 * report is a results TABLE — analyte, value, unit, reference band, flag. The
 * mirrored version only rendered order.findings/observation, so per-analyte
 * results entered via PerAnalyteResultEntry were written to lab_test_result
 * and then had nowhere to appear.
 *
 * Both shapes are supported, because both exist in the data: orders with
 * per-analyte results render the table; legacy free-text orders still render
 * their narrative below it.
 *
 * Reads are best-effort and independent — a failed results fetch degrades to
 * the narrative rather than blanking the whole report.
 */
function LabReportView() {
    const { id } = useParams();
    const navigate = useNavigate();
    const { user } = useAuth();
    const [order, setOrder] = useState(null);
    const [results, setResults] = useState([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        if (!id) return;
        let cancelled = false;
        setLoading(true);
        (async () => {
            const [o, r] = await Promise.all([
                labApi.get(Number(id)).catch(() => null),
                resultApi.listForOrder(Number(id)).catch(() => []),
            ]);
            if (cancelled) return;
            setOrder(o);
            setResults(r ?? []);
            setLoading(false);
        })();
        return () => {
            cancelled = true;
        };
    }, [id]);

    // Renderable statuses only, amendments collapsed to the current value.
    const rows = useMemo(() => reportableResults(results), [results]);

    const hasPanic = useMemo(() => rows.some((r) => isPanic(r.abnormalFlag)), [rows]);
    const hasPreliminary = useMemo(
        () => rows.some((r) => r.resultStatus === "PRELIMINARY"),
        [rows]
    );

    const hasNarrative = Boolean(order?.findings || order?.observation);
    const showEmpty = rows.length === 0 && !hasNarrative;

    if (loading) {
        return (
            <div className="hms-rad-rep-loading">
                <Loader2 className="w-3 h-3 animate-spin text-gray-400" />
            </div>
        );
    }
    if (!order) {
        return (
            <div className="hms-rad-rep-notfound">
                <AlertCircle className="w-5 h-5 text-gray-300" />
                <p className="hms-rad-rep-notfound__text">Report not found.</p>
                <button onClick={() => navigate("/labs/dashboard")} className="hms-btn-secondary is-sm">
                    ← Back to Dashboard
                </button>
            </div>
        );
    }

    return (
        <div className="hms-rad-rep-view">
            <div className="hms-rad-rep-toolbar">
                <button onClick={() => navigate(-1)} className="hms-rad-rep-back-btn">
                    <ArrowLeft className="w-4 h-4" /> Back
                </button>
                <button onClick={() => window.print()} className="hms-rad-rep-print-btn">
                    <Printer className="w-4 h-4" /> Print Report
                </button>
            </div>

            <div className="hms-rad-rep-card">
                <div className="hms-rad-rep-card__hdr">
                    <div className="hms-rad-rep-card__hdr-row">
                        <div className="hms-rad-rep-card__hosp">
                            <div className="hms-rad-rep-card__hosp-icon">
                                <TestTube className="w-5 h-5 text-gray-900" />
                            </div>
                            <div>
                                <h1 className="hms-rad-rep-card__hosp-name">
                                    {user?.hospitalName ?? "Hospital"}
                                </h1>
                                <p className="hms-rad-rep-card__hosp-dept">Laboratory Department</p>
                            </div>
                        </div>
                        <div className="hms-rad-rep-card__dept">
                            <span className="hms-rad-rep-card__dept-chip">Department of Pathology</span>
                            {order.reportId && (
                                <p className="hms-rad-rep-card__report-id">
                                    Report ID:{" "}
                                    <span className="hms-rad-rep-card__report-id-strong">
                                        {fmtId(order.reportId)}
                                    </span>
                                </p>
                            )}
                        </div>
                    </div>
                </div>

                <div className="hms-rad-rep-card__pinfo">
                    <div className="hms-rad-rep-card__pinfo-grid">
                        <InfoRow label="Patient Name" value={order.patientName} />
                        <InfoRow label="Patient ID" value={fmtId(order.patientUhid)} bold />
                        <InfoRow label="Referred By" value={order.referredByName} />
                        <InfoRow label="Technician" value={order.technicianName ?? "N/A"} />
                        <InfoRow label="Sample Collected" value={fmtDateTime(order.collectedAt)} />
                        <InfoRow label="Report Date" value={fmtDateTime(order.reportedAt)} />
                    </div>
                </div>

                <div className="hms-rad-rep-card__body">
                    <div className="hms-rad-rep-inv">
                        <div className="hms-rad-rep-inv__tab">{order.serviceName}</div>
                        <div className="hms-rad-rep-inv__body">
                            {order.sampleType && (
                                <p className="hms-rad-rep-inv__bill">
                                    Sample:{" "}
                                    <span className="hms-rad-rep-inv__bill-strong">{order.sampleType}</span>
                                </p>
                            )}
                            {order.billNo && (
                                <p className="hms-rad-rep-inv__bill">
                                    Bill No:{" "}
                                    <span className="hms-rad-rep-inv__bill-strong">{order.billNo}</span>
                                </p>
                            )}
                            <span className={`hms-rad-priority ${PRIORITY_CLS[order.priority]}`}>
                                Priority: {order.priority}
                            </span>
                        </div>
                    </div>

                    {/* Critical results must be impossible to miss — banner first,
                        above the table, not buried in a row. */}
                    {hasPanic && (
                        <div className="hms-lab-rep-banner is-danger">
                            <AlertOctagon className="w-4 h-4" />
                            <div>
                                <div className="hms-lab-rep-banner__title">Critical result — immediate attention</div>
                                <div className="hms-lab-rep-banner__body">
                                    One or more analytes are outside panic limits and are flagged below.
                                </div>
                            </div>
                        </div>
                    )}
                    {hasPreliminary && (
                        <div className="hms-lab-rep-banner is-warning">
                            <Clock className="w-4 h-4" />
                            <div>
                                <div className="hms-lab-rep-banner__title">Preliminary report</div>
                                <div className="hms-lab-rep-banner__body">
                                    Some results are not yet verified and may change. Do not treat as final.
                                </div>
                            </div>
                        </div>
                    )}

                    {/* No caption on the table — the panel tab above already names
                        the investigation, and an "INVESTIGATIONS" caption stacked
                        on the "Investigation" column header just read as noise. */}
                    {rows.length > 0 && (
                        <table className="hms-lab-rep-results">
                            <thead>
                                <tr>
                                    <th>Investigation</th>
                                    <th>Result</th>
                                    <th>Reference Range</th>
                                    <th>Flag</th>
                                </tr>
                            </thead>
                            <tbody>
                                {rows.map((r) => (
                                    <ResultRow key={r.id ?? r.testCode} r={r} />
                                ))}
                            </tbody>
                        </table>
                    )}

                    {showEmpty && (
                        <div className="hms-lab-rep-empty">
                            No results have been recorded for this order yet.
                        </div>
                    )}

                    {order.findings && (
                        <div>
                            <h3 className="hms-rad-rep-h3">Findings</h3>
                            <p className="hms-rad-rep-prose">{order.findings}</p>
                        </div>
                    )}
                    {order.observation && (
                        <div>
                            <h3 className="hms-rad-rep-h3">Observation / Impression</h3>
                            <p className="hms-rad-rep-prose">{order.observation}</p>
                        </div>
                    )}

                    <div className="hms-rad-rep-sig">
                        <div className="hms-rad-rep-sig__qr">
                            <div className="hms-rad-rep-sig__qr-box">
                                <p className="hms-rad-rep-sig__qr-text">QR Code</p>
                            </div>
                        </div>
                        <div className="hms-rad-rep-sig__doc">
                            <div className="hms-rad-rep-sig__doc-line" />
                            <p className="hms-rad-rep-sig__doc-name">Lab Technician</p>
                            <p className="hms-rad-rep-sig__doc-stamp">Signature &amp; Stamp</p>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}

function ResultRow({ r }) {
    const meta = FLAG_META[r.abnormalFlag];
    const FlagIcon = meta?.icon;
    const panic = isPanic(r.abnormalFlag);
    // N is a flag, but a normal one — it shouldn't tint the row or the value.
    const abnormal = Boolean(meta) && r.abnormalFlag !== "N";
    const ref = referenceDisplay(r);

    // Provenance: what was measured and how. Quiet, but it's what makes the
    // number defensible when a clinician queries it.
    const sub = [r.loincCode && `LOINC ${r.loincCode}`, r.method].filter(Boolean).join(" · ");

    return (
        <tr className={panic ? "is-panic" : abnormal ? "is-abnormal" : ""}>
            <td>
                <div className="hms-lab-rep-results__name">{r.analyteName}</div>
                {sub && <div className="hms-lab-rep-results__sub">{sub}</div>}
            </td>
            {/* Unit rides with the value ("40 U/L") rather than owning a column:
                referenceText already carries its own unit, so a separate Unit
                column printed it twice on every row. */}
            <td>
                <span className={`hms-lab-rep-val ${abnormal ? `is-${meta.tone}` : ""}`}>
                    {valueDisplay(r)}
                </span>
                {r.unit && <span className="hms-lab-rep-unit"> {r.unit}</span>}
            </td>
            <td className={`hms-lab-rep-ref ${ref ? "" : "is-none"}`}>{ref ?? "—"}</td>
            <td>
                {meta ? (
                    <span className={`hms-analyte-flag is-${meta.tone}`}>
                        {FlagIcon && <FlagIcon className="w-3 h-3" />}
                        {meta.label}
                    </span>
                ) : (
                    <span className="hms-lab-rep-ref is-none">—</span>
                )}
            </td>
        </tr>
    );
}

function InfoRow({ label, value, bold }) {
    return (
        <div>
            <p className="hms-rad-rep-card__pinfo-label">{label}</p>
            <p className={`hms-rad-rep-card__pinfo-value ${bold ? "is-bold" : ""}`}>
                {value ?? "N/A"}
            </p>
        </div>
    );
}

export { LabReportView as default };
