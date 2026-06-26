import { useState, useEffect } from "react";
import { useAuth } from "@/context/AuthContext";
import { useNotification } from "@/context/NotificationContext";
import { labPackageApi } from "@/api/labsClient";
import SearchableSelect from "@/components/ui/SearchableSelect";
import TestPicker from "@/components/TestPicker";
import {
    Package, Plus, Edit2, Trash2, ToggleLeft, ToggleRight,
    ChevronDown, ChevronUp, GripVertical, X, Check, AlertCircle,
} from "lucide-react";

/**
 * Lab Packages — ad-hoc investigation bundles at combo pricing (e.g.
 * "Liver Profile" = SGPT + SGOT + Bilirubin at flat rate). Distinct from
 * Health Checkup Packages (wellness bundles) which live under /checkups.
 *
 * Reuses the hms-pkg-* / hms-checkup-pkg-* design-system classes already in
 * the labs CSS tree so no new styles ship.
 */

const CATEGORIES = [
    { value: "GENERAL", label: "General" },
    { value: "HAEMATOLOGY", label: "Haematology" },
    { value: "BIOCHEMISTRY", label: "Biochemistry" },
    { value: "MICROBIOLOGY", label: "Microbiology" },
    { value: "ENDOCRINOLOGY", label: "Endocrinology" },
    { value: "IMAGING", label: "Imaging" },
    { value: "CUSTOM", label: "Custom" },
];

const INVESTIGATION_TYPES = [
    { value: "PATHOLOGY", label: "Pathology (lab)" },
    { value: "RADIOLOGY", label: "Radiology (imaging)" },
];

const CATEGORY_CHIP_CLS = {
    GENERAL: "is-cat-general",
    HAEMATOLOGY: "is-cat-cardiac",
    BIOCHEMISTRY: "is-cat-diabetic",
    MICROBIOLOGY: "is-cat-cancer",
    ENDOCRINOLOGY: "is-cat-womens",
    IMAGING: "is-cat-comprehensive",
    CUSTOM: "is-cat-custom",
};

const EMPTY_ITEM = { investigationName: "", investigationType: "PATHOLOGY", category: "", labServiceId: null };

const EMPTY_FORM = {
    name: "", description: "", category: "GENERAL",
    price: "", taxRate: "0", validityDays: 1, active: true, items: [],
};

