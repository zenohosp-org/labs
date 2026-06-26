import { useEffect, useState } from "react";
import {
    History,
    Filter,
    User as UserIcon,
    Loader2,
    ChevronDown,
    ChevronRight,
} from "lucide-react";
import { useNotification } from "@/context/NotificationContext";
import { auditApi } from "@/api/labsClient";
import {
    Alert,
    Badge,
    Button,
    FormGroup,
    Input,
    PageHeader,
    Select,
} from "@/components/ui";

const OPERATION_TONE = {
    CREATE: "success",
    UPDATE: "info",
    STATUS_CHANGE: "info",
    REPORT_GENERATED: "success",
    VERIFY: "info",
    AUTHORISE: "success",
    AMEND: "warning",
    RECEIVE: "info",
    ACCESSION: "success",
    REJECT: "danger",
    DELETE: "danger",
    CANCEL: "danger",
    PANIC_CALL: "warning",
    TOGGLE: "neutral",
    AUTO_CREATE_ON_COLLECT: "neutral",
};

const ENTITY_OPTIONS = [
    { value: "", label: "All entity types" },
    { value: "LabOrder", label: "LabOrder" },
    { value: "LabSpecimen", label: "LabSpecimen" },
    { value: "LabTestResult", label: "LabTestResult" },
    { value: "LabService", label: "LabService" },
];

/**
 * Audit Trail viewer (Phase 0).
 *
 * Read-only — backend tenant-scopes the query from the JWT so a cross-
 * hospital lookup is impossible. Filter by entity type, drill into a
 * specific entity (Lab Order #123, Specimen #45), or scan the whole
 * timeline. JSONB before/after snapshots expand inline.
 */
