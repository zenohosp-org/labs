import { useState, useEffect, useCallback } from "react";
import { useAuth } from "@/context/AuthContext";
import { useNotification } from "@/context/NotificationContext";
import { labApi } from "@/api/labsClient";
import { fmtId } from "@/utils/idFormat";
import { fmtDate } from "@/utils/date";
import {
    TestTube,
    Clock,
    CheckCircle2,
    Search,
    Loader2,
    Stethoscope,
    AlertTriangle,
    Zap,
    Droplet,
} from "lucide-react";
import LabWriteReportModal from "./LabWriteReportModal";
import CollectPaymentModal from "../radiology/CollectPaymentModal";
import PaymentCell from "@/components/PaymentCell";
import { Menu } from "@/components/ui";
import { MoreHorizontal, CheckCircle2 as CheckCircle2Icon, PlayCircle, Edit3, XCircle } from "lucide-react";

// HMS-parity status badge — single pill shown in the STATUS column per row.
const STATUS_META = {
    PENDING_COLLECTION: { label: "Pending Collection", cls: "is-pending" },
    AWAITING_REPORT:    { label: "Awaiting Report",    cls: "is-awaiting" },
    IN_PROGRESS:        { label: "In Progress",        cls: "is-progress" },
    REPORT_GENERATED:   { label: "Reported",           cls: "is-reported" },
    BILLED:             { label: "Billed",             cls: "is-billed" },
    CANCELLED:          { label: "Cancelled",          cls: "is-billed" },
};

const PRIORITY_META = {
    ROUTINE: { cls: "is-routine", icon: Clock },
    URGENT: { cls: "is-urgent", icon: AlertTriangle },
    STAT: { cls: "is-stat", icon: Zap },
};

/**
 * Pathology queue — mirrors RadiologyQueue with lab semantics:
 *   PENDING_COLLECTION  → sample to be drawn (action: Mark Collected)
 *   AWAITING_REPORT     → in the analyser (action: Write Report)
 *
 * Orders here flow in from HMS (Consultation View + IPD Detail Pane → Labs tab)
 * via POST /api/lab. The bench technician collects + reports from this page;
 * generateReport auto-bills if a price was captured at order time.
 */
