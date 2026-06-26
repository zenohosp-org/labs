import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import {
    ShieldCheck,
    ShieldAlert,
    ShieldOff,
    Loader2,
    FileSignature,
    Building2,
    User as UserIcon,
    Calendar,
    Hash,
} from "lucide-react";
import { reportVerifyApi } from "@/api/labsClient";

/**
 * Public report verification page. Reached via the QR on every signed PDF.
 * No authentication required — backend returns a deliberately minimal
 * payload (patient initials + signatory + signed_at + accession). Enough
 * to prove the QR is genuine; not enough to leak PHI to someone scanning
 * a discarded printout.
 */
export default function ReportVerify() {
    const { token } = useParams();
    const [state, setState] = useState({ loading: true, data: null, error: null });

    useEffect(() => {
        if (!token) {
            setState({ loading: false, data: null, error: "Missing token" });
            return;
        }
        reportVerifyApi
            .verify(token)
            .then((d) => setState({ loading: false, data: d, error: null }))
            .catch((e) =>
                setState({
                    loading: false,
                    data: null,
                    error: e?.response?.data?.message || "Could not verify this report.",
                }),
            );
    }, [token]);

    const status = state.data?.status;
    const Banner =
        status === "verified" ? VerifiedBanner :
        status === "revoked"  ? RevokedBanner  :
        status === "not_found" ? NotFoundBanner :
        null;

    return (
        <div style={pageStyles.page}>
            <div style={pageStyles.card}>
                <div style={pageStyles.header}>
                    <FileSignature size={20} style={{ color: "#0f172a" }} />
                    <span style={pageStyles.brand}>ZenoLabs · Report Verification</span>
                </div>

                {state.loading ? (
                    <div style={pageStyles.loading}>
                        <Loader2 size={20} className="animate-spin" />
                        <span style={{ marginLeft: 8 }}>Verifying…</span>
                    </div>
                ) : state.error ? (
                    <div style={pageStyles.error}>{state.error}</div>
                ) : Banner ? (
                    <>
                        <Banner data={state.data} />
                        {(status === "verified" || status === "revoked") && (
                            <Details data={state.data} />
                        )}
                    </>
                ) : null}

                <div style={pageStyles.footer}>
                    Powered by ZenoHosp · Authenticity is verified against the lab's
                    signed report ledger. Forwarded or screenshotted images of this
                    page are not proof — always scan the QR directly on the printed
                    or PDF report.
                </div>
            </div>
        </div>
    );
}

function VerifiedBanner({ data }) {
    return (
        <div style={{ ...pageStyles.banner, background: "#ecfdf5", borderColor: "#a7f3d0", color: "#065f46" }}>
            <ShieldCheck size={36} style={{ flexShrink: 0 }} />
            <div>
                <div style={pageStyles.bannerTitle}>Verified — report is authentic</div>
                <div style={pageStyles.bannerSub}>
                    Signed {fmtDate(data.signedAt)} · version v{data.version}
                </div>
            </div>
        </div>
    );
}

function RevokedBanner({ data }) {
    return (
        <div style={{ ...pageStyles.banner, background: "#fef2f2", borderColor: "#fecaca", color: "#991b1b" }}>
            <ShieldAlert size={36} style={{ flexShrink: 0 }} />
            <div>
                <div style={pageStyles.bannerTitle}>REVOKED — this report version is no longer valid</div>
                <div style={pageStyles.bannerSub}>
                    Revoked {fmtDate(data.revokedAt)}
                    {data.revokedReason ? ` · ${data.revokedReason}` : ""}
                </div>
                <div style={{ ...pageStyles.bannerSub, marginTop: 4 }}>
                    A corrected version may have been issued. Please contact the lab.
                </div>
            </div>
        </div>
    );
}

function NotFoundBanner() {
    return (
        <div style={{ ...pageStyles.banner, background: "#fefce8", borderColor: "#fde68a", color: "#854d0e" }}>
            <ShieldOff size={36} style={{ flexShrink: 0 }} />
            <div>
                <div style={pageStyles.bannerTitle}>Not found</div>
                <div style={pageStyles.bannerSub}>
                    This verification link doesn't match any signed report. The QR may
                    be from a different system, or the token is mistyped.
                </div>
            </div>
        </div>
    );
}

function Details({ data }) {
    return (
        <div style={pageStyles.grid}>
            <Row icon={Building2} label="Hospital" value={data.hospitalName} />
            <Row icon={UserIcon}  label="Patient"  value={data.patientInitials} />
            <Row icon={Hash}      label="Order"    value={`#${data.labOrderId}${data.accessionNumber ? ` · ACC ${data.accessionNumber}` : ""}`} />
            <Row icon={FileSignature} label="Investigation" value={data.testSummary} />
            <Row icon={Calendar}  label="Signed at" value={fmtDate(data.signedAt)} />
            <Row icon={UserIcon}  label="Signatory"
                 value={
                    data.signedByName +
                    (data.signatoryQualification ? ` · ${data.signatoryQualification}` : "") +
                    (data.signatoryRegistration ? ` · Reg. ${data.signatoryRegistration}` : "")
                 } />
        </div>
    );
}

function Row({ icon: Icon, label, value }) {
    return (
        <div style={pageStyles.row}>
            <Icon size={14} style={pageStyles.rowIcon} />
            <span style={pageStyles.rowLabel}>{label}</span>
            <span style={pageStyles.rowValue}>{value || "—"}</span>
        </div>
    );
}

function fmtDate(s) {
    if (!s) return "—";
    try { return new Date(s).toLocaleString(); } catch { return s; }
}

const pageStyles = {
    page: {
        minHeight: "100vh",
        background: "#f8fafc",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: 16,
    },
    card: {
        width: "100%",
        maxWidth: 560,
        background: "white",
        borderRadius: 16,
        boxShadow: "0 10px 40px -10px rgba(0,0,0,0.15)",
        padding: 24,
    },
    header: {
        display: "flex",
        alignItems: "center",
        gap: 8,
        marginBottom: 16,
    },
    brand: {
        fontSize: 14,
        fontWeight: 700,
        color: "#0f172a",
    },
    loading: {
        display: "flex",
        alignItems: "center",
        padding: "32px 0",
        color: "#475569",
        fontSize: 14,
    },
    error: {
        padding: 16,
        background: "#fef2f2",
        border: "1px solid #fecaca",
        borderRadius: 8,
        color: "#991b1b",
        fontSize: 13,
    },
    banner: {
        display: "flex",
        alignItems: "flex-start",
        gap: 12,
        border: "1px solid",
        borderRadius: 12,
        padding: 16,
        marginBottom: 16,
    },
    bannerTitle: { fontWeight: 700, fontSize: 15 },
    bannerSub:   { fontSize: 12, marginTop: 4 },
    grid: {
        display: "grid",
        gridTemplateColumns: "1fr",
        gap: 8,
        background: "#f8fafc",
        padding: 12,
        borderRadius: 8,
        border: "1px solid #e2e8f0",
    },
    row: {
        display: "flex",
        alignItems: "center",
        gap: 8,
        fontSize: 13,
        color: "#0f172a",
    },
    rowIcon:  { color: "#64748b", flexShrink: 0 },
    rowLabel: { color: "#64748b", width: 100, flexShrink: 0 },
    rowValue: { fontWeight: 600, color: "#0f172a" },
    footer: {
        marginTop: 20,
        fontSize: 11,
        color: "#64748b",
        textAlign: "center",
        lineHeight: 1.5,
    },
};
