import { useEffect, useState, useMemo } from "react";
import {
    Plus,
    Beaker,
    CheckCircle2,
    Inbox,
    ShieldOff,
    XCircle,
    Clock,
    AlertTriangle,
    Copy,
    Loader2,
} from "lucide-react";
import { useNotification } from "@/context/NotificationContext";
import { specimenApi, rejectionReasonApi } from "@/api/labsClient";
import {
    Alert,
    Badge,
    Button,
    FormGroup,
    Input,
    Modal,
    Select,
} from "@/components/ui";

const CONTAINER_OPTIONS = [
    { value: "", label: "Auto" },
    { value: "EDTA", label: "EDTA (CBC)" },
    { value: "CITRATE", label: "Citrate (Coagulation)" },
    { value: "HEPARIN", label: "Heparin" },
    { value: "PLAIN", label: "Plain / Serum" },
    { value: "FLUORIDE", label: "Fluoride (Glucose)" },
    { value: "URINE_CUP", label: "Urine cup" },
    { value: "STOOL_CUP", label: "Stool cup" },
    { value: "SWAB", label: "Swab" },
    { value: "OTHER", label: "Other" },
];

const STAGE_TONE = {
    PENDING_COLLECTION: "neutral",
    COLLECTED: "warning",
    RECEIVED: "info",
    ACCESSIONED: "success",
    REJECTED: "danger",
};

/**
 * Specimens drawer for a single lab order.
 *
 * Lists every container, surfaces inline "Receive", "Accession", "Reject"
 * actions, and lets staff add another tube on the fly (CBC + LFT done from
 * the same draw on two tubes). Backward-compat: the legacy "Mark Collected"
 * button on LabQueue still works without ever opening this modal — it
 * auto-creates a default specimen behind the scenes.
 */
