import { useCallback, useEffect, useMemo, useState } from "react";
import {
    Beaker,
    Search,
    Loader2,
    CheckCircle2,
    XCircle,
    Inbox,
    Clock,
    Printer,
    Droplet,
    User as UserIcon,
} from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import { useNotification } from "@/context/NotificationContext";
import { collectionApi, labApi, specimenApi } from "@/api/labsClient";
import { printBarcodes } from "@/utils/printBarcodes";
import { fmtId } from "@/utils/idFormat";
import { fmtDateTime } from "@/utils/date";

// One day in YYYY-MM-DD — Vite-safe (no Date.now polyfill needed).
const todayIso = () => new Date().toISOString().slice(0, 10);

// Order-level status pill — same vocab as LabQueue + an extra "Pending" for
// rows synthesized from PENDING_COLLECTION orders that don't have a specimen
// row yet (the Mark Collected affordance lives there).
const STATUS_META = {
    PENDING_COLLECTION: { label: "Pending Collection", cls: "is-pending" },
    AWAITING_REPORT:    { label: "Collected",          cls: "is-awaiting" },
    IN_PROGRESS:        { label: "In Progress",        cls: "is-progress" },
    REPORT_GENERATED:   { label: "Reported",           cls: "is-reported" },
    BILLED:             { label: "Billed",             cls: "is-billed"   },
    CANCELLED:          { label: "Cancelled",          cls: "is-billed"   },
};

/**
 * Collections — read-only specimen log + collect-fallback affordance.
 *
 * Two row types in one list:
 *   1. specimen — a collected tube. Print Barcode action.
 *   2. order    — a still-pending order that hasn't been collected yet
 *                 (synthesized from PENDING_COLLECTION lab_orders for the
 *                 same window). Mark Collected action. Once collected, the
 *                 row flips to the specimen shape on next refresh.
 *
 * Defaults to today's window. Search filters by patient name, UHID, test
 * name, accession, or barcode — bench tech can hand-scan a barcode into
 * the search box to surface a single row instantly.
 */
