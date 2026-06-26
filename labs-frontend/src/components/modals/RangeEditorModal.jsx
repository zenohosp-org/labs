import { useState, useEffect } from "react";
import { useNotification } from "@/context/NotificationContext";
import { referenceRangeApi } from "@/api/labsClient";
import { Button, FormGroup, Input, Modal, Select } from "@/components/ui";
import TestPicker from "@/components/TestPicker";

const SEX_OPTIONS = [
    { value: "ANY", label: "Any" },
    { value: "MALE", label: "Male" },
    { value: "FEMALE", label: "Female" },
];

const SPECIAL_STATE_OPTIONS = [
    { value: "", label: "— baseline —" },
    { value: "PREGNANT", label: "Pregnant" },
    { value: "NEONATE", label: "Neonate" },
    { value: "FASTING", label: "Fasting" },
    { value: "POSTPRANDIAL", label: "Postprandial" },
];

const empty = {
    labServiceId: null,
    testName: "",
    category: "",
    sex: "ANY",
    minAgeYears: "",
    maxAgeYears: "",
    minValue: "",
    maxValue: "",
    criticalLow: "",
    criticalHigh: "",
    specialState: "",
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
                labServiceId: range.labServiceId ?? null,
                testName: range.testName ?? "",
                category: range.category ?? "",
                sex: range.sex ?? "ANY",
                minAgeYears: range.minAgeYears ?? "",
                maxAgeYears: range.maxAgeYears ?? "",
                minValue: range.minValue ?? "",
                maxValue: range.maxValue ?? "",
                criticalLow: range.criticalLow ?? "",
                criticalHigh: range.criticalHigh ?? "",
                specialState: range.specialState ?? "",
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
            labServiceId: form.labServiceId ?? null,
            testName: form.testName.trim(),
            category: form.category.trim() || null,
            sex: form.sex,
            minAgeYears: form.minAgeYears === "" ? null : Number(form.minAgeYears),
            maxAgeYears: form.maxAgeYears === "" ? null : Number(form.maxAgeYears),
            minValue: form.minValue === "" ? null : Number(form.minValue),
            maxValue: form.maxValue === "" ? null : Number(form.maxValue),
            criticalLow: form.criticalLow === "" ? null : Number(form.criticalLow),
            criticalHigh: form.criticalHigh === "" ? null : Number(form.criticalHigh),
            specialState: form.specialState || null,
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
                    <FormGroup
                        label="Test name *"
                        hint={
                            form.labServiceId
                                ? "Linked to catalogue — unit/category auto-fill from the test row."
                                : "Type to search the catalogue; pick a row to link, or save as free text."
                        }
                    >
                        <TestPicker
                            value={form.testName}
                            labServiceId={form.labServiceId}
                            onChange={(v) => set("testName", v)}
                            onPick={(t) => {
                                setForm((f) => ({
                                    ...f,
                                    labServiceId: t.labServiceId,
                                    testName: t.name,
                                    category: t.category || f.category,
                                    unit: t.defaultUnit || f.unit,
                                }));
                            }}
                            onClear={() => set("labServiceId", null)}
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

                <div className="grid grid-cols-3 gap-3">
                    <FormGroup label="Critical LOW" hint="Triggers HL7 LL panic flag when crossed">
                        <Input
                            type="number"
                            step="any"
                            value={form.criticalLow}
                            onChange={(e) => set("criticalLow", e.target.value)}
                            placeholder="e.g. 6.0"
                        />
                    </FormGroup>
                    <FormGroup label="Critical HIGH" hint="Triggers HL7 HH panic flag when crossed">
                        <Input
                            type="number"
                            step="any"
                            value={form.criticalHigh}
                            onChange={(e) => set("criticalHigh", e.target.value)}
                            placeholder="e.g. 20.0"
                        />
                    </FormGroup>
                    <FormGroup label="Special state">
                        <select
                            className="hms-input"
                            value={form.specialState}
                            onChange={(e) => set("specialState", e.target.value)}
                        >
                            {SPECIAL_STATE_OPTIONS.map((o) => (
                                <option key={o.value} value={o.value}>{o.label}</option>
                            ))}
                        </select>
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