export default function SpecimensModal({ order, onClose, onChanged }) {
    const { notify } = useNotification();
    const [rows, setRows] = useState([]);
    const [loading, setLoading] = useState(true);
    const [reasons, setReasons] = useState([]);
    const [add, setAdd] = useState({
        open: false,
        containerType: "",
        additive: "",
        volumeMl: "",
        collectionSite: "",
        notes: "",
        collectedByName: "",
        saving: false,
    });
    const [reject, setReject] = useState({
        open: false,
        target: null,
        reasonCode: "",
        reasonNotes: "",
        saving: false,
    });
    const [actingId, setActingId] = useState(null);

    const load = async () => {
        if (!order?.id) return;
        setLoading(true);
        try {
            const data = await specimenApi.listForOrder(order.id);
            setRows(data ?? []);
        } catch {
            notify("Failed to load specimens", "error");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        load();
        rejectionReasonApi
            .list(true)
            .then((r) => setReasons(r ?? []))
            .catch(() => {});
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [order?.id]);

    const sortedRows = useMemo(
        () => [...rows].sort((a, b) => (a.id < b.id ? -1 : 1)),
        [rows],
    );

    const handleAdd = async () => {
        setAdd((a) => ({ ...a, saving: true }));
        try {
            const payload = {
                containerType: add.containerType || null,
                additive: add.additive || null,
                volumeMl: add.volumeMl === "" ? null : Number(add.volumeMl),
                collectionSite: add.collectionSite || null,
                notes: add.notes || null,
                collectedByName: add.collectedByName || null,
            };
            await specimenApi.create(order.id, payload);
            notify("Specimen added", "success");
            setAdd({
                open: false,
                containerType: "",
                additive: "",
                volumeMl: "",
                collectionSite: "",
                notes: "",
                collectedByName: "",
                saving: false,
            });
            await load();
            onChanged?.();
        } catch {
            notify("Failed to add specimen", "error");
            setAdd((a) => ({ ...a, saving: false }));
        }
    };

    const handleReceive = async (row) => {
        setActingId(row.id);
        try {
            await specimenApi.receive(row.id, {});
            notify(`Specimen ${row.barcode} received`, "success");
            await load();
            onChanged?.();
        } catch {
            notify("Failed to mark received", "error");
        } finally {
            setActingId(null);
        }
    };

    const handleAccession = async (row) => {
        setActingId(row.id);
        try {
            await specimenApi.accession(row.id);
            notify(`Specimen ${row.barcode} accessioned`, "success");
            await load();
            onChanged?.();
        } catch {
            notify("Failed to accession", "error");
        } finally {
            setActingId(null);
        }
    };

    const handleReject = async () => {
        if (!reject.reasonCode) {
            notify("Pick a rejection reason", "error");
            return;
        }
        setReject((r) => ({ ...r, saving: true }));
        try {
            await specimenApi.reject(reject.target.id, {
                reasonCode: reject.reasonCode,
                reasonNotes: reject.reasonNotes || null,
            });
            notify("Specimen rejected — audit trail updated", "success");
            setReject({ open: false, target: null, reasonCode: "", reasonNotes: "", saving: false });
            await load();
            onChanged?.();
        } catch {
            notify("Failed to reject", "error");
            setReject((r) => ({ ...r, saving: false }));
        }
    };

    const copy = (text) => {
        if (!text) return;
        navigator.clipboard?.writeText(text);
        notify("Copied", "info");
    };

    return (
        <>
            <Modal
                isOpen
                onClose={onClose}
                size="xl"
                title={
                    <span className="inline-flex items-center gap-2">
                        <Beaker size={18} /> Specimens · {order.patientName}
                        {order.accessionNumber && (
                            <Badge tone="info" soft>
                                {order.accessionNumber}
                            </Badge>
                        )}
                    </span>
                }
                footer={
                    <>
                        <Button variant="cancel" onClick={onClose}>
                            Close
                        </Button>
                        <Button
                            variant="primary"
                            onClick={() => setAdd((a) => ({ ...a, open: true }))}
                        >
                            <Plus size={14} /> Add specimen
                        </Button>
                    </>
                }
            >
                <Alert tone="info">
                    Each row is one physical container. Chain of custody runs{" "}
                    <strong>collected → received → accessioned</strong>; reject ends the chain
                    with a code (audit-logged).
                </Alert>

                {loading ? (
                    <div className="flex items-center justify-center py-8">
                        <Loader2 className="w-5 h-5 animate-spin text-gray-400" />
                    </div>
                ) : sortedRows.length === 0 ? (
                    <div className="text-center py-8">
                        <Beaker className="w-8 h-8 mx-auto text-gray-300" />
                        <p className="text-13 text-gray-500 mt-2">
                            No specimens yet. Hit <strong>Add specimen</strong> when the
                            sample is drawn — barcode + audit log are generated automatically.
                        </p>
                    </div>
                ) : (
                    <div className="flex flex-col gap-2">
                        {sortedRows.map((r) => (
                            <SpecimenRow
                                key={r.id}
                                row={r}
                                acting={actingId === r.id}
                                onReceive={handleReceive}
                                onAccession={handleAccession}
                                onReject={(row) =>
                                    setReject({
                                        open: true,
                                        target: row,
                                        reasonCode: "",
                                        reasonNotes: "",
                                        saving: false,
                                    })
                                }
                                onCopy={copy}
                            />
                        ))}
                    </div>
                )}
            </Modal>

            <Modal
                isOpen={add.open}
                onClose={() => !add.saving && setAdd((a) => ({ ...a, open: false }))}
                size="md"
                title="Add specimen"
                footer={
                    <>
                        <Button
                            variant="cancel"
                            onClick={() => setAdd((a) => ({ ...a, open: false }))}
                            disabled={add.saving}
                        >
                            Cancel
                        </Button>
                        <Button variant="primary" onClick={handleAdd} disabled={add.saving}>
                            {add.saving ? "Saving…" : "Add specimen"}
                        </Button>
                    </>
                }
            >
                <div className="grid grid-cols-2 gap-3">
                    <FormGroup label="Container type">
                        <Select
                            value={add.containerType}
                            onChange={(e) =>
                                setAdd((a) => ({ ...a, containerType: e.target.value }))
                            }
                            options={CONTAINER_OPTIONS}
                        />
                    </FormGroup>
                    <FormGroup label="Volume (mL)">
                        <Input
                            type="number"
                            step="any"
                            value={add.volumeMl}
                            onChange={(e) =>
                                setAdd((a) => ({ ...a, volumeMl: e.target.value }))
                            }
                            placeholder="3.0"
                        />
                    </FormGroup>
                </div>
                <div className="grid grid-cols-2 gap-3">
                    <FormGroup label="Additive (if not implied by container)">
                        <Input
                            value={add.additive}
                            onChange={(e) =>
                                setAdd((a) => ({ ...a, additive: e.target.value }))
                            }
                            placeholder="optional"
                        />
                    </FormGroup>
                    <FormGroup label="Collection site">
                        <Input
                            value={add.collectionSite}
                            onChange={(e) =>
                                setAdd((a) => ({ ...a, collectionSite: e.target.value }))
                            }
                            placeholder="e.g. Antecubital fossa"
                        />
                    </FormGroup>
                </div>
                <FormGroup label="Collected by (name)">
                    <Input
                        value={add.collectedByName}
                        onChange={(e) =>
                            setAdd((a) => ({ ...a, collectedByName: e.target.value }))
                        }
                        placeholder="Phlebotomist name"
                    />
                </FormGroup>
                <FormGroup label="Notes">
                    <Input
                        value={add.notes}
                        onChange={(e) => setAdd((a) => ({ ...a, notes: e.target.value }))}
                        placeholder="optional"
                    />
                </FormGroup>
                <p className="text-12 text-gray-500">
                    A unique barcode + QR payload is generated server-side.
                </p>
            </Modal>

            <Modal
                isOpen={reject.open}
                onClose={() =>
                    !reject.saving &&
                    setReject((r) => ({ ...r, open: false, target: null }))
                }
                size="md"
                title={
                    <span className="inline-flex items-center gap-2 text-rose-700">
                        <ShieldOff size={18} /> Reject specimen
                    </span>
                }
                footer={
                    <>
                        <Button
                            variant="cancel"
                            onClick={() =>
                                setReject((r) => ({ ...r, open: false, target: null }))
                            }
                            disabled={reject.saving}
                        >
                            Cancel
                        </Button>
                        <Button
                            variant="danger"
                            onClick={handleReject}
                            disabled={reject.saving}
                        >
                            {reject.saving ? "Rejecting…" : "Reject specimen"}
                        </Button>
                    </>
                }
            >
                <Alert tone="warning" icon={<AlertTriangle size={16} />}>
                    Rejection is terminal — this specimen can't be revived. Add a new
                    specimen row after recollection.
                </Alert>
                {reject.target && (
                    <p className="text-13 text-gray-700 mb-2">
                        <strong>Barcode:</strong>{" "}
                        <code className="font-mono">{reject.target.barcode}</code>
                        {reject.target.containerType ? ` · ${reject.target.containerType}` : ""}
                    </p>
                )}
                <FormGroup label="Reason *">
                    <Select
                        value={reject.reasonCode}
                        onChange={(e) =>
                            setReject((r) => ({ ...r, reasonCode: e.target.value }))
                        }
                        options={[
                            { value: "", label: "Select a reason…" },
                            ...reasons.map((r) => ({ value: r.code, label: r.label })),
                        ]}
                    />
                </FormGroup>
                <FormGroup label="Notes">
                    <Input
                        value={reject.reasonNotes}
                        onChange={(e) =>
                            setReject((r) => ({ ...r, reasonNotes: e.target.value }))
                        }
                        placeholder="optional — describe the incident"
                    />
                </FormGroup>
            </Modal>
        </>
    );
}

function SpecimenRow({ row, acting, onReceive, onAccession, onReject, onCopy }) {
    const stage =
        row.stage ||
        (row.rejected
            ? "REJECTED"
            : row.accessionedAt
            ? "ACCESSIONED"
            : row.receivedAt
            ? "RECEIVED"
            : row.collectedAt
            ? "COLLECTED"
            : "PENDING_COLLECTION");

    return (
        <div className="border border-gray-200 rounded-lg p-3 flex flex-col gap-2 bg-white">
            <div className="flex items-center justify-between gap-3 flex-wrap">
                <div className="flex items-center gap-2 flex-wrap">
                    <Badge tone={STAGE_TONE[stage] ?? "neutral"} soft>
                        {stage.replaceAll("_", " ")}
                    </Badge>
                    <code
                        className="font-mono text-13 text-gray-900 cursor-pointer hover:underline"
                        title="Click to copy"
                        onClick={() => onCopy(row.barcode)}
                    >
                        {row.barcode || "—"}
                        <Copy size={11} className="inline ml-1 text-gray-400" />
                    </code>
                    {row.containerType && (
                        <span className="text-12 text-gray-600">{row.containerType}</span>
                    )}
                    {row.volumeMl != null && (
                        <span className="text-12 text-gray-500">{row.volumeMl} mL</span>
                    )}
                </div>
                <div className="flex items-center gap-1.5">
                    {!row.rejected && !row.receivedAt && (
                        <Button
                            variant="secondary"
                            size="sm"
                            onClick={() => onReceive(row)}
                            disabled={acting}
                        >
                            <Inbox size={12} /> Receive
                        </Button>
                    )}
                    {!row.rejected && row.receivedAt && !row.accessionedAt && (
                        <Button
                            variant="primary"
                            size="sm"
                            onClick={() => onAccession(row)}
                            disabled={acting}
                        >
                            <CheckCircle2 size={12} /> Accession
                        </Button>
                    )}
                    {!row.rejected && (
                        <Button
                            variant="danger"
                            size="sm"
                            onClick={() => onReject(row)}
                            disabled={acting}
                        >
                            <XCircle size={12} /> Reject
                        </Button>
                    )}
                </div>
            </div>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-12 text-gray-600">
                <Timestamp icon={Clock} label="Collected" at={row.collectedAt} by={row.collectedByName} />
                <Timestamp icon={Inbox} label="Received" at={row.receivedAt} />
                <Timestamp icon={CheckCircle2} label="Accessioned" at={row.accessionedAt} />
                {row.rejected && (
                    <Timestamp
                        icon={ShieldOff}
                        label="Rejected"
                        at={row.rejectedAt}
                        extra={row.rejectionReasonCode}
                    />
                )}
            </div>
            {row.rejected && row.rejectionNotes && (
                <p className="text-12 text-rose-700">{row.rejectionNotes}</p>
            )}
        </div>
    );
}

function Timestamp({ icon: Icon, label, at, by, extra }) {
    if (!at) {
        return (
            <span className="text-gray-300 inline-flex items-center gap-1">
                <Icon size={11} /> {label}: —
            </span>
        );
    }
    const t = new Date(at);
    return (
        <span className="inline-flex items-center gap-1">
            <Icon size={11} className="text-gray-400" /> {label}:{" "}
            <strong className="text-gray-800">{t.toLocaleString()}</strong>
            {by && <span className="text-gray-500">· {by}</span>}
            {extra && <span className="text-rose-600 font-bold ml-1">{extra}</span>}
        </span>
    );
}
