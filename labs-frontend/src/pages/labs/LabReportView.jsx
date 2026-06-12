import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";
import { labApi } from "@/api/labsClient";
import { fmtId } from "@/utils/idFormat";
import { ArrowLeft, Printer, Loader2, TestTube, AlertCircle } from "lucide-react";
import { fmtDateTime } from "@/utils/date";

const PRIORITY_CLS = {
    ROUTINE: "is-routine",
    URGENT: "is-urgent",
    STAT: "is-stat",
};

/**
 * Mirror of RadiologyReportView for the pathology workflow. Same toolbar,
 * same card layout, same print path — only the labels swap to match the
 * lab semantics (collected vs scanned, sample type vs modality, Lab
 * Technician vs Radiologist).
 */
function LabReportView() {
    const { id } = useParams();
    const navigate = useNavigate();
    const { user } = useAuth();
    const [order, setOrder] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        if (!id) return;
        labApi
            .get(Number(id))
            .then(setOrder)
            .catch(() => setOrder(null))
            .finally(() => setLoading(false));
    }, [id]);

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
