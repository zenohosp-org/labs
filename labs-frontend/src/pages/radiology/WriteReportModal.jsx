import { useState } from "react";
import { useNotification } from "@/context/NotificationContext";
import { radiologyApi } from "@/api/labsClient";
import { X, FileText } from "lucide-react";

function WriteReportModal({ order, onClose, onSaved }) {
    const { notify } = useNotification();
    const [findings, setFindings] = useState("");
    const [observation, setObservation] = useState("");
    const [saving, setSaving] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!findings.trim()) {
            notify("Findings are required", "error");
            return;
        }
        setSaving(true);
        try {
            await radiologyApi.generateReport(order.id, findings, observation);
            notify("Report generated", "success");
            onSaved();
        } catch {
            notify("Failed to generate report", "error");
        } finally {
            setSaving(false);
        }
    };

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
                    <div>
                        <label className="hms-rad-label-small">Findings *</label>
                        <textarea
                            rows={5}
                            className="hms-rad-textarea"
                            placeholder="Enter radiology findings…"
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
                            placeholder="e.g. Study appears within normal limits…"
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

export { WriteReportModal as default };
