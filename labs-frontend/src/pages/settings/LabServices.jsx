import { useEffect, useMemo, useState } from "react";
import {
    Plus,
    Edit2,
    Power,
    MoreHorizontal,
    AlertTriangle,
    FlaskConical,
    Trash2,
} from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import { useNotification } from "@/context/NotificationContext";
import { labServiceApi } from "@/api/labsClient";
import {
    Alert,
    Badge,
    Button,
    FormGroup,
    Input,
    Menu,
    Modal,
    MultiSelect,
    PageHeader,
    Pagination,
    SearchBar,
    Select,
    Table,
} from "@/components/ui";

const PAGE_SIZE = 30;

// Bounded by V11 CHECK constraint on lab_services.discipline.
const DISCIPLINE_OPTIONS = [
    { value: "PATHOLOGY",      label: "Pathology" },
    { value: "RADIOLOGY",      label: "Radiology" },
    { value: "CYTOLOGY",       label: "Cytology" },
    { value: "HISTOPATHOLOGY", label: "Histopathology" },
    { value: "MICROBIOLOGY",   label: "Microbiology" },
    { value: "IMMUNOLOGY",     label: "Immunology" },
];

const VALUE_TYPE_OPTIONS = [
    { value: "NUMERIC", label: "Numeric (e.g. Hb 13.2)" },
    { value: "TEXT",    label: "Text / Narrative (e.g. radiology findings)" },
    { value: "CODED",   label: "Coded (e.g. Positive / Negative / Reactive)" },
    { value: "RATIO",   label: "Ratio (e.g. 1:160 titre)" },
    { value: "BOOLEAN", label: "Boolean (Yes / No)" },
];

// Common Indian-lab specimen sources. Order = frequency of use.
const SPECIMEN_OPTIONS = [
    { value: "",            label: "—" },
    { value: "BLOOD",       label: "Blood (Whole)" },
    { value: "SERUM",       label: "Serum" },
    { value: "PLASMA",      label: "Plasma" },
    { value: "URINE",       label: "Urine" },
    { value: "STOOL",       label: "Stool" },
    { value: "SPUTUM",      label: "Sputum" },
    { value: "SWAB",        label: "Swab (Throat / Nasal / Wound)" },
    { value: "CSF",         label: "CSF" },
    { value: "BODY_FLUID",  label: "Body fluid (Pleural / Ascitic / Synovial)" },
    { value: "TISSUE",      label: "Tissue (Biopsy / Resection)" },
    { value: "BONE_MARROW", label: "Bone marrow" },
    { value: "SEMEN",       label: "Semen" },
    { value: "OTHER",       label: "Other" },
];

// Indian-lab tube/container conventions. NACL = sodium citrate for ESR.
const CONTAINER_OPTIONS = [
    { value: "",          label: "—" },
    { value: "EDTA",      label: "EDTA (Lavender) — CBC / HbA1c" },
    { value: "PLAIN",     label: "Plain / Serum (Red) — Biochem / Serology" },
    { value: "GEL_CLOT",  label: "Gel clot (Yellow / SST) — Biochem" },
    { value: "FLUORIDE",  label: "Fluoride (Grey) — Glucose / FBS" },
    { value: "CITRATE",   label: "Citrate (Blue) — PT / INR / aPTT" },
    { value: "HEPARIN",   label: "Heparin (Green) — Ammonia / Electrolytes" },
    { value: "ESR",       label: "ESR (Black / NACL)" },
    { value: "URINE_CUP", label: "Urine cup (Yellow lid)" },
    { value: "STOOL_CUP", label: "Stool cup" },
    { value: "SWAB",      label: "Swab transport medium" },
    { value: "FORMALIN",  label: "Formalin jar — Histopath" },
    { value: "OTHER",     label: "Other" },
];