export default function CollectionQueue() {
    const { user } = useAuth();
    const { notify } = useNotification();

    const [from, setFrom] = useState(todayIso);
    const [to, setTo] = useState(todayIso);
    const [search, setSearch] = useState("");
    const [specimens, setSpecimens] = useState([]);
    const [pendingOrders, setPendingOrders] = useState([]);
    const [loading, setLoading] = useState(true);
    const [actingOn, setActingOn] = useState(null);

    const load = useCallback(async () => {
        if (!user?.hospitalId) return;
        setLoading(true);
        try {
            // Pending orders only loaded when the window includes today — older
            // orders that never got collected stay surfaced on Lab Queue.
            const includeToday = from <= todayIso() && todayIso() <= to;
            const [logRows, queueRows] = await Promise.all([
                collectionApi.log({ from, to }),
                includeToday ? collectionApi.queue() : Promise.resolve([]),
            ]);
            setSpecimens(Array.isArray(logRows) ? logRows : []);
            // The /queue endpoint groups by patient; flatten to one row per order
            // for the unified table.
            const flat = (queueRows || []).flatMap((p) =>
                (p.orders || []).map((o) => ({
                    rowType: "ORDER",
                    labOrderId: o.id,
                    serviceName: o.serviceName,
                    accessionNumber: o.accessionNumber,
                    priority: o.priority,
                    orderStatus: "PENDING_COLLECTION",
                    sampleType: o.sampleType,
                    patientId: p.patientId,
                    patientName: p.patientName,
                    patientUhid: p.patientUhid,
                    createdAt: o.createdAt,
                }))
            );
            setPendingOrders(flat);
        } catch (err) {
            notify(err?.response?.data?.message || "Failed to load collections", "error");
        } finally {
            setLoading(false);
        }
    }, [user?.hospitalId, from, to, notify]);

    useEffect(() => { load(); }, [load]);

    const handleMarkCollected = async (row) => {
        setActingOn(`order-${row.labOrderId}`);
        try {
            await labApi.markCollected(row.labOrderId);
            notify("Sample collected — specimen row created, barcode ready to print", "success");
            load();
        } catch (err) {
            notify(err?.response?.data?.message || "Failed to mark collected", "error");
        } finally {
            setActingOn(null);
        }
    };

    const handlePrintForSpecimen = (s) => {
        printBarcodes({
            patient: { name: s.patientName, uhid: s.patientUhid },
            specimens: [{
                barcode: s.barcode,
                containerType: s.containerType,
                volumeMl: s.volumeMl,
                accessionNumber: s.accessionNumber,
            }],
        });
    };

    // Fallback path: PENDING order has no specimen yet — fetch what the
    // markCollected just created on the backend, then print.
    const handlePrintForOrder = async (row) => {
        setActingOn(`order-${row.labOrderId}`);
        try {
            const created = await specimenApi.listForOrder(row.labOrderId);
            if (!created || created.length === 0) {
                notify("No specimen on this order yet — Mark Collected first", "warning");
                return;
            }
            printBarcodes({
                patient: { name: row.patientName, uhid: row.patientUhid },
                specimens: created.map((s) => ({
                    barcode: s.barcode,
                    containerType: s.containerType,
                    volumeMl: s.volumeMl,
                    accessionNumber: row.accessionNumber,
                })),
            });
        } catch (err) {
            notify(err?.response?.data?.message || "Failed to fetch specimen", "error");
        } finally {
            setActingOn(null);
        }
    };

    // Unified rows for the table — collected specimens (newest first), then
    // pending orders (still need to be collected). Search filter applies to
    // both halves.
    const rows = useMemo(() => {
        const q = search.trim().toLowerCase();
        const specimenRows = specimens.map((s) => ({ ...s, rowType: "SPECIMEN" }));
        const all = [...specimenRows, ...pendingOrders];
        if (!q) return all;
        return all.filter((r) =>
            (r.patientName || "").toLowerCase().includes(q) ||
            (r.patientUhid || "").toLowerCase().includes(q) ||
            (r.serviceName || "").toLowerCase().includes(q) ||
            (r.accessionNumber || "").toLowerCase().includes(q) ||
            (r.barcode || "").toLowerCase().includes(q)
        );
    }, [specimens, pendingOrders, search]);

    const collectedCount = specimens.length;
    const pendingCount = pendingOrders.length;
    const rejectedCount = specimens.filter((s) => s.rejected).length;

    return (
        <div className="hms-rad-page">
            <div className="hms-rad-page__head">
                <div>
                    <h1 className="hms-rad-page__title">
                        <Beaker className="w-5 h-5 hms-rad-page__title-icon" /> Collections
                    </h1>
                    <p className="hms-rad-page__sub">
                        Specimen log — every tube collected, with chain of custody and barcode reprint.
                        Pending orders shown for today so the bench can collect from here too.
                    </p>
                </div>
                <div className="hms-rad-page__chips">
                    <div className="hms-rad-chip-row">
                        <span className="hms-rad-chip is-emerald">
                            <CheckCircle2 className="w-3 h-3" /> {collectedCount} collected
                        </span>
                        <span className="hms-rad-chip is-amber">
                            <Clock className="w-3 h-3" /> {pendingCount} pending
                        </span>
                        {rejectedCount > 0 && (
                            <span className="hms-rad-chip is-rose">
                                <XCircle className="w-3 h-3" /> {rejectedCount} rejected
                            </span>
                        )}
                    </div>
                </div>
            </div>

            {/* Date range + search */}
            <div className="hms-rad-filterbar">
                <div className="hms-rad-priority-row" style={{ gap: 12 }}>
                    <label className="hms-rad-priority-btn" style={{ cursor: "default" }}>
                        From
                        <input
                            type="date"
                            value={from}
                            max={to}
                            onChange={(e) => setFrom(e.target.value)}
                            style={{ marginLeft: 8, border: "none", background: "transparent" }}
                        />
                    </label>
                    <label className="hms-rad-priority-btn" style={{ cursor: "default" }}>
                        To
                        <input
                            type="date"
                            value={to}
                            min={from}
                            onChange={(e) => setTo(e.target.value)}
                            style={{ marginLeft: 8, border: "none", background: "transparent" }}
                        />
                    </label>
                </div>
                <div className="hms-rad-search">
                    <Search className="w-4 h-4 hms-rad-search__icon" />
                    <input
                        className="hms-rad-search__input"
                        placeholder="Patient · UHID · test · accession · barcode"
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                    />
                </div>
            </div>

            <div className="hms-rad-section is-slate">
                <div className="hms-rad-section__head">
                    <p className="hms-rad-section__title">Specimens</p>
                    <p className="hms-rad-section__sub">
                        Each row is one tube. Pending rows are orders that haven't been collected yet.
                    </p>
                </div>
                {loading ? (
                    <div className="hms-rad-section__loading">
                        <Loader2 className="w-5 h-5 animate-spin text-gray-400" />
                    </div>
                ) : rows.length === 0 ? (
                    <div className="hms-rad-section__empty">
                        <Inbox className="w-5 h-5 hms-rad-section__empty-icon" />
                        <p className="hms-rad-section__empty-title">No specimens in this window</p>
                        <p className="hms-rad-section__empty-sub">
                            Try widening the date range, or hit Mark Collected on Lab Queue to add one.
                        </p>
                    </div>
                ) : (
                    <div className="hms-rad-section__list">
                        <div className="hms-rad-table-head">
                            {["Patient", "Investigation", "Sample / tube", "Accession", "Collected", "Status", ""].map((h) => (
                                <p key={h} className="hms-rad-table-head__cell">{h}</p>
                            ))}
                        </div>
                        {rows.map((row) => (
                            <SpecimenRow
                                key={row.rowType === "SPECIMEN" ? `s-${row.specimenId}` : `o-${row.labOrderId}`}
                                row={row}
                                actingOn={actingOn}
                                onMarkCollected={handleMarkCollected}
                                onPrint={row.rowType === "SPECIMEN" ? handlePrintForSpecimen : handlePrintForOrder}
                            />
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
}

function SpecimenRow({ row, actingOn, onMarkCollected, onPrint }) {
    const isSpecimen = row.rowType === "SPECIMEN";
    const status = isSpecimen ? row.orderStatus : "PENDING_COLLECTION";
    const meta = STATUS_META[status] || STATUS_META.PENDING_COLLECTION;
    const acting = actingOn === `order-${row.labOrderId}`;
    const printable = isSpecimen || status !== "PENDING_COLLECTION";

    return (
        <div className="hms-rad-row">
            <div className="hms-rad-patient">
                <div className="hms-rad-patient__avatar">
                    {row.patientName ? row.patientName[0] : <UserIcon className="w-4 h-4 text-gray-400" />}
                </div>
                <div>
                    <p className="hms-rad-patient__name">{row.patientName ?? "—"}</p>
                    <p className="hms-rad-patient__uhid">{fmtId(row.patientUhid)}</p>
                </div>
            </div>
            <div>
                <p className="hms-rad-row__svc-name">{row.serviceName ?? "—"}</p>
                {row.collectedByName && (
                    <p className="hms-rad-row__svc-bill">By {row.collectedByName}</p>
                )}
            </div>
            <div>
                {row.containerType || row.sampleType ? (
                    <div className="hms-rad-tech">
                        <Droplet className="w-3 h-3 hms-rad-tech__icon" />
                        <p>
                            {row.containerType ?? row.sampleType}
                            {row.volumeMl ? ` · ${row.volumeMl} mL` : ""}
                        </p>
                    </div>
                ) : (
                    <p className="hms-rad-tech-empty">—</p>
                )}
                {row.barcode && (
                    <p className="hms-rad-row__svc-bill" style={{ fontFamily: "monospace", fontSize: 11 }}>
                        {row.barcode}
                    </p>
                )}
            </div>
            <div>
                {row.accessionNumber ? (
                    <code className="hms-rad-row__svc-bill">{row.accessionNumber}</code>
                ) : (
                    <p className="hms-rad-tech-empty">—</p>
                )}
            </div>
            <div>
                {isSpecimen && row.collectedAt ? (
                    <p className="hms-rad-row__date" style={{ display: "flex", gap: 4, alignItems: "center" }}>
                        <Clock className="w-3 h-3 shrink-0" />
                        {fmtDateTime(row.collectedAt)}
                    </p>
                ) : (
                    <p className="hms-rad-tech-empty">—</p>
                )}
            </div>
            <div>
                {row.rejected ? (
                    <span className="hms-lab-status-badge is-billed">
                        <span className="hms-lab-status-badge__dot" /> Rejected
                    </span>
                ) : (
                    <span className={`hms-lab-status-badge ${meta.cls}`}>
                        <span className="hms-lab-status-badge__dot" /> {meta.label}
                    </span>
                )}
            </div>
            <div className="hms-rad-row__action" style={{ display: "flex", gap: 6 }}>
                {!isSpecimen && (
                    <button
                        type="button"
                        onClick={() => onMarkCollected(row)}
                        disabled={acting}
                        className="hms-rad-row__view-btn"
                        title="Mark sample collected — also creates the specimen row + barcode"
                    >
                        <CheckCircle2 className="w-3 h-3" />
                        {acting ? "Working…" : "Mark Collected"}
                    </button>
                )}
                {printable && (
                    <button
                        type="button"
                        onClick={() => onPrint(row)}
                        disabled={acting || row.rejected}
                        className="hms-rad-row__view-btn"
                        title="Print specimen label"
                    >
                        <Printer className="w-3 h-3" />
                        Print
                    </button>
                )}
            </div>
        </div>
    );
}