function PackageFormModal({ initial, hospitalId, onClose, onSaved }) {
    const { notify } = useNotification();
    const [form, setForm] = useState(initial || EMPTY_FORM);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState(null);

    const set = (k, v) => setForm((f) => ({ ...f, [k]: v }));
    const addItem = () => setForm((f) => ({ ...f, items: [...f.items, { ...EMPTY_ITEM }] }));
    const updateItem = (i, k, v) =>
        setForm((f) => {
            const items = [...f.items];
            items[i] = { ...items[i], [k]: v };
            return { ...f, items };
        });
    const removeItem = (i) =>
        setForm((f) => ({ ...f, items: f.items.filter((_, idx) => idx !== i) }));

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!form.name.trim() || !form.price) {
            setError("Name and combo price are required.");
            return;
        }
        if (form.items.length === 0) {
            setError("Add at least one investigation to the package.");
            return;
        }
        setSaving(true);
        setError(null);
        try {
            await labPackageApi.save(hospitalId, {
                ...form,
                price: parseFloat(form.price),
                taxRate: form.taxRate === "" ? 0 : parseFloat(form.taxRate),
            });
            notify(form.id ? "Package updated" : "Package created", "success");
            onSaved();
            onClose();
        } catch {
            setError("Failed to save package. Please try again.");
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="hms-pkg-modal-overlay">
            <div className="hms-pkg-modal">
                <div className="hms-pkg-modal__head">
                    <h2 className="hms-pkg-modal__title">
                        {form.id ? "Edit Lab Package" : "New Lab Package"}
                    </h2>
                    <button onClick={onClose} className="hms-pkg-modal__close">
                        <X className="w-4 h-4" />
                    </button>
                </div>

                <form onSubmit={handleSubmit} className="hms-pkg-modal__form">
                    {error && (
                        <div className="hms-pkg-modal__error">
                            <AlertCircle className="w-4 h-4 shrink-0" /> {error}
                        </div>
                    )}

                    <div className="hms-pkg-modal__grid">
                        <div className="is-span-2">
                            <label className="hms-pkg-modal__label">Package Name</label>
                            <input
                                value={form.name}
                                onChange={(e) => set("name", e.target.value)}
                                placeholder="e.g. Liver Function Profile"
                                className="hms-pkg-modal__input"
                            />
                        </div>
                        <div>
                            <label className="hms-pkg-modal__label">Category</label>
                            <SearchableSelect
                                value={form.category}
                                onChange={(v) => set("category", v)}
                                options={CATEGORIES.map((c) => ({ value: c.value, label: c.label }))}
                            />
                        </div>
                        <div>
                            <label className="hms-pkg-modal__label">Combo Price (₹)</label>
                            <input
                                type="number"
                                step="0.01"
                                value={form.price}
                                onChange={(e) => set("price", e.target.value)}
                                placeholder="0.00"
                                className="hms-pkg-modal__input"
                            />
                        </div>
                        <div>
                            <label className="hms-pkg-modal__label">GST %</label>
                            <input
                                type="number"
                                step="0.01"
                                value={form.taxRate}
                                onChange={(e) => set("taxRate", e.target.value)}
                                placeholder="0"
                                className="hms-pkg-modal__input"
                            />
                        </div>
                        <div>
                            <label className="hms-pkg-modal__label">Validity (Days)</label>
                            <input
                                type="number"
                                min="1"
                                value={form.validityDays}
                                onChange={(e) => set("validityDays", parseInt(e.target.value || "1", 10))}
                                className="hms-pkg-modal__input"
                            />
                        </div>
                        <div className="is-span-2">
                            <label className="hms-pkg-modal__label">Description</label>
                            <textarea
                                rows={2}
                                value={form.description}
                                onChange={(e) => set("description", e.target.value)}
                                placeholder="Brief description of what this package covers…"
                                className="hms-pkg-modal__input"
                            />
                        </div>
                    </div>

                    {/* Items */}
                    <div>
                        <div className="hms-pkg-modal__test-head">
                            <label className="hms-pkg-modal__label">
                                Investigations Included ({form.items.length})
                            </label>
                            <button type="button" onClick={addItem} className="hms-pkg-modal__add-test">
                                <Plus className="w-3 h-3" /> Add Investigation
                            </button>
                        </div>

                        {form.items.length === 0 ? (
                            <div className="hms-pkg-modal__tests-empty">
                                <Package className="w-6 h-6 opacity-40" />
                                <p className="hms-pkg-modal__tests-empty-text">
                                    No investigations added yet. Click "Add Investigation" to begin.
                                </p>
                            </div>
                        ) : (
                            <div className="flex flex-col gap-2">
                                {form.items.map((it, i) => (
                                    <div key={i} className="hms-pkg-modal__test-row">
                                        <GripVertical className="hms-pkg-modal__test-grip w-4 h-4" />
                                        <div className="hms-pkg-modal__test-fields">
                                            <div className="is-span-2">
                                                <TestPicker
                                                    value={it.investigationName}
                                                    labServiceId={it.labServiceId}
                                                    onChange={(v) => updateItem(i, "investigationName", v)}
                                                    onPick={(t) => {
                                                        setForm((f) => {
                                                            const items = [...f.items];
                                                            items[i] = {
                                                                ...items[i],
                                                                investigationName: t.name,
                                                                labServiceId: t.labServiceId,
                                                                category: t.category || items[i].category,
                                                            };
                                                            return { ...f, items };
                                                        });
                                                    }}
                                                    onClear={() => updateItem(i, "labServiceId", null)}
                                                    placeholder="Investigation name (e.g. SGPT (ALT))"
                                                />
                                            </div>
                                            <div>
                                                <SearchableSelect
                                                    value={it.investigationType}
                                                    onChange={(v) => updateItem(i, "investigationType", v)}
                                                    options={INVESTIGATION_TYPES}
                                                />
                                            </div>
                                            <div>
                                                <input
                                                    value={it.category}
                                                    onChange={(e) => updateItem(i, "category", e.target.value)}
                                                    placeholder="Category (optional)"
                                                    className="hms-pkg-modal__test-field"
                                                />
                                            </div>
                                        </div>
                                        <div className="hms-pkg-modal__test-actions">
                                            <button
                                                type="button"
                                                onClick={() => removeItem(i)}
                                                className="hms-pkg-modal__test-remove"
                                            >
                                                <X className="w-3.5 h-3.5" />
                                            </button>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>

                    <div className="hms-pkg-modal__foot">
                        <label className="hms-pkg-modal__active-toggle">
                            <input
                                type="checkbox"
                                checked={form.active}
                                onChange={(e) => set("active", e.target.checked)}
                            />
                            <span>Active (visible for ordering)</span>
                        </label>
                        <div className="hms-pkg-modal__foot-actions">
                            <button type="button" onClick={onClose} className="hms-btn-cancel">
                                Cancel
                            </button>
                            <button type="submit" disabled={saving} className="hms-btn-primary is-green">
                                {saving ? "Saving…" : "Save Package"}
                            </button>
                        </div>
                    </div>
                </form>
            </div>
        </div>
    );
}

function PackageCard({ pkg, onEdit, onToggle, onDelete }) {
    const [expanded, setExpanded] = useState(false);
    const label = CATEGORIES.find((c) => c.value === pkg.category)?.label || pkg.category;
    const chipCls = CATEGORY_CHIP_CLS[pkg.category] || CATEGORY_CHIP_CLS.CUSTOM;

    return (
        <div className={`hms-checkup-pkg-tile ${pkg.active ? "" : "is-inactive"}`}>
            <div className="hms-checkup-pkg-tile__body">
                <div className="hms-checkup-pkg-tile__row">
                    <div className="hms-checkup-pkg-tile__main">
                        <div className="hms-checkup-pkg-tile__title-row">
                            <h3 className="hms-checkup-pkg-tile__name">{pkg.name}</h3>
                            <span className={`hms-checkup-pkg-tile__chip ${chipCls}`}>{label}</span>
                            {pkg.taxRate > 0 && (
                                <span className="hms-checkup-pkg-tile__chip is-gender">GST {Number(pkg.taxRate)}%</span>
                            )}
                            {!pkg.active && (
                                <span className="hms-checkup-pkg-tile__chip is-inactive">Inactive</span>
                            )}
                        </div>
                        {pkg.description && (
                            <p className="hms-checkup-pkg-tile__desc">{pkg.description}</p>
                        )}
                    </div>
                    <div className="hms-checkup-pkg-tile__price-col">
                        <p className="hms-checkup-pkg-tile__price">
                            ₹{Number(pkg.price).toLocaleString("en-IN")}
                        </p>
                        <p className="hms-checkup-pkg-tile__validity">
                            {pkg.validityDays === 1 ? "Single visit" : `${pkg.validityDays} days`}
                        </p>
                    </div>
                </div>

                <div className="hms-checkup-pkg-tile__foot">
                    <button
                        onClick={() => setExpanded((v) => !v)}
                        className="hms-checkup-pkg-tile__count"
                    >
                        {expanded ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
                        {pkg.items?.length || 0} investigations
                    </button>
                    <div className="hms-checkup-pkg-tile__actions">
                        <button
                            onClick={onToggle}
                            className={`hms-checkup-pkg-tile__act ${pkg.active ? "is-on-toggle" : ""}`}
                            title={pkg.active ? "Deactivate" : "Activate"}
                        >
                            {pkg.active ? <ToggleRight className="w-4 h-4" /> : <ToggleLeft className="w-4 h-4" />}
                        </button>
                        <button onClick={onEdit} className="hms-checkup-pkg-tile__act is-edit">
                            <Edit2 className="w-3.5 h-3.5" />
                        </button>
                        <button onClick={onDelete} className="hms-checkup-pkg-tile__act is-delete">
                            <Trash2 className="w-3.5 h-3.5" />
                        </button>
                    </div>
                </div>
            </div>

            {expanded && pkg.items?.length > 0 && (
                <div className="hms-checkup-pkg-tile__tests">
                    {pkg.items.map((it, i) => (
                        <div key={i} className="hms-checkup-pkg-tile__test-row">
                            <span className="hms-checkup-pkg-tile__test-num">{i + 1}</span>
                            <span className="hms-checkup-pkg-tile__test-name">{it.investigationName}</span>
                            <span className="hms-checkup-pkg-tile__test-cat">
                                {it.investigationType}
                            </span>
                            {it.category && (
                                <span className="hms-checkup-pkg-tile__test-range">({it.category})</span>
                            )}
                            <Check className="w-3 h-3 text-emerald shrink-0" />
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}

export default function LabPackages() {
    const { user } = useAuth();
    const { notify } = useNotification();
    const hospitalId = user?.hospitalId;

    const [packages, setPackages] = useState([]);
    const [loading, setLoading] = useState(true);
    const [showForm, setShowForm] = useState(false);
    const [editing, setEditing] = useState(null);
    const [filterCat, setFilterCat] = useState("ALL");

    useEffect(() => {
        if (hospitalId) load();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [hospitalId]);

    const load = async () => {
        setLoading(true);
        try {
            setPackages(await labPackageApi.list(hospitalId));
        } catch {
            notify("Failed to load lab packages", "error");
        } finally {
            setLoading(false);
        }
    };

    const handleEdit = (pkg) => {
        setEditing({
            ...pkg,
            price: String(pkg.price),
            taxRate: String(pkg.taxRate ?? "0"),
            items: (pkg.items || []).map((it) => ({ ...it })),
        });
        setShowForm(true);
    };

    const handleToggle = async (id) => {
        await labPackageApi.toggle(id);
        load();
    };

    const handleDelete = async (id) => {
        if (!confirm("Delete this package? Already-ordered packages keep their snapshot.")) return;
        await labPackageApi.delete(id);
        load();
    };

    const filtered =
        filterCat === "ALL" ? packages : packages.filter((p) => p.category === filterCat);

    return (
        <div className="hms-checkup-page">
            <div className="hms-checkup-header">
                <div>
                    <h1 className="hms-checkup-header__title">Lab Packages</h1>
                    <p className="hms-checkup-header__sub">
                        Bundle lab + radiology investigations into combo-priced packages
                    </p>
                </div>
                <button
                    onClick={() => {
                        setEditing(null);
                        setShowForm(true);
                    }}
                    className="hms-btn-primary"
                >
                    <Plus className="w-4 h-4" /> New Package
                </button>
            </div>

            {/* Category filter */}
            <div className="hms-checkup-pkg-cat-row">
                <button
                    onClick={() => setFilterCat("ALL")}
                    className={`hms-checkup-pkg-cat-pill ${filterCat === "ALL" ? "is-on" : ""}`}
                >
                    All ({packages.length})
                </button>
                {CATEGORIES.filter((c) => packages.some((p) => p.category === c.value)).map((c) => (
                    <button
                        key={c.value}
                        onClick={() => setFilterCat(c.value)}
                        className={`hms-checkup-pkg-cat-pill ${
                            filterCat === c.value ? "is-on" : ""
                        }`}
                    >
                        {c.label}
                    </button>
                ))}
            </div>

            {loading ? (
                <div className="hms-checkup-pkg-list">
                    {[1, 2, 3].map((i) => (
                        <div key={i} className="hms-checkup-pkg-skel" />
                    ))}
                </div>
            ) : filtered.length === 0 ? (
                <div className="hms-checkup-pkg-empty">
                    <Package className="w-12 h-12 opacity-25" />
                    <p className="hms-checkup-pkg-empty__title">No lab packages yet</p>
                    <p className="hms-checkup-pkg-empty__sub">
                        Create your first lab investigation bundle to get started
                    </p>
                </div>
            ) : (
                <div className="hms-checkup-pkg-list">
                    {filtered.map((pkg) => (
                        <PackageCard
                            key={pkg.id}
                            pkg={pkg}
                            onEdit={() => handleEdit(pkg)}
                            onToggle={() => handleToggle(pkg.id)}
                            onDelete={() => handleDelete(pkg.id)}
                        />
                    ))}
                </div>
            )}

            {showForm && (
                <PackageFormModal
                    initial={editing}
                    hospitalId={hospitalId}
                    onClose={() => {
                        setShowForm(false);
                        setEditing(null);
                    }}
                    onSaved={load}
                />
            )}
        </div>
    );
}
