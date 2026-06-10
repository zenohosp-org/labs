import { useState, useEffect } from "react";
import { useNotification } from "@/context/NotificationContext";
import { referenceRangeApi } from "@/api/labsClient";
import { Button, FormGroup, Input, Modal, Select } from "@/components/ui";

const SEX_OPTIONS = [
    { value: "ANY", label: "Any" },
    { value: "MALE", label: "Male" },
    { value: "FEMALE", label: "Female" },
];

const empty = {
    testName: "",
    category: "",
    sex: "ANY",
    minAgeYears: "",
    maxAgeYears: "",
    minValue: "",
    maxValue: "",
    unit: "",
    rangeText: "",
    isActive: true,
};

/**
 * Add/edit a reference band. Numeric bounds + display text are independent —
 * a band can be all-text ("Negative") with no numeric flags, or fully numeric
 * with a derived display string.
 */
export default function RangeEditorModal({ isOpen, onClose, range, onSuccess }) {
    const { notify } = useNotification();
    const [form, setForm] = useState(empty);
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        if (range) {
            setForm({
                testName: range.testName ?? "",
                category: range.category ?? "",
                sex: range.sex ?? "ANY",
                minAgeYears: range.minAgeYears ?? "",
                maxAgeYears: range.maxAgeYears ?? "",
                minValue: range.minValue ?? "",
                maxValue: range.maxValue ?? "",
                unit: range.unit ?? "",
                rangeText: range.rangeText ?? "",
                isActive: range.isActive !== false,
            });
        } else {
            setForm(empty);
        }
    }, [range, isOpen]);

    const set = (k, v) => setForm((f) => ({ ...f, [k]: v }));

    const handleSubmit = async (e) => {
        e?.preventDefault?.();
        if (!form.testName.trim()) {
            notify("Test name is required", "error");
            return;
        }
        if (!form.rangeText.trim()) {
            notify("Display text is required (e.g. '13.5 – 17.5 g/dL')", "error");
            return;
        }
        const payload = {
            testName: form.testName.trim(),
            category: form.category.trim() || null,
            sex: form.sex,
            minAgeYears: form.minAgeYears === "" ? null : Number(form.minAgeYears),
            maxAgeYears: form.maxAgeYears === "" ? null : Number(form.maxAgeYears),
            minValue: form.minValue === "" ? null : Number(form.minValue),
            maxValue: form.maxValue === "" ? null : Number(form.maxValue),
            unit: form.unit.trim() || null,
            rangeText: form.rangeText.trim(),
            isActive: form.isActive,
        };
        setSaving(true);
        try {
            if (range?.id) {
                await referenceRangeApi.update(range.id, payload);
            } else {
                await referenceRangeApi.create(payload);
            }
            notify(range?.id ? "Band updated" : "Band added", "success");
            onSuccess?.();
            onClose();
        } catch {
            notify("Failed to save", "error");
        } finally {
            setSaving(false);
        }
    };

    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            size="lg"
            title={range?.id ? "Edit reference band" : "New reference band"}
            footer={
                <>
                    <Button variant="cancel" onClick={onClose}>
                        Cancel
                    </Button>
                    <Button variant="primary" onClick={handleSubmit} disabled={saving}>
                        {saving ? "Saving…" : range?.id ? "Save changes" : "Add band"}
                    </Button>
                </>
            }
        >
            <form onSubmit={handleSubmit} className="flex flex-col gap-3">
                <div className="grid grid-cols-2 gap-3">
                    <FormGroup label="Test name *">
                        <Input
                            value={form.testName}
                            onChange={(e) => set("testName", e.target.value)}
                            placeholder="e.g. Hemoglobin"
                            autoFocus
                        />
                    </FormGroup>
                    <FormGroup label="Category">
                        <Input
                            value={form.category}
                            onChange={(e) => set("category", e.target.value)}
                            placeholder="e.g. HAEMATOLOGY"
                        />
                    </FormGroup>
                </div>

                <div className="grid grid-cols-3 gap-3">
                    <FormGroup label="Sex">
                        <Select
                            value={form.sex}
                            onChange={(e) => set("sex", e.target.value)}
                            options={SEX_OPTIONS}
                        />
                    </FormGroup>
                    <FormGroup label="Min age (years)">
                        <Input
                            type="number"
                            min="0"
                            value={form.minAgeYears}
                            onChange={(e) => set("minAgeYears", e.target.value)}
                            placeholder="e.g. 18"
                        />
                    </FormGroup>
                    <FormGroup label="Max age (years)">
                        <Input
                            type="number"
                            min="0"
                            value={form.maxAgeYears}
                            onChange={(e) => set("maxAgeYears", e.target.value)}
                            placeholder="e.g. 60"
                        />
                    </FormGroup>
                </div>

                <div className="grid grid-cols-3 gap-3">
                    <FormGroup label="Min value">
                        <Input
                            type="number"
                            step="any"
                            value={form.minValue}
                            onChange={(e) => set("minValue", e.target.value)}
                            placeholder="leave blank if open"
                        />
                    </FormGroup>
                    <FormGroup label="Max value">
                        <Input
                            type="number"
                            step="any"
                            value={form.maxValue}
                            onChange={(e) => set("maxValue", e.target.value)}
                            placeholder="leave blank if open"
                        />
                    </FormGroup>
                    <FormGroup label="Unit">
                        <Input
                            value={form.unit}
                            onChange={(e) => set("unit", e.target.value)}
                            placeholder="e.g. g/dL"
                        />
                    </FormGroup>
                </div>

                <FormGroup
                    label="Display text *"
                    hint="Shown verbatim on the report. e.g. '13.5 – 17.5 g/dL', 'Negative', '< 200 mg/dL'"
                >
                    <Input
                        value={form.rangeText}
                        onChange={(e) => set("rangeText", e.target.value)}
                        placeholder="13.5 – 17.5 g/dL"
                    />
                </FormGroup>

                <label className="inline-flex items-center gap-2 text-13 text-gray-700">
                    <input
                        type="checkbox"
                        checked={form.isActive}
                        onChange={(e) => set("isActive", e.target.checked)}
                    />
                    Active
                </label>
            </form>
        </Modal>
    );
}
