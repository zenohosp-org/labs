import { useEffect, useState } from "react";
import {
    Plus,
    Edit2,
    Trash2,
    FileSignature,
    AlertTriangle,
    MoreHorizontal,
    Eye,
} from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import { useNotification } from "@/context/NotificationContext";
import { reportTemplateApi } from "@/api/labsClient";
import {
    Alert,
    Badge,
    Button,
    FormGroup,
    Input,
    Menu,
    Modal,
    PageHeader,
    Select,
    Table,
} from "@/components/ui";
import ReportTemplatePreview from "@/components/ReportTemplatePreview";

const DISCIPLINE_OPTIONS = [
    { value: "", label: "— Applies to all —" },
    { value: "PATHOLOGY", label: "Pathology" },
    { value: "RADIOLOGY", label: "Radiology" },
    { value: "CYTOLOGY", label: "Cytology" },
    { value: "HISTOPATHOLOGY", label: "Histopathology" },
];

const EMPTY = {
    id: null,
    name: "",
    discipline: "",
    isDefault: true,
    logoUrl: "",
    headerHtml: "",
    footerHtml: "",
    accentColor: "#14b8a6",
    signatoryName: "",
    signatoryQualification: "",
    signatoryRegistration: "",
    signatureImageUrl: "",
    portalBaseUrl: "",
    active: true,
};

/**
 * Report Template admin — per-hospital branding for the PDF lab report.
 * Each template carries the logo + header + footer + accent + signatory
 * details that the PDF renderer pulls in at sign time.
 *
 * Default template applies to every discipline; create a discipline-
 * specific row to override (e.g. histopathology letterhead with a
 * different signatory).
 */
