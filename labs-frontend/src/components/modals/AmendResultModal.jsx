import { useState } from "react";
import { Pencil, AlertTriangle } from "lucide-react";
import { useNotification } from "@/context/NotificationContext";
import { resultApi } from "@/api/labsClient";
import {
    Alert,
    Button,
    FormGroup,
    Input,
    Modal,
    Select,
} from "@/components/ui";

const REASON_OPTIONS = [
    { value: "", label: "Select a reason…" },
    { value: "TRANSCRIPTION_ERROR", label: "Transcription error" },
    { value: "INSTRUMENT_RECAL", label: "Instrument recalibration" },
    { value: "UNIT_CONVERSION", label: "Unit conversion" },
    { value: "DELTA_RECHECK", label: "Delta-check re-run" },
    { value: "NEW_INFO", label: "New clinical information" },
    { value: "OTHER", label: "Other" },
];

/**
 * Amend a FINAL result. NEVER mutates the original — backend inserts a NEW
 * row with amendmentOfId pointing at the corrected row. Both rows survive.
 * Reason code is required (NABL audit-trail).
 */
export default function AmendResultModal({ result, onClose, onAmended }) {
    const { notify } = useNotification();
    const [reasonCode, setReasonCode] = useState("");
    const [reasonNotes, setReasonNotes] = useState("");
    const [valueNumeric, setValueNumeric] = useState(
        result?.valueNumeric != null ? String(result.valueNumeric) : "",
    );
    const [valueText, setValueText] = useState(result?.valueText ?? "");
    const [comments, setComments] = useState("");
    const [saving, setSaving] = useState(false);

    const isNumeric = result?.valueNumeric != null || !result?.valueText;

    const submit = async () => {
        if (!reasonCode) {
            notify("Pick a reason code (NABL requires it)", "error");
            return;
        }
        if (isNumeric && (valueNumeric === "" || isNaN(Number(valueNumeric)))) {
            notify("Enter a numeric value", "error");
            return;
        }
        if (!isNumeric && !valueText.trim()) {
            notify("Enter a text value", "error");
            return;
        }
        setSaving(true);
        try {
            await resultApi.amend(result.id, {
                reasonCode,
                reasonNotes: reasonNotes || null,
                valueNumeric: isNumeric ? Number(valueNumeric) : null,
                valueText: isNumeric ? null : valueText,
                comments: comments || null,
            });
            notify("Amendment recorded — original preserved", "success");
            onAmended?.();
            onClose();
        } catch {
            notify("Failed to amend", "error");
        } finally {
            setSaving(false);
        }
    };

    return (
        <Modal
            isOpen
            onClose={onClose}
            size="md"
            title={
                <span className="inline-flex items-center gap-2">
                    <Pencil size={18} /> Amend result · {result?.analyteName}
                </span>
            }
            footer={
                <>
                    <Button variant="cancel" onClick={onClose} disabled={saving}>
                        Cancel
                    </Button>
                    <Button variant="primary" onClick={submit} disabled={saving}>
                        {saving ? "Recording…" : "Record amendment"}
                    </Button>
                </>
            }
        >
            <Alert tone="warning" icon={<AlertTriangle size={16} />}>
                The original result stays untouched — a new <strong>CORRECTED</strong> row
                will supersede it. Both rows remain visible in the audit trail.
            </Alert>

            <div className="text-13 text-gray-600 mb-2">
                Previous value:{" "}
                <strong>
                    {result?.valueNumeric ?? result?.valueText}
                    {result?.unit ? ` ${result.unit}` : ""}
                </strong>
                {result?.abnormalFlag && (
                    <span className="ml-2 text-rose-600">[{result.abnormalFlag}]</span>
                )}
            </div>

            {isNumeric ? (
                <FormGroup label="Corrected value *">
                    <Input
                        type="number"
                        step="any"
                        value={valueNumeric}
                        onChange={(e) => setValueNumeric(e.target.value)}
                    />
                </FormGroup>
            ) : (
                <FormGroup label="Corrected value *">
                    <Input value={valueText} onChange={(e) => setValueText(e.target.value)} />
                </FormGroup>
            )}

            <FormGroup label="Reason code *">
                <Select
                    value={reasonCode}
                    onChange={(e) => setReasonCode(e.target.value)}
                    options={REASON_OPTIONS}
                />
            </FormGroup>

            <FormGroup label="Notes">
                <Input
                    value={reasonNotes}
                    onChange={(e) => setReasonNotes(e.target.value)}
                    placeholder="optional — describe the correction"
                />
            </FormGroup>

            <FormGroup label="Comments (appended to row)">
                <Input value={comments} onChange={(e) => setComments(e.target.value)} />
            </FormGroup>
        </Modal>
    );
}