const empty = {
    id: null,
    testCode: "",
    loincCode: "",
    name: "",
    aliases: "",
    category: "",
    discipline: "PATHOLOGY",
    specimenKind: "BLOOD",
    defaultContainerType: "",
    defaultAdditive: "",
    defaultVolumeMl: "",
    fastingRequired: false,
    stabilityMinutes: "",
    defaultMethod: "",
    defaultUnit: "",
    valueType: "NUMERIC",
    requiresAuthorisation: false,
    tatMinutes: "",
    isPanel: false,
    parentPanelCode: "",
    price: "",
    gstRate: "",
    displayOrder: "",
    active: true,
};

/**
 * Per-hospital LOINC-coded test catalogue admin.
 *
 * Lazy-seeded by the backend the first time the page is opened — ~47
 * Indian-lab analytes across 7 panels (CBC, LFT, RFT, Lipid, Thyroid,
 * Diabetes, Urine). Admins extend / disable / customise from here; the
 * per-analyte result entry in LabWriteReportModal consumes the same
 * catalogue.
 */
export default function LabServices() {
    const { user } = useAuth();
    const { notify } = useNotification();

    const [rows, setRows] = useState([]);
    const [loading, setLoading] = useState(true);
    const [search, setSearch] = useState("");
    const [page, setPage] = useState(1);
    const [editorOpen, setEditorOpen] = useState(false);
    const [form, setForm] = useState(empty);
    const [confirmDelete, setConfirmDelete] = useState(null);
    // Empty array = no category filter. Multi-select: the LOINC 2.82 seed
    // brings 31 LOINC classes on top of the app's own taxonomy, and real
    // filtering questions are unions ("CHEM + HEM/BC"), not single picks.
    const [categoryFilter, setCategoryFilter] = useState([]);

    const load = async () => {
        if (!user?.hospitalId) return;
        setLoading(true);
        try {
            const data = await labServiceApi.list(user.hospitalId, false);
            setRows(data ?? []);
        } catch {
            notify("Failed to load lab services", "error");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        load();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [user?.hospitalId]);

    /** Categories present in the data, each with its tally for the dropdown. */
    const categoryOptions = useMemo(() => {
        const counts = new Map();
        for (const r of rows) {
            if (!r.category) continue;
            counts.set(r.category, (counts.get(r.category) ?? 0) + 1);
        }
        return [...counts.entries()]
            .sort((a, b) => a[0].localeCompare(b[0]))
            .map(([value, count]) => ({ value, label: value, count }));
    }, [rows]);

    const filtered = useMemo(() => {
        const q = search.trim().toLowerCase();
        // Set lookup, not Array.includes: this runs per row over ~1900 rows on
        // every keystroke, and includes() would make it O(rows × categories).
        const cats = categoryFilter.length ? new Set(categoryFilter) : null;
        return rows.filter((r) => {
            const matchesSearch =
                !q ||
                r.name?.toLowerCase().includes(q) ||
                r.testCode?.toLowerCase().includes(q) ||
                r.loincCode?.toLowerCase().includes(q) ||
                r.aliases?.toLowerCase().includes(q);
            const matchesCat = !cats || cats.has(r.category);
            return matchesSearch && matchesCat;
        });
    }, [rows, search, categoryFilter]);

    const paginated = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
    const totalPages = Math.ceil(filtered.length / PAGE_SIZE);

    const openNew = () => {
        setForm(empty);
        setEditorOpen(true);
    };
    const openEdit = (r) => {
        setForm({
            id: r.id,
            testCode: r.testCode ?? "",
            loincCode: r.loincCode ?? "",
            name: r.name ?? "",
            aliases: r.aliases ?? "",
            category: r.category ?? "",
            discipline: r.discipline ?? "PATHOLOGY",
            specimenKind: r.specimenKind ?? "",
            defaultContainerType: r.defaultContainerType ?? "",
            defaultAdditive: r.defaultAdditive ?? "",
            defaultVolumeMl: r.defaultVolumeMl ?? "",
            fastingRequired: !!r.fastingRequired,
            stabilityMinutes: r.stabilityMinutes ?? "",
            defaultMethod: r.defaultMethod ?? "",
            defaultUnit: r.defaultUnit ?? "",
            valueType: r.valueType ?? "NUMERIC",
            requiresAuthorisation: !!r.requiresAuthorisation,
            tatMinutes: r.tatMinutes ?? "",
            isPanel: !!r.isPanel,
            parentPanelCode: r.parentPanelCode ?? "",
            price: r.price ?? "",
            gstRate: r.gstRate ?? "",
            displayOrder: r.displayOrder ?? "",
            active: r.active !== false,
        });
        setEditorOpen(true);
    };

    const set = (k, v) => setForm((f) => ({ ...f, [k]: v }));

    const save = async () => {
        if (!form.testCode.trim() || !form.name.trim()) {
            notify("testCode and name are required", "error");
            return;
        }
        const payload = {
            testCode: form.testCode.trim(),
            loincCode: form.loincCode.trim() || null,
            name: form.name.trim(),
            aliases: form.aliases.trim() || null,
            category: form.category.trim() || null,
            discipline: form.discipline || null,
            specimenKind: form.specimenKind || null,
            defaultContainerType: form.defaultContainerType || null,
            defaultAdditive: form.defaultAdditive.trim() || null,
            defaultVolumeMl: form.defaultVolumeMl === "" ? null : Number(form.defaultVolumeMl),
            fastingRequired: !!form.fastingRequired,
            stabilityMinutes: form.stabilityMinutes === "" ? null : Number(form.stabilityMinutes),
            defaultMethod: form.defaultMethod.trim() || null,
            defaultUnit: form.defaultUnit.trim() || null,
            valueType: form.valueType,
            requiresAuthorisation: !!form.requiresAuthorisation,
            tatMinutes: form.tatMinutes === "" ? null : Number(form.tatMinutes),
            isPanel: !!form.isPanel,
            parentPanelCode: form.parentPanelCode.trim() || null,
            price: form.price === "" ? null : Number(form.price),
            gstRate: form.gstRate === "" ? null : Number(form.gstRate),
            displayOrder: form.displayOrder === "" ? null : Number(form.displayOrder),
            active: form.active,
        };
        try {
            await labServiceApi.upsert(payload);
            notify(form.id ? "Test updated" : "Test added", "success");
            setEditorOpen(false);
            await load();
        } catch {
            notify("Save failed", "error");
        }
    };

    const handleToggle = async (id) => {
        try {
            await labServiceApi.toggle(id);
            notify("Status updated", "success");
            await load();
        } catch {
            notify("Toggle failed", "error");
        }
    };

    const handleDelete = async () => {
        if (!confirmDelete) return;
        try {
            await labServiceApi.delete(confirmDelete.id);
            notify("Test deleted", "success");
            setConfirmDelete(null);
            await load();
        } catch {
            notify("Delete failed", "error");
            setConfirmDelete(null);
        }
    };

    const columns = [
        {
            header: "Test",
            width: "26%",
            render: (r) => (
                <div className="flex flex-col gap-0.5">
                    <span className="font-bold text-gray-900 text-14 inline-flex items-center gap-1">
                        {r.isPanel && <Badge tone="info" soft>PANEL</Badge>}
                        {r.name}
                    </span>
                    <span className="text-12 text-gray-500 font-mono">
                        {r.testCode}
                        {r.loincCode && <span className="ml-2">LOINC {r.loincCode}</span>}
                        {r.parentPanelCode && (
                            <span className="ml-2 text-amber-700">→ {r.parentPanelCode}</span>
                        )}
                    </span>
                </div>
            ),
        },
        {
            header: "Category",
            width: "14%",
            render: (r) =>
                r.category ? (
                    <Badge tone="neutral" soft>{r.category}</Badge>
                ) : (
                    <span className="text-gray-300">—</span>
                ),
        },
        {
            header: "Specimen",
            width: "12%",
            render: (r) => (
                <div className="text-12 text-gray-700">
                    {r.specimenKind || "—"}
                    {r.defaultContainerType && (
                        <div className="text-11 text-gray-500">{r.defaultContainerType}</div>
                    )}
                </div>
            ),
        },
        {
            header: "Unit",
            width: "8%",
            render: (r) => r.defaultUnit || <span className="text-gray-300">—</span>,
        },
        {
            header: "Method",
            width: "14%",
            render: (r) =>
                r.defaultMethod ? (
                    <span className="text-12 text-gray-700">{r.defaultMethod}</span>
                ) : (
                    <span className="text-gray-300">—</span>
                ),
        },
        {
            header: "Price",
            width: "10%",
            render: (r) =>
                r.price != null ? (
                    <span className="text-13 text-gray-800 font-medium tabular-nums">
                        ₹{Number(r.price).toLocaleString("en-IN")}
                        {r.gstRate != null && (
                            <span className="text-11 text-gray-500"> +{r.gstRate}%</span>
                        )}
                    </span>
                ) : (
                    <span className="text-gray-300 text-12">—</span>
                ),
        },
        {
            header: "Ranges",
            width: "8%",
            render: (r) =>
                r.rangeCount && r.rangeCount > 0 ? (
                    <Badge tone="info" soft>{r.rangeCount}</Badge>
                ) : (
                    <span className="text-gray-300 text-12">none</span>
                ),
        },
        {
            header: "Status",
            width: "8%",
            render: (r) => (
                <div className="flex flex-col gap-0.5">
                    <Badge tone={r.active ? "success" : "danger"} soft>
                        {r.active ? "Active" : "Inactive"}
                    </Badge>
                    {r.hospitalServiceId && (
                        <span
                            className="text-11 text-emerald-700"
                            title={`Linked to HMS service ${r.hospitalServiceId}`}
                        >
                            → service
                        </span>
                    )}
                </div>
            ),
        },
        {
            header: "",
            width: "10%",
            align: "right",
            render: (r) => (
                <Menu
                    triggerIcon={<MoreHorizontal size={18} />}
                    triggerLabel="Row actions"
                    align="right"
                    items={[
                        { label: "Edit", icon: <Edit2 size={14} />, onClick: () => openEdit(r) },
                        {
                            label: r.active ? "Deactivate" : "Activate",
                            icon: <Power size={14} />,
                            onClick: () => handleToggle(r.id),
                        },
                        { divider: true },
                        {
                            label: "Delete",
                            icon: <Trash2 size={14} />,
                            tone: "danger",
                            onClick: () => setConfirmDelete(r),
                        },
                    ]}
                />
            ),
        },
    ];

    const titleNode = (
        <span className="inline-flex items-center gap-3">
            Lab Services
            <Badge tone="info">{rows.length} services</Badge>
        </span>
    );

    return (
        <div className="flex flex-col gap-4">
            <PageHeader
                title={titleNode}
                actions={
                    <Button variant="primary" onClick={openNew}>
                        <Plus size={14} strokeWidth={2.4} /> New test
                    </Button>
                }
            />

            <div className="hms-page-content">
                <Alert tone="info">
                    Auto-seeded with common Indian-lab analytes on first open. Edits here
                    drive the per-analyte result-entry UI (LOINC, defaults, signoff rules).
                </Alert>

                <div className="flex items-center gap-3">
                    <div className="flex-1">
                        <SearchBar
                            value={search}
                            onChange={(v) => {
                                setSearch(v);
                                setPage(1);
                            }}
                            placeholder="Search by name, code, LOINC, alias…"
                        />
                    </div>
                    {/* Was a row of chips — one per category. Fine at 3
                        taxonomies; the LOINC seed took it to 41 and it wrapped
                        to four rows that pushed the table below the fold. A
                        searchable multi-select holds the same choices in one
                        control and adds the union filtering the chips couldn't
                        express. */}
                    <div className="hms-lab-svc-catfilter">
                        <MultiSelect
                            options={categoryOptions}
                            value={categoryFilter}
                            onChange={(next) => {
                                setCategoryFilter(next);
                                setPage(1);
                            }}
                            placeholder="All categories"
                            searchPlaceholder="Search categories…"
                            summaryNoun="categories"
                        />
                    </div>
                </div>

                <Table
                    columns={columns}
                    data={paginated}
                    loading={loading}
                    loadingMessage={<span className="text-gray-500">Loading catalogue…</span>}
                    emptyMessage={
                        <div className="hms-cell-empty">
                            <span className="hms-cell-empty__icon">
                                <FlaskConical size={22} />
                            </span>
                            <div className="hms-cell-empty__text">
                                {search || categoryFilter.length > 0
                                    ? "No tests match your filters."
                                    : "No tests configured yet."}
                            </div>
                        </div>
                    }
                />

                {!loading && filtered.length > 0 && totalPages > 1 && (
                    <div className="pt-1">
                        <Pagination
                            currentPage={page}
                            totalPages={totalPages}
                            totalItems={filtered.length}
                            pageSize={PAGE_SIZE}
                            onPageChange={setPage}
                        />
                    </div>
                )}
            </div>

            <Modal
                isOpen={editorOpen}
                onClose={() => setEditorOpen(false)}
                size="xl"
                title={form.id ? "Edit test" : "New test"}
                footer={
                    <>
                        <Button variant="cancel" onClick={() => setEditorOpen(false)}>Cancel</Button>
                        <Button variant="primary" onClick={save}>
                            {form.id ? "Save changes" : "Add test"}
                        </Button>
                    </>
                }
            >
                <div className="grid grid-cols-3 gap-3">
                    <FormGroup label="Test code *">
                        <Input value={form.testCode} onChange={(e) => set("testCode", e.target.value)} placeholder="e.g. HB" autoFocus />
                    </FormGroup>
                    <FormGroup label="LOINC code">
                        <Input value={form.loincCode} onChange={(e) => set("loincCode", e.target.value)} placeholder="e.g. 718-7" />
                    </FormGroup>
                    <FormGroup label="Display name *">
                        <Input value={form.name} onChange={(e) => set("name", e.target.value)} placeholder="e.g. Hemoglobin" />
                    </FormGroup>
                </div>

                <div className="grid grid-cols-3 gap-3">
                    <FormGroup label="Category">
                        <Input value={form.category} onChange={(e) => set("category", e.target.value)} placeholder="HAEMATOLOGY" />
                    </FormGroup>
                    <FormGroup label="Discipline">
                        <Select value={form.discipline} onChange={(e) => set("discipline", e.target.value)}>
                            {DISCIPLINE_OPTIONS.map((o) => (
                                <option key={o.value} value={o.value}>{o.label}</option>
                            ))}
                        </Select>
                    </FormGroup>
                    <FormGroup label="Value type">
                        <Select value={form.valueType} onChange={(e) => set("valueType", e.target.value)}>
                            {VALUE_TYPE_OPTIONS.map((o) => (
                                <option key={o.value} value={o.value}>{o.label}</option>
                            ))}
                        </Select>
                    </FormGroup>
                </div>

                <div className="grid grid-cols-3 gap-3">
                    <FormGroup label="Specimen kind">
                        <Select value={form.specimenKind} onChange={(e) => set("specimenKind", e.target.value)}>
                            {SPECIMEN_OPTIONS.map((o) => (
                                <option key={o.value || "blank"} value={o.value}>{o.label}</option>
                            ))}
                        </Select>
                    </FormGroup>
                    <FormGroup label="Default container">
                        <Select value={form.defaultContainerType} onChange={(e) => set("defaultContainerType", e.target.value)}>
                            {CONTAINER_OPTIONS.map((o) => (
                                <option key={o.value || "blank"} value={o.value}>{o.label}</option>
                            ))}
                        </Select>
                    </FormGroup>
                    <FormGroup label="Default volume (mL)">
                        <Input type="number" step="any" value={form.defaultVolumeMl} onChange={(e) => set("defaultVolumeMl", e.target.value)} />
                    </FormGroup>
                </div>

                <div className="grid grid-cols-3 gap-3">
                    <FormGroup label="Default unit (UCUM)">
                        <Input value={form.defaultUnit} onChange={(e) => set("defaultUnit", e.target.value)} placeholder="g/dL" />
                    </FormGroup>
                    <FormGroup label="Default method">
                        <Input value={form.defaultMethod} onChange={(e) => set("defaultMethod", e.target.value)} placeholder="Spectrophotometry" />
                    </FormGroup>
                    <FormGroup label="Stability (min)">
                        <Input type="number" value={form.stabilityMinutes} onChange={(e) => set("stabilityMinutes", e.target.value)} placeholder="e.g. 240" />
                    </FormGroup>
                </div>

                <div className="grid grid-cols-3 gap-3">
                    <FormGroup label="TAT (min)">
                        <Input type="number" value={form.tatMinutes} onChange={(e) => set("tatMinutes", e.target.value)} />
                    </FormGroup>
                    <FormGroup label="Price">
                        <Input type="number" step="any" value={form.price} onChange={(e) => set("price", e.target.value)} />
                    </FormGroup>
                    <FormGroup label="GST rate (%)">
                        <Input type="number" step="any" value={form.gstRate} onChange={(e) => set("gstRate", e.target.value)} />
                    </FormGroup>
                </div>

                <div className="grid grid-cols-3 gap-3">
                    <FormGroup label="Parent panel code">
                        <Input value={form.parentPanelCode} onChange={(e) => set("parentPanelCode", e.target.value)} placeholder="e.g. CBC (for analytes)" />
                    </FormGroup>
                    <FormGroup label="Display order">
                        <Input type="number" value={form.displayOrder} onChange={(e) => set("displayOrder", e.target.value)} />
                    </FormGroup>
                    <FormGroup label="Aliases (comma-separated)">
                        <Input value={form.aliases} onChange={(e) => set("aliases", e.target.value)} placeholder="C.B.C, Complete blood count" />
                    </FormGroup>
                </div>

                <div className="flex gap-6 mt-2 text-13 text-gray-700">
                    <label className="inline-flex items-center gap-2">
                        <input type="checkbox" checked={form.isPanel} onChange={(e) => set("isPanel", e.target.checked)} />
                        Is panel (composite)
                    </label>
                    <label className="inline-flex items-center gap-2">
                        <input type="checkbox" checked={form.fastingRequired} onChange={(e) => set("fastingRequired", e.target.checked)} />
                        Fasting required
                    </label>
                    <label className="inline-flex items-center gap-2">
                        <input type="checkbox" checked={form.requiresAuthorisation} onChange={(e) => set("requiresAuthorisation", e.target.checked)} />
                        Requires pathologist authorise
                    </label>
                    <label className="inline-flex items-center gap-2">
                        <input type="checkbox" checked={form.active} onChange={(e) => set("active", e.target.checked)} />
                        Active
                    </label>
                </div>
            </Modal>

            <Modal
                isOpen={!!confirmDelete}
                onClose={() => setConfirmDelete(null)}
                size="sm"
                title="Delete test"
                footer={
                    <>
                        <Button variant="cancel" onClick={() => setConfirmDelete(null)}>Cancel</Button>
                        <Button variant="danger" onClick={handleDelete}>Delete</Button>
                    </>
                }
            >
                <Alert tone="danger" icon={<AlertTriangle size={16} />}>
                    Already-recorded results stay as they were. Future entries won't be
                    auto-classified for this test.
                </Alert>
                {confirmDelete && (
                    <div className="text-13 text-gray-700">
                        <strong>{confirmDelete.name}</strong> ({confirmDelete.testCode})
                    </div>
                )}
            </Modal>
        </div>
    );
}