function LabQueue() {
    const { user } = useAuth();
    const { notify } = useNotification();
    const [stats, setStats] = useState({ pendingCollection: 0, awaitingReport: 0, reportGenerated: 0 });
    const [orders, setOrders] = useState([]);
    const [loading, setLoading] = useState(true);
    const [search, setSearch] = useState("");
    // Phase 9 — section tab. Replaces the priority filter; visual triage
    // by priority lives on each row badge instead. Default: pending (the
    // queue most likely to need immediate attention).
    const [activeSection, setActiveSection] = useState("pending");
    const [writeReport, setWriteReport] = useState(null);
    const [collectPayment, setCollectPayment] = useState(null);
    const [markingCollected, setMarkingCollected] = useState(null);
    const [actingOn, setActingOn] = useState(null);   // Phase 7 — receive / start

    const load = useCallback(async () => {
        if (!user?.hospitalId) return;
        setLoading(true);
        try {
            const [ordersData, statsData] = await Promise.all([
                labApi.list(user.hospitalId),
                labApi.getStats(user.hospitalId),
            ]);
            setOrders(ordersData);
            setStats(statsData);
        } catch {
            notify("Failed to load lab queue", "error");
        } finally {
            setLoading(false);
        }
    }, [user?.hospitalId, notify]);

    useEffect(() => {
        load();
    }, [load]);

    const handleMarkCollected = async (order) => {
        setMarkingCollected(order.id);
        try {
            await labApi.markCollected(order.id);
            notify("Sample collected — moved to Awaiting Report", "success");
            load();
        } catch {
            notify("Failed to update status", "error");
        } finally {
            setMarkingCollected(null);
        }
    };

    // Phase 7/9 — start (analyser run) + complete + cancel.
    const handleMarkStarted = async (order) => {
        setActingOn(order.id);
        try {
            await labApi.markStarted(order.id);
            notify("Test started — moved to In Progress", "success");
            load();
        } catch (err) {
            notify(err?.response?.data?.message || "Failed to start test", "error");
        } finally {
            setActingOn(null);
        }
    };

    // Phase 9 — single transition IN_PROGRESS → REPORT_GENERATED. Backend
    // rejects when neither findings text nor analyte rows exist; toast carries
    // the exact server message so the tech knows to click Write Report first.
    const handleMarkCompleted = async (order) => {
        setActingOn(order.id);
        try {
            await labApi.markCompleted(order.id);
            notify("Report completed — billed to active invoice if priced", "success");
            load();
        } catch (err) {
            notify(err?.response?.data?.message || "Failed to mark completed", "error");
        } finally {
            setActingOn(null);
        }
    };

    // Phase 9 — soft cancel. Confirm prompt is intentional: terminal action.
    const handleCancel = async (order) => {
        const reason = window.prompt(
            `Cancel ${order.serviceName} for ${order.patientName}?\n\nOptional reason (audit):`,
            ""
        );
        if (reason === null) return;  // user clicked Cancel on the prompt
        setActingOn(order.id);
        try {
            await labApi.cancelOrder(order.id, reason);
            notify("Order cancelled", "success");
            load();
        } catch (err) {
            notify(err?.response?.data?.message || "Failed to cancel order", "error");
        } finally {
            setActingOn(null);
        }
    };

    const pending = orders.filter((o) => o.status === "PENDING_COLLECTION");
    const awaiting = orders.filter((o) => o.status === "AWAITING_REPORT");
    const inProgress = orders.filter((o) => o.status === "IN_PROGRESS");   // Phase 7

    const applyFilters = (list) => {
        if (!search.trim()) return list;
        const q = search.toLowerCase();
        return list.filter(
            (o) =>
                o.patientName.toLowerCase().includes(q) ||
                o.patientUhid.toLowerCase().includes(q) ||
                o.serviceName.toLowerCase().includes(q) ||
                o.technicianName?.toLowerCase().includes(q)
        );
    };

    const filteredPending = applyFilters(pending);
    const filteredAwaiting = applyFilters(awaiting);
    const filteredInProgress = applyFilters(inProgress);   // Phase 7

    return (
        <div className="hms-rad-page">
            <div className="hms-rad-page__head">
                <div>
                    <h1 className="hms-rad-page__title">
                        <TestTube className="w-5 h-5 hms-rad-page__title-icon" /> Lab Queue
                    </h1>
                    <p className="hms-rad-page__sub">
                        Pathology investigations — sample collection, processing, and reporting
                    </p>
                </div>
                <div className="hms-rad-page__chips">
                    <div className="hms-rad-chip-row">
                        <span className="hms-rad-chip is-amber">
                            <Droplet className="w-3 h-3" /> {stats.pendingCollection} awaiting collection
                        </span>
                        <span className="hms-rad-chip is-slate">
                            <Clock className="w-3 h-3" /> {stats.awaitingReport} awaiting report
                        </span>
                        <span className="hms-rad-chip is-emerald">
                            <CheckCircle2 className="w-3 h-3" /> {stats.reportGenerated} done
                        </span>
                    </div>
                </div>
            </div>

            <div className="hms-rad-stat-grid">
                <div className="hms-rad-stat is-amber">
                    <div>
                        <p className="hms-rad-stat__label">Pending Collection</p>
                        <p className="hms-rad-stat__value">{stats.pendingCollection}</p>
                    </div>
                    <Droplet className="hms-rad-stat__icon" />
                </div>
                <div className="hms-rad-stat is-slate">
                    <div>
                        <p className="hms-rad-stat__label">Awaiting Reports</p>
                        <p className="hms-rad-stat__value">{stats.awaitingReport}</p>
                    </div>
                    <Clock className="hms-rad-stat__icon" />
                </div>
                <div className="hms-rad-stat is-emerald">
                    <div>
                        <p className="hms-rad-stat__label">Completed Today</p>
                        <p className="hms-rad-stat__value">{stats.reportGenerated}</p>
                    </div>
                    <CheckCircle2 className="hms-rad-stat__icon" />
                </div>
            </div>

            <div className="hms-rad-filterbar">
                <div className="hms-rad-section-tabs" role="tablist" aria-label="Queue section">
                    <button
                        type="button"
                        role="tab"
                        aria-selected={activeSection === "pending"}
                        onClick={() => setActiveSection("pending")}
                        className={`hms-rad-section-tab ${activeSection === "pending" ? "is-active" : ""}`}
                    >
                        <span className="hms-rad-section-tab__dot is-amber" />
                        Collection Queue
                        <span className="hms-rad-section-tab__count">{pending.length}</span>
                    </button>
                    <button
                        type="button"
                        role="tab"
                        aria-selected={activeSection === "awaiting"}
                        onClick={() => setActiveSection("awaiting")}
                        className={`hms-rad-section-tab ${activeSection === "awaiting" ? "is-active" : ""}`}
                    >
                        <span className="hms-rad-section-tab__dot is-slate" />
                        Awaiting Reports
                        <span className="hms-rad-section-tab__count">{awaiting.length}</span>
                    </button>
                    <button
                        type="button"
                        role="tab"
                        aria-selected={activeSection === "inprogress"}
                        onClick={() => setActiveSection("inprogress")}
                        className={`hms-rad-section-tab ${activeSection === "inprogress" ? "is-active" : ""}`}
                    >
                        <span className="hms-rad-section-tab__dot is-emerald" />
                        In Progress
                        <span className="hms-rad-section-tab__count">{inProgress.length}</span>
                    </button>
                </div>
                <div className="hms-rad-search">
                    <Search className="w-4 h-4 hms-rad-search__icon" />
                    <input
                        className="hms-rad-search__input"
                        placeholder="Search patient, test, UHID, technician…"
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                    />
                </div>
            </div>

            {loading ? (
                <div className="hms-rad-section__loading">
                    <Loader2 className="w-5 h-5 animate-spin text-gray-400" />
                </div>
            ) : activeSection === "pending" ? (
                <QueueSection
                    title="Collection Queue"
                    subtitle="Orders waiting for the sample to be drawn"
                    colorMod="is-amber"
                    orders={filteredPending}
                    emptyText="No pending collections"
                    emptySubtext="New orders from HMS will appear here"
                    actionLabel="Mark Collected"
                    actionMod="is-amber"
                    onAction={handleMarkCollected}
                    loadingId={markingCollected}
                    onCollect={(o) => setCollectPayment(o)}
                    onCancel={handleCancel}
                />
            ) : activeSection === "awaiting" ? (
                <QueueSection
                    title="Awaiting Reports"
                    subtitle="Sample collected — start the analyser when ready"
                    colorMod="is-slate"
                    orders={filteredAwaiting}
                    emptyText="No samples awaiting reports"
                    emptySubtext="Collected samples will appear here"
                    actionLabel="Start Test"
                    actionMod="is-indigo"
                    onAction={handleMarkStarted}
                    loadingId={actingOn}
                    onCollect={(o) => setCollectPayment(o)}
                    onCancel={handleCancel}
                />
            ) : (
                <QueueSection
                    title="In Progress"
                    subtitle="Analyser run started — write the report, then mark completed"
                    colorMod="is-emerald"
                    orders={filteredInProgress}
                    emptyText="No tests in progress"
                    emptySubtext="Started tests appear here until report is written"
                    actionLabel="Write Report"
                    actionMod="is-emerald"
                    onAction={(o) => setWriteReport(o)}
                    loadingId={actingOn}
                    onMarkCompleted={handleMarkCompleted}
                    onCollect={(o) => setCollectPayment(o)}
                    onCancel={handleCancel}
                />
            )}

            {writeReport && (
                <LabWriteReportModal
                    order={writeReport}
                    onClose={() => setWriteReport(null)}
                    onSaved={() => {
                        setWriteReport(null);
                        load();
                    }}
                />
            )}
            {collectPayment && (
                <CollectPaymentModal
                    order={collectPayment}
                    onClose={() => setCollectPayment(null)}
                    onPaid={() => {
                        setCollectPayment(null);
                        load();
                    }}
                />
            )}
        </div>
    );
}