export default function ReportTemplates() {
    const { notify } = useNotification();
    const { user } = useAuth();
    const [rows, setRows] = useState([]);
    const [loading, setLoading] = useState(true);
    const [editor, setEditor] = useState({ open: false, form: EMPTY });
    const [confirmDelete, setConfirmDelete] = useState(null);

    const load = async () => {
        setLoading(true);
        try {
            const data = await reportTemplateApi.list();
            setRows(data ?? []);
        } catch {
            notify("Failed to load report templates", "error");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => { load(); }, []);

    const openNew = () => setEditor({ open: true, form: { ...EMPTY } });
    const openEdit = (r) => setEditor({
        open: true,
        form: {
            id: r.id,
            name: r.name ?? "",
            discipline: r.discipline ?? "",
            isDefault: !!r.isDefault,
            logoUrl: r.logoUrl ?? "",
            headerHtml: r.headerHtml ?? "",
            footerHtml: r.footerHtml ?? "",
            accentColor: r.accentColor ?? "#14b8a6",
            signatoryName: r.signatoryName ?? "",
            signatoryQualification: r.signatoryQualification ?? "",
            signatoryRegistration: r.signatoryRegistration ?? "",
            signatureImageUrl: r.signatureImageUrl ?? "",
            portalBaseUrl: r.portalBaseUrl ?? "",
            active: r.active !== false,
        },
    });

    const set = (k, v) => setEditor((e) => ({ ...e, form: { ...e.form, [k]: v } }));

    const save = async () => {
        if (!editor.form.name.trim()) {
            notify("Name is required", "error");
            return;
        }
        const payload = {
            ...editor.form,
            discipline: editor.form.discipline || null,
        };
        try {
            if (editor.form.id) {
                await reportTemplateApi.update(editor.form.id, payload);
                notify("Template updated", "success");
            } else {
                await reportTemplateApi.create(payload);
                notify("Template created", "success");
            }
            setEditor({ open: false, form: EMPTY });
            await load();
        } catch {
            notify("Save failed", "error");
        }
    };

    const remove = async () => {
        if (!confirmDelete) return;
        try {
            await reportTemplateApi.delete(confirmDelete.id);
            notify("Template deleted", "success");
            setConfirmDelete(null);
            await load();
        } catch {
            notify("Delete failed", "error");
        }
    };

    const columns = [
        {
            header: "Template",
            width: "26%",
            render: (r) => (
                <div className="flex flex-col">
                    <span className="font-bold text-gray-900 text-14">{r.name}</span>
                    {r.isDefault && (
                        <span className="text-11 text-emerald-700">DEFAULT</span>
                    )}
                </div>
            ),
        },
        {
            header: "Scope",
            width: "14%",
            render: (r) =>
                r.discipline ? (
                    <Badge tone="info" soft>{r.discipline}</Badge>
                ) : (
                    <span className="text-12 text-gray-500">All disciplines</span>
                ),
        },
        {
            header: "Signatory",
            width: "22%",
            render: (r) => (
                <div className="flex flex-col text-12">
                    <span className="text-gray-800">{r.signatoryName || "—"}</span>
                    <span className="text-gray-500">
                        {r.signatoryQualification}
                        {r.signatoryRegistration && (
                            <span> · Reg. {r.signatoryRegistration}</span>
                        )}
                    </span>
                </div>
            ),
        },
        {
            header: "Accent",
            width: "10%",
            render: (r) => (
                <div className="flex items-center gap-2">
                    <span
                        style={{
                            display: "inline-block",
                            width: 14,
                            height: 14,
                            background: r.accentColor || "#14b8a6",
                            borderRadius: 3,
                            border: "1px solid #ccc",
                        }}
                    />
                    <code className="text-11 text-gray-500">{r.accentColor || "#14b8a6"}</code>
                </div>
            ),
        },
        {
            header: "Logo",
            width: "12%",
            render: (r) =>
                r.logoUrl ? (
                    <img
                        src={r.logoUrl}
                        alt="logo"
                        style={{ maxHeight: 28, maxWidth: 80, objectFit: "contain" }}
                    />
                ) : (
                    <span className="text-12 text-gray-300">—</span>
                ),
        },
        {
            header: "Status",
            width: "8%",
            render: (r) => (
                <Badge tone={r.active ? "success" : "danger"} soft>
                    {r.active ? "Active" : "Inactive"}
                </Badge>
            ),
        },
        {
            header: "",
            width: "8%",
            align: "right",
            render: (r) => (
                <Menu
                    triggerIcon={<MoreHorizontal size={18} />}
                    triggerLabel="Row actions"
                    align="right"
                    items={[
                        { label: "Edit", icon: <Edit2 size={14} />, onClick: () => openEdit(r) },
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

    return (
        <div className="flex flex-col gap-4">
            <PageHeader
                title={
                    <span className="inline-flex items-center gap-3">
                        Report Templates
                        <Badge tone="info">{rows.length} templates</Badge>
                    </span>
                }
                actions={
                    <Button variant="primary" onClick={openNew}>
                        <Plus size={14} strokeWidth={2.4} /> New template
                    </Button>
                }
            />

            <div className="hms-page-content">
                <Alert tone="info">
                    Each template controls the PDF lab report's logo, accent colour,
                    header / footer HTML, and signatory line. A discipline-specific row
                    overrides the default for that discipline (e.g. histopathology).
                </Alert>

                <Table
                    columns={columns}
                    data={rows}
                    loading={loading}
                    loadingMessage={<span className="text-gray-500">Loading templates…</span>}
                    emptyMessage={
                        <div className="hms-cell-empty">
                            <span className="hms-cell-empty__icon">
                                <FileSignature size={22} />
                            </span>
                            <div className="hms-cell-empty__text">
                                No templates configured. PDFs will render with a generic
                                fallback until you add one.
                            </div>
                        </div>
                    }
                />
            </div>

            <Modal
                isOpen={editor.open}
                onClose={() => setEditor({ open: false, form: EMPTY })}
                size="xl"
                className="rt-editor-modal"
                title={
                    <span className="inline-flex items-center gap-2">
                        <FileSignature size={16} />
                        {editor.form.id ? "Edit template" : "New template"}
                        <span className="text-12 font-normal text-gray-500 inline-flex items-center gap-1 ml-2">
                            <Eye size={11} /> Live preview on the right
                        </span>
                    </span>
                }
                footer={
                    <>
                        <Button variant="cancel" onClick={() => setEditor({ open: false, form: EMPTY })}>
                            Cancel
                        </Button>
                        <Button variant="primary" onClick={save}>
                            {editor.form.id ? "Save changes" : "Create template"}
                        </Button>
                    </>
                }
            >
                <style>{editorCss}</style>

                <div className="rt-editor-split">
                    {/* ── FORM PANE ────────────────────────────────────── */}
                    <div className="rt-editor-form">
                        <div className="grid grid-cols-3 gap-3">
                            <FormGroup label="Template name *">
                                <Input
                                    value={editor.form.name}
                                    onChange={(e) => set("name", e.target.value)}
                                    placeholder="e.g. Default Pathology Report"
                                    autoFocus
                                />
                            </FormGroup>
                            <FormGroup label="Discipline">
                                <Select
                                    value={editor.form.discipline}
                                    onChange={(e) => set("discipline", e.target.value)}
                                    options={DISCIPLINE_OPTIONS}
                                />
                            </FormGroup>
                            <FormGroup label="Accent colour">
                                <div className="rt-color-row">
                                    <Input
                                        type="color"
                                        value={editor.form.accentColor || "#14b8a6"}
                                        onChange={(e) => set("accentColor", e.target.value)}
                                    />
                                    <code className="rt-color-code">
                                        {(editor.form.accentColor || "#14b8a6").toUpperCase()}
                                    </code>
                                </div>
                            </FormGroup>
                        </div>

                        <div className="grid grid-cols-2 gap-3">
                            <FormGroup label="Logo URL" hint="Public URL — Supabase storage / CDN. Max width 200px on PDF.">
                                <Input
                                    value={editor.form.logoUrl}
                                    onChange={(e) => set("logoUrl", e.target.value)}
                                    placeholder="https://…/logo.png"
                                />
                            </FormGroup>
                            <FormGroup label="Portal base URL" hint="Used to build the verify QR — e.g. https://labs.zenohosp.com">
                                <Input
                                    value={editor.form.portalBaseUrl}
                                    onChange={(e) => set("portalBaseUrl", e.target.value)}
                                    placeholder="https://labs.zenohosp.com"
                                />
                            </FormGroup>
                        </div>

                        <FormGroup
                            label="Header HTML"
                            hint="Free HTML below the logo (clinic address, GSTIN, contact). Plain text also works."
                        >
                            <textarea
                                rows={3}
                                className="hms-input rt-monospace"
                                value={editor.form.headerHtml}
                                onChange={(e) => set("headerHtml", e.target.value)}
                                placeholder="<div>123 Main Rd, Chennai — 600001<br/>GSTIN 33ABCDE1234F1Z5</div>"
                            />
                        </FormGroup>

                        <FormGroup label="Footer HTML" hint="Disclaimer / contact line at the bottom of the report.">
                            <textarea
                                rows={2}
                                className="hms-input rt-monospace"
                                value={editor.form.footerHtml}
                                onChange={(e) => set("footerHtml", e.target.value)}
                                placeholder="<em>This report is for clinical purposes only — consult your doctor before acting on it.</em>"
                            />
                        </FormGroup>

                        <div className="grid grid-cols-3 gap-3">
                            <FormGroup label="Signatory name">
                                <Input
                                    value={editor.form.signatoryName}
                                    onChange={(e) => set("signatoryName", e.target.value)}
                                    placeholder="Dr. Vasantha Kumari"
                                />
                            </FormGroup>
                            <FormGroup label="Qualification">
                                <Input
                                    value={editor.form.signatoryQualification}
                                    onChange={(e) => set("signatoryQualification", e.target.value)}
                                    placeholder="MD (Pathology)"
                                />
                            </FormGroup>
                            <FormGroup label="Reg. number">
                                <Input
                                    value={editor.form.signatoryRegistration}
                                    onChange={(e) => set("signatoryRegistration", e.target.value)}
                                    placeholder="MCI 123456"
                                />
                            </FormGroup>
                        </div>

                        <FormGroup label="Signature image URL" hint="Scanned signature shown above the signatory name on the PDF.">
                            <Input
                                value={editor.form.signatureImageUrl}
                                onChange={(e) => set("signatureImageUrl", e.target.value)}
                                placeholder="https://…/signature.png"
                            />
                        </FormGroup>

                        <div className="flex gap-6 mt-2 text-13 text-gray-700">
                            <label className="inline-flex items-center gap-2">
                                <input
                                    type="checkbox"
                                    checked={!!editor.form.isDefault}
                                    onChange={(e) => set("isDefault", e.target.checked)}
                                />
                                Default for this scope
                            </label>
                            <label className="inline-flex items-center gap-2">
                                <input
                                    type="checkbox"
                                    checked={!!editor.form.active}
                                    onChange={(e) => set("active", e.target.checked)}
                                />
                                Active
                            </label>
                        </div>
                    </div>

                    {/* ── PREVIEW PANE ─────────────────────────────────── */}
                    <div className="rt-editor-preview">
                        <div className="rt-preview-header">
                            <Eye size={12} /> Live preview · A4
                        </div>
                        <div className="rt-preview-frame">
                            <ReportTemplatePreview
                                template={{
                                    ...editor.form,
                                    hospitalName: user?.hospitalName || "Hospital",
                                }}
                                scale={0.58}
                            />
                        </div>
                        <div className="rt-preview-foot">
                            Mirrors the PDF renderer · sample patient data ·
                            updates as you type
                        </div>
                    </div>
                </div>
            </Modal>

            <Modal
                isOpen={!!confirmDelete}
                onClose={() => setConfirmDelete(null)}
                size="sm"
                title="Delete template"
                footer={
                    <>
                        <Button variant="cancel" onClick={() => setConfirmDelete(null)}>Cancel</Button>
                        <Button variant="danger" onClick={remove}>Delete</Button>
                    </>
                }
            >
                <Alert tone="danger" icon={<AlertTriangle size={16} />}>
                    Already-signed reports keep their original signatory snapshot. Future
                    renders fall back to the default template (or the auto-fallback if
                    you delete that too).
                </Alert>
                {confirmDelete && (
                    <div className="text-13 text-gray-700 mt-2">
                        <strong>{confirmDelete.name}</strong>
                    </div>
                )}
            </Modal>
        </div>
    );
}

// Scoped to the editor modal so it doesn't leak into the rest of the app.
// Prod-grade clean movement notes:
//   - the split layout uses flex with min-width:0 on the form pane so
//     long URLs don't blow out the right pane width
//   - the preview frame has its own scroll container — A4 fits at 0.58×
//     in ~480 px wide pane without horizontal scroll
//   - subtle background diff (white form / soft slate preview) so the
//     eye reads the two zones as form/output without a heavy divider
//   - the preview pane's children animate via the in-component CSS
//     (accent + logo + signature transitions live in
//     ReportTemplatePreview)
const editorCss = `
.rt-editor-modal { max-width: 1180px; width: calc(100vw - 48px); }
.rt-editor-modal .hms-modal-body { padding: 0; }
.rt-editor-split {
    display: grid;
    grid-template-columns: minmax(0, 1.05fr) minmax(420px, 0.95fr);
    min-height: 70vh;
    max-height: calc(100vh - 220px);
}
.rt-editor-form {
    padding: 16px 18px;
    overflow-y: auto;
    border-right: 1px solid #e5e7eb;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 12px;
    background: #ffffff;
}
.rt-editor-preview {
    background: #f8fafc;
    display: flex;
    flex-direction: column;
    min-width: 0;
}
.rt-preview-header {
    padding: 8px 14px;
    border-bottom: 1px solid #e5e7eb;
    background: white;
    font-size: 11px;
    font-weight: 600;
    color: #64748b;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    display: inline-flex;
    align-items: center;
    gap: 6px;
}
.rt-preview-frame {
    flex: 1; overflow: auto;
    background: #f1f5f9;
    transition: background 250ms ease;
}
.rt-preview-foot {
    padding: 6px 12px;
    border-top: 1px solid #e5e7eb;
    background: white;
    font-size: 10px;
    color: #94a3b8;
    text-align: center;
}
.rt-color-row { display: flex; align-items: center; gap: 8px; }
.rt-color-row input[type="color"] {
    width: 44px; height: 32px; padding: 0; border-radius: 6px;
    cursor: pointer;
    transition: transform 120ms ease, box-shadow 200ms ease;
}
.rt-color-row input[type="color"]:hover { transform: scale(1.05); }
.rt-color-code {
    font-size: 11px; color: #475569; font-family: ui-monospace, monospace;
}
.rt-monospace {
    font-family: ui-monospace, SFMono-Regular, "Menlo", monospace;
    font-size: 12px;
}

/* Mobile/narrow: stack form on top, preview below */
@media (max-width: 900px) {
    .rt-editor-split {
        grid-template-columns: 1fr;
        max-height: none;
    }
    .rt-editor-form { border-right: none; border-bottom: 1px solid #e5e7eb; }
    .rt-editor-preview { min-height: 60vh; }
}
`;