export default function AuditTrail() {
    const { notify } = useNotification();
    const [rows, setRows] = useState([]);
    const [pageMeta, setPageMeta] = useState({ totalElements: 0, totalPages: 0, number: 0 });
    const [loading, setLoading] = useState(true);
    const [entityType, setEntityType] = useState("");
    const [entityId, setEntityId] = useState("");
    const [page, setPage] = useState(0);
    const [expanded, setExpanded] = useState(new Set());

    const load = async () => {
        setLoading(true);
        try {
            const data = await auditApi.list({
                entityType: entityType || undefined,
                entityId: entityType && entityId ? entityId : undefined,
                page,
                size: 50,
            });
            setRows(data?.content ?? []);
            setPageMeta({
                totalElements: data?.totalElements ?? 0,
                totalPages: data?.totalPages ?? 0,
                number: data?.number ?? 0,
            });
        } catch {
            notify("Failed to load audit log", "error");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        load();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [entityType, page]);

    const toggle = (id) => {
        setExpanded((s) => {
            const next = new Set(s);
            if (next.has(id)) next.delete(id);
            else next.add(id);
            return next;
        });
    };

    return (
        <div className="flex flex-col gap-4">
            <PageHeader
                title={
                    <span className="inline-flex items-center gap-3">
                        Audit Trail
                        <Badge tone="info">{pageMeta.totalElements} events</Badge>
                    </span>
                }
            />

            <div className="hms-page-content">
                <Alert tone="info">
                    Append-only audit row written on every labs-owned mutation — required
                    for HIPAA + NABL audit-trail compliance. Filter by entity to scope down.
                </Alert>

                <div className="flex flex-wrap items-end gap-3">
                    <FormGroup label="Entity type">
                        <Select
                            value={entityType}
                            onChange={(e) => {
                                setPage(0);
                                setEntityType(e.target.value);
                            }}
                            options={ENTITY_OPTIONS}
                        />
                    </FormGroup>
                    {entityType && (
                        <FormGroup label="Entity ID">
                            <Input
                                value={entityId}
                                onChange={(e) => setEntityId(e.target.value)}
                                onBlur={() => {
                                    setPage(0);
                                    load();
                                }}
                                placeholder="e.g. 123"
                            />
                        </FormGroup>
                    )}
                    <Button variant="secondary" onClick={() => { setPage(0); load(); }}>
                        <Filter size={14} /> Refresh
                    </Button>
                </div>

                {loading ? (
                    <div className="flex items-center justify-center py-8">
                        <Loader2 className="w-5 h-5 animate-spin text-gray-400" />
                    </div>
                ) : rows.length === 0 ? (
                    <div className="hms-cell-empty">
                        <span className="hms-cell-empty__icon">
                            <History size={22} />
                        </span>
                        <div className="hms-cell-empty__text">No audit events match.</div>
                    </div>
                ) : (
                    <div className="flex flex-col">
                        {rows.map((r) => (
                            <AuditRow
                                key={r.id}
                                r={r}
                                expanded={expanded.has(r.id)}
                                onToggle={() => toggle(r.id)}
                            />
                        ))}
                    </div>
                )}

                {!loading && pageMeta.totalPages > 1 && (
                    <div className="flex items-center justify-between pt-2 text-13 text-gray-600">
                        <span>
                            Page {pageMeta.number + 1} of {pageMeta.totalPages}
                        </span>
                        <div className="flex gap-2">
                            <Button
                                variant="secondary"
                                size="sm"
                                disabled={pageMeta.number === 0}
                                onClick={() => setPage((p) => Math.max(0, p - 1))}
                            >
                                Prev
                            </Button>
                            <Button
                                variant="secondary"
                                size="sm"
                                disabled={pageMeta.number >= pageMeta.totalPages - 1}
                                onClick={() => setPage((p) => p + 1)}
                            >
                                Next
                            </Button>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}

function AuditRow({ r, expanded, onToggle }) {
    const when = new Date(r.occurredAt).toLocaleString();
    const tone = OPERATION_TONE[r.operation] ?? "neutral";
    return (
        <div className="border-b border-gray-100 py-2.5">
            <button
                type="button"
                onClick={onToggle}
                className="w-full flex items-start justify-between text-left gap-3 hover:bg-gray-50 px-1 py-1 rounded"
            >
                <div className="flex items-start gap-2 flex-wrap min-w-0">
                    {expanded ? (
                        <ChevronDown size={14} className="text-gray-400 mt-0.5 shrink-0" />
                    ) : (
                        <ChevronRight size={14} className="text-gray-400 mt-0.5 shrink-0" />
                    )}
                    <Badge tone={tone} soft>{r.operation}</Badge>
                    <span className="font-mono text-13 text-gray-900">
                        {r.entityType} <span className="text-gray-400">#</span>{r.entityId}
                    </span>
                    {r.reasonCode && (
                        <Badge tone="warning" soft>{r.reasonCode}</Badge>
                    )}
                </div>
                <div className="text-12 text-gray-500 flex items-center gap-2 shrink-0">
                    <UserIcon size={11} /> {r.userEmail || "—"}
                    <span className="text-gray-300">·</span>
                    {when}
                </div>
            </button>
            {expanded && (
                <div className="px-1 pt-2 grid grid-cols-1 md:grid-cols-2 gap-2">
                    <JsonPanel title="Before" value={r.oldValueJson} />
                    <JsonPanel title="After" value={r.newValueJson} />
                    {r.reasonNotes && (
                        <div className="md:col-span-2 text-12 text-gray-700">
                            <strong>Notes:</strong> {r.reasonNotes}
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}

function JsonPanel({ title, value }) {
    let pretty = value;
    if (value && typeof value === "string") {
        try {
            pretty = JSON.stringify(JSON.parse(value), null, 2);
        } catch {
            pretty = value;
        }
    } else if (value) {
        try {
            pretty = JSON.stringify(value, null, 2);
        } catch {
            pretty = String(value);
        }
    }
    return (
        <div>
            <div className="text-11 font-bold text-gray-500 uppercase tracking-wide mb-1">{title}</div>
            <pre className="text-11 font-mono bg-gray-50 border border-gray-100 rounded p-2 overflow-x-auto max-h-48">
                {pretty ?? <span className="text-gray-300">—</span>}
            </pre>
        </div>
    );
}