function QueueSection({
    title,
    subtitle,
    colorMod,
    orders,
    emptyText,
    emptySubtext,
    actionLabel,
    actionMod,
    onAction,
    loadingId,
    onCollect,
    onMarkCompleted,    // Phase 9 — In Progress section only
    onCancel,           // Phase 9 — all active sections
}) {
    return (
        <div className={`hms-rad-section ${colorMod}`}>
            <div className="hms-rad-section__head">
                <p className="hms-rad-section__title">{title}</p>
                <p className="hms-rad-section__sub">{subtitle}</p>
            </div>
            {orders.length === 0 ? (
                <div className="hms-rad-section__empty">
                    <TestTube className="w-5 h-5 hms-rad-section__empty-icon" />
                    <p className="hms-rad-section__empty-title">{emptyText}</p>
                    <p className="hms-rad-section__empty-sub">{emptySubtext}</p>
                </div>
            ) : (
                <div className="hms-rad-section__list">
                    <div className="hms-rad-table-head">
                        {["Patient", "Investigation", "Sample", "Priority", "Payment", "Scheduled", "Status", ""].map((h) => (
                            <p key={h} className="hms-rad-table-head__cell">{h}</p>
                        ))}
                    </div>
                    {orders.map((order) => {
                        const pmeta = PRIORITY_META[order.priority];
                        const PIcon = pmeta.icon;
                        return (
                            <div key={order.id} className="hms-rad-row">
                                <div className="hms-rad-patient">
                                    <div className="hms-rad-patient__avatar">{order.patientName[0]}</div>
                                    <div>
                                        <p className="hms-rad-patient__name">
                                            {order.patientName}
                                            {order.admissionId && (
                                                <span className="hms-lab-ipd-badge" title="Inpatient — admission linked">IPD</span>
                                            )}
                                        </p>
                                        <p className="hms-rad-patient__uhid">{fmtId(order.patientUhid)}</p>
                                        {order.admissionNumber && (
                                            <p className="hms-rad-patient__uhid" title="IPD admission number — for cross-reference with HMS">
                                                ADM: <code>{order.admissionNumber}</code>
                                            </p>
                                        )}
                                        {order.accessionNumber && (
                                            <p className="hms-rad-patient__uhid" title="Lab accession — printed on specimen tube barcode">
                                                ACC: <code>{order.accessionNumber}</code>
                                            </p>
                                        )}
                                    </div>
                                </div>
                                <div>
                                    <p className="hms-rad-row__svc-name">{order.serviceName}</p>
                                    {order.referredByName && (
                                        <div className="hms-rad-row__svc-doc">
                                            <Stethoscope className="w-3 h-3 hms-rad-row__svc-doc-icon" />
                                            <p>{order.referredByName}</p>
                                        </div>
                                    )}
                                </div>
                                <div>
                                    {order.sampleType ? (
                                        <div className="hms-rad-tech">
                                            <Droplet className="w-3 h-3 hms-rad-tech__icon" />
                                            <p>{order.sampleType}</p>
                                        </div>
                                    ) : (
                                        <p className="hms-rad-tech-empty">—</p>
                                    )}
                                </div>
                                <div>
                                    <span className={`hms-rad-priority ${pmeta.cls}`}>
                                        <PIcon className="w-2 h-2" />
                                        {order.priority}
                                    </span>
                                </div>
                                <div>
                                    <PaymentCell order={order} onCollect={onCollect} />
                                </div>
                                <div>
                                    <p className="hms-rad-row__date-empty">{order.scheduledDate ? fmtDate(order.scheduledDate) : "—"}</p>
                                </div>
                                <div>
                                    {STATUS_META[order.status] && (
                                        <span className={`hms-lab-status-badge ${STATUS_META[order.status].cls}`}>
                                            <span className="hms-lab-status-badge__dot" />
                                            {STATUS_META[order.status].label}
                                        </span>
                                    )}
                                </div>
                                <div className="hms-rad-row__action">
                                    <Menu
                                        triggerLabel="Order actions"
                                        triggerIcon={<MoreHorizontal className="w-4 h-4" />}
                                        items={buildActionItems({
                                            order,
                                            actionLabel,
                                            onAction,
                                            loadingId,
                                            onMarkCompleted,
                                            onCancel,
                                        })}
                                    />
                                </div>
                            </div>
                        );
                    })}
                </div>
            )}
        </div>
    );
}

