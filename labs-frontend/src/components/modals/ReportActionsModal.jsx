import { useEffect, useState } from "react";
import {
    FileSignature,
    Download,
    ShieldCheck,
    AlertTriangle,
    Loader2,
    LineChart,
    Copy,
    Link as LinkIcon,
    XCircle,
} from "lucide-react";
import { useNotification } from "@/context/NotificationContext";
import { reportPdfApi } from "@/api/labsClient";
import {
    Alert,
    Badge,
    Button,
    FormGroup,
    Input,
    Modal,
} from "@/components/ui";

/**
 * Report actions panel for a single lab order.
 *
 *   1. Sign — mint a new ReportPdf row (version = max+1). Optional toggle
 *      to include the cumulative trend in the rendered PDF.
 *   2. Download — open the latest signed PDF (inline render in browser).
 *   3. Versions — every signed render with version, signedAt, verify URL,
 *      revoke / re-download buttons.
 *   4. Cumulative trend — per-analyte time-series for the patient.
 *   5. Public verify URL — copy-to-clipboard for sharing with the patient.
 */
export default function ReportActionsModal({ order, onClose }) {
    const { notify } = useNotification();
    const [versions, setVersions] = useState([]);
    const [loading, setLoading] = useState(true);
    const [signing, setSigning] = useState(false);
    const [includeCumulative, setIncludeCumulative] = useState(false);
    const [cumulative, setCumulative] = useState([]);
    const [cumulativeLoading, setCumulativeLoading] = useState(false);
    const [revoking, setRevoking] = useState(null);

    const load = async () => {
        if (!order?.id) return;
        setLoading(true);
        try {
            const v = await reportPdfApi.versions(order.id);
            setVersions(v ?? []);
        } catch {
            notify("Failed to load report versions", "error");
        } finally {
            setLoading(false);
        }
    };

    const loadCumulative = async () => {
        if (!order?.id) return;
        setCumulativeLoading(true);
        try {
            const c = await reportPdfApi.cumulative(order.id);
            setCumulative(c ?? []);
        } catch {
            setCumulative([]);
        } finally {
            setCumulativeLoading(false);
        }
    };

    useEffect(() => {
        load();
        loadCumulative();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [order?.id]);

    const handleSign = async () => {
        setSigning(true);
        try {
            await reportPdfApi.sign(order.id, { cumulative: includeCumulative });
            notify("Report signed — new version ready to download", "success");
            await load();
        } catch {
            notify("Sign failed", "error");
        } finally {
            setSigning(false);
        }
    };

    const handleDownloadLatest = () => {
        window.open(reportPdfApi.latestPdfUrl(order.id), "_blank");
    };

    const handleRevoke = async (pdf) => {
        const reason = prompt("Reason for revoking this report version?");
        if (!reason) return;
        setRevoking(pdf.id);
        try {
            await reportPdfApi.revoke(pdf.id, reason);
            notify("Report version revoked", "success");
            await load();
        } catch {
            notify("Revoke failed", "error");
        } finally {
            setRevoking(null);
        }
    };

    const copyVerifyUrl = (url) => {
        navigator.clipboard?.writeText(url);
        notify("Verify URL copied", "info");
    };

    const latest = versions[0];

    return (
        <Modal
            isOpen
            onClose={onClose}
            size="xl"
            title={
                <span className="inline-flex items-center gap-2">
                    <FileSignature size={18} /> Report · {order.patientName}
                    {order.accessionNumber && (
                        <Badge tone="info" soft>{order.accessionNumber}</Badge>
                    )}
                </span>
            }
            footer={
                <>
                    <Button variant="cancel" onClick={onClose}>Close</Button>
                    {latest && (
                        <Button variant="secondary" onClick={handleDownloadLatest}>
                            <Download size={14} /> Download latest
                        </Button>
                    )}
                    <Button variant="primary" onClick={handleSign} disabled={signing}>
                        {signing ? "Signing…" : latest
                            ? `Sign new version (v${(latest.version ?? 0) + 1})`
                            : "Sign first report"}
                    </Button>
                </>
            }
        >
            <Alert tone="info">
                Signing creates a new immutable version. The PDF is rendered fresh
                on every download so amendments to underlying results show up
                automatically. Each version carries a unique QR / verify URL.
            </Alert>

            <div className="flex items-center gap-3 mb-3">
                <label className="inline-flex items-center gap-2 text-13 text-gray-700">
                    <input
                        type="checkbox"
                        checked={includeCumulative}
                        onChange={(e) => setIncludeCumulative(e.target.checked)}
                    />
                    Include cumulative trend in the PDF
                </label>
                {cumulative.length > 0 && (
                    <Badge tone="success" soft>
                        <LineChart size={11} /> {cumulative.length} trending analyte(s)
                    </Badge>
                )}
            </div>

            {loading ? (
                <div className="flex justify-center py-6">
                    <Loader2 className="w-5 h-5 animate-spin text-gray-400" />
                </div>
            ) : versions.length === 0 ? (
                <div className="text-center py-6 text-13 text-gray-500">
                    No report signed yet. Hit <strong>Sign first report</strong> when results
                    are ready.
                </div>
            ) : (
                <div className="flex flex-col gap-2">
                    {versions.map((v) => (
                        <VersionRow
                            key={v.id}
                            v={v}
                            revoking={revoking === v.id}
                            onDownload={() => window.open(reportPdfApi.versionPdfUrl(v.id), "_blank")}
                            onRevoke={() => handleRevoke(v)}
                            onCopyUrl={() => copyVerifyUrl(v.verifyUrl)}
                        />
                    ))}
                </div>
            )}

            {cumulative.length > 0 && (
                <div className="mt-4">
                    <div className="text-12 font-bold text-gray-700 uppercase tracking-wide mb-2">
                        Cumulative trend
                    </div>
                    {cumulativeLoading ? (
                        <Loader2 className="w-4 h-4 animate-spin text-gray-400" />
                    ) : (
                        <div className="flex flex-col gap-3">
                            {cumulative.map((series) => (
                                <TrendBlock key={series.testCode} series={series} />
                            ))}
                        </div>
                    )}
                </div>
            )}
        </Modal>
    );
}

function VersionRow({ v, revoking, onDownload, onRevoke, onCopyUrl }) {
    const when = v.signedAt ? new Date(v.signedAt).toLocaleString() : "—";
    return (
        <div className="border border-gray-200 rounded-lg p-3 flex flex-col gap-2 bg-white">
            <div className="flex items-center justify-between gap-3 flex-wrap">
                <div className="flex items-center gap-2 flex-wrap">
                    <Badge tone="info" soft>v{v.version}</Badge>
                    {v.revoked ? (
                        <Badge tone="danger" soft>
                            <XCircle size={11} className="inline mr-1" /> REVOKED
                        </Badge>
                    ) : (
                        <Badge tone="success" soft>
                            <ShieldCheck size={11} className="inline mr-1" /> Signed
                        </Badge>
                    )}
                    {v.cumulativeIncluded && (
                        <Badge tone="neutral" soft>
                            <LineChart size={11} className="inline mr-1" /> +trend
                        </Badge>
                    )}
                    <span className="text-12 text-gray-700">
                        by <strong>{v.signedByName || "—"}</strong> · {when}
                    </span>
                </div>
                <div className="inline-flex gap-1.5">
                    <Button variant="secondary" size="sm" onClick={onDownload}>
                        <Download size={12} /> PDF
                    </Button>
                    <Button variant="ghost" size="sm" onClick={onCopyUrl}>
                        <LinkIcon size={12} /> Copy verify URL
                    </Button>
                    {!v.revoked && (
                        <Button
                            variant="danger"
                            size="sm"
                            onClick={onRevoke}
                            disabled={revoking}
                        >
                            <XCircle size={12} /> {revoking ? "Revoking…" : "Revoke"}
                        </Button>
                    )}
                </div>
            </div>
            <div className="text-11 text-gray-500 break-all">
                <code>{v.verifyUrl}</code>
            </div>
            {v.revoked && v.revokedReason && (
                <div className="text-12 text-rose-700">
                    <AlertTriangle size={12} className="inline mr-1" />
                    Revoked: {v.revokedReason}
                </div>
            )}
        </div>
    );
}

function TrendBlock({ series }) {
    return (
        <div className="border border-gray-200 rounded p-3 bg-gray-50/50">
            <div className="font-bold text-13 text-gray-900 mb-1">
                {series.analyteName}
                {series.unit && <span className="text-gray-500"> ({series.unit})</span>}
                {series.loincCode && (
                    <span className="text-11 text-gray-400 ml-2">LOINC {series.loincCode}</span>
                )}
            </div>
            {series.referenceText && (
                <div className="text-11 text-gray-500 mb-2">Ref: {series.referenceText}</div>
            )}
            <Sparkline points={series.points} />
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-1 mt-2 text-11">
                {series.points.map((p) => (
                    <div key={p.resultId} className="text-gray-700">
                        {p.at ? new Date(p.at).toLocaleDateString() : "—"}:{" "}
                        <strong>{p.value ?? "—"}</strong>
                        {p.abnormalFlag && (
                            <span className="text-rose-600 ml-1">[{p.abnormalFlag}]</span>
                        )}
                    </div>
                ))}
            </div>
        </div>
    );
}

function Sparkline({ points }) {
    if (!points || points.length < 2) return null;
    const nums = points
        .map((p) => (p.value == null ? null : Number(p.value)))
        .filter((n) => n != null && !Number.isNaN(n));
    if (nums.length < 2) return null;
    const w = 240, h = 32, pad = 2;
    const min = Math.min(...nums);
    const max = Math.max(...nums);
    const span = max - min || 1;
    // Newest is points[0] (server returns newest-first). Reverse for left-to-right time.
    const ordered = [...nums].reverse();
    const pts = ordered.map((n, i) => {
        const x = pad + (i * (w - 2 * pad)) / (ordered.length - 1);
        const y = h - pad - ((n - min) / span) * (h - 2 * pad);
        return `${x.toFixed(1)},${y.toFixed(1)}`;
    });
    return (
        <svg width={w} height={h} style={{ display: "block" }}>
            <polyline
                fill="none"
                stroke="#14b8a6"
                strokeWidth="1.5"
                points={pts.join(" ")}
            />
        </svg>
    );
}