/**
 * Build the kebab menu items for one order row (Phase 9 — simplified):
 *
 *   Collection Queue  → Mark Collected · Cancel
 *   Awaiting Reports  → Start Test     · Cancel
 *   In Progress       → Write Report   · Mark Completed · Cancel
 *
 * Specimens tracker, Receive button, and Sign/Authorise/Amend ceremony were
 * retired in Phase 9 — kept the backend audit hooks, dropped the bench-tech
 * UI surface that nobody used.
 */
function buildActionItems({ order, actionLabel, onAction, loadingId, onMarkCompleted, onCancel }) {
    const items = [];

    // Primary action (Mark Collected / Start Test / Write Report).
    if (onAction) {
        items.push({
            key: "primary",
            label: loadingId === order.id ? "Updating…" : actionLabel,
            icon: actionLabel === "Write Report"
                ? <Edit3 className="w-4 h-4" />
                : actionLabel === "Start Test"
                    ? <PlayCircle className="w-4 h-4" />
                    : <CheckCircle2Icon className="w-4 h-4" />,
            disabled: loadingId === order.id,
            onClick: () => onAction(order),
        });
    }

    // Phase 9 — Mark Completed (In Progress section only).
    if (onMarkCompleted && order.status === "IN_PROGRESS") {
        items.push({
            key: "complete",
            label: "Mark Completed",
            icon: <CheckCircle2Icon className="w-4 h-4" />,
            disabled: loadingId === order.id,
            onClick: () => onMarkCompleted(order),
        });
    }

    // Phase 9 — Cancel (all active sections). Destructive tone.
    if (onCancel) {
        if (items.length > 0) items.push({ divider: true });
        items.push({
            key: "cancel",
            label: "Cancel order",
            icon: <XCircle className="w-4 h-4" />,
            tone: "danger",
            disabled: loadingId === order.id,
            onClick: () => onCancel(order),
        });
    }

    return items;
}

export { LabQueue as default };
