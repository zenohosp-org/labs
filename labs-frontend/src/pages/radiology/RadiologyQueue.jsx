import { useState, useEffect, useCallback } from "react";
import { useAuth } from "@/context/AuthContext";
import { useNotification } from "@/context/NotificationContext";
import { radiologyApi } from "@/api/labsClient";
import { fmtId } from "@/utils/idFormat";
import { fmtDate } from "@/utils/date";
import {
    ScanLine,
    Clock,
    CheckCircle2,
    Plus,
    Search,
    Loader2,
    User,
    Stethoscope,
    AlertTriangle,
    Zap,
    IndianRupee,
} from "lucide-react";
import NewOrderModal from "./NewOrderModal";
import WriteReportModal from "./WriteReportModal";
import CollectPaymentModal from "./CollectPaymentModal";
import PaymentCell from "@/components/PaymentCell";
import { Menu } from "@/components/ui";
import { MoreHorizontal, PlayCircle, Edit3, XCircle, CheckCircle2 as CheckCircle2Icon } from "lucide-react";

// HMS-parity status badge — single pill shown in the STATUS column per row.
const STATUS_META = {
    PENDING_SCAN:     { label: "Pending Scan",     cls: "is-pending" },
    IN_PROGRESS:      { label: "In Progress",      cls: "is-progress" },
    AWAITING_REPORT:  { label: "Awaiting Report",  cls: "is-awaiting" },
    REPORT_GENERATED: { label: "Reported",         cls: "is-reported" },
    BILLED:           { label: "Billed",           cls: "is-billed" },
    CANCELLED:        { label: "Cancelled",        cls: "is-billed" },
};

const PRIORITY_META = {
    ROUTINE: { cls: "is-routine", icon: Clock },
    URGENT: { cls: "is-urgent", icon: AlertTriangle },
    STAT: { cls: "is-stat", icon: Zap },
};

function RadiologyQueue() {
    const { user } = useAuth();
    const { notify } = useNotification();
    const [stats, setStats] = useState({ pendingScan: 0, awaitingReport: 0, reportGenerated: 0 });
    const [orders, setOrders] = useState([]);
    const [loading, setLoading] = useState(true);
    const [search, setSearch] = useState("");
    // Phase 9 — section tab. Replaces the priority filter; per-row priority
    // badges still carry the visual triage. Default: pending scans.
    const [activeSection, setActiveSection] = useState("pending");
    const [showNewModal, setShowNewModal] = useState(false);
    const [writeReport, setWriteReport] = useState(null);
    const [collectPayment, setCollectPayment] = useState(null);
    const [markingScanned, setMarkingScanned] = useState(null);

    const load = useCallback(async () => {
        if (!user?.hospitalId) return;
        setLoading(true);
        try {
            const [ordersData, statsData] = await Promise.all([
                radiologyApi.list(user.hospitalId),
                radiologyApi.getStats(user.hospitalId),
            ]);
            setOrders(ordersData);
            setStats(statsData);
        } catch (err) {
            notify(err?.response?.data?.message || "Failed to load radiology queue", "error");
        } finally {
            setLoading(false);
        }
    }, [user?.hospitalId, notify]);

    useEffect(() => {
        load();
    }, [load]);

    const handleMarkScanned = async (order) => {
        setMarkingScanned(order.id);
        try {
            await radiologyApi.markScanned(order.id);
            notify("Marked as scanned — moved to Awaiting Report", "success");
            load();
        } catch (err) {
            notify(err?.response?.data?.message || "Failed to update status", "error");
        } finally {
            setMarkingScanned(null);
        }
    };

    // Phase 7 — start the modality run (PENDING_SCAN → IN_PROGRESS)
    const handleMarkStarted = async (order) => {
        setMarkingScanned(order.id);
        try {
            await radiologyApi.markStarted(order.id);
            notify("Scan started — moved to In Progress", "success");
            load();
        } catch (err) {
            notify(err?.response?.data?.message || "Failed to start scan", "error");
        } finally {
            setMarkingScanned(null);
        }
    };

    // Phase 9 — Mark Completed (AWAITING_REPORT / IN_PROGRESS → REPORT_GENERATED).
    const handleMarkCompleted = async (order) => {
        setMarkingScanned(order.id);
        try {
            await radiologyApi.markCompleted(order.id);
            notify("Report completed — billed to active invoice if priced", "success");
            load();
        } catch (err) {
            notify(err?.response?.data?.message || "Failed to mark completed", "error");
        } finally {
            setMarkingScanned(null);
        }
    };

    // Phase 9 — soft cancel.
    const handleCancel = async (order) => {
        const reason = window.prompt(
            `Cancel ${order.serviceName} for ${order.patientName}?\n\nOptional reason (audit):`,
            ""
        );
        if (reason === null) return;
        setMarkingScanned(order.id);
        try {
            await radiologyApi.cancelOrder(order.id, reason);
            notify("Order cancelled", "success");
            load();
        } catch (err) {
            notify(err?.response?.data?.message || "Failed to cancel order", "error");
        } finally {
            setMarkingScanned(null);
        }
    };

    const pending = orders.filter((o) => o.status === "PENDING_SCAN");
    const inProgress = orders.filter((o) => o.status === "IN_PROGRESS");   // Phase 7
    const awaiting = orders.filter((o) => o.status === "AWAITING_REPORT");

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
    const filteredInProgress = applyFilters(inProgress);   // Phase 7
    const filteredAwaiting = applyFilters(awaiting);

    return (
        <div className="hms-rad-page">
            <div className="hms-rad-page__head">
                <div>
                    <h1 className="hms-rad-page__title">
                        <ScanLine className="w-5 h-5 hms-rad-page__title-icon" /> Radiology Queue
                    </h1>
                    <p className="hms-rad-page__sub">
                        X-Ray, CT Scan, MRI, Ultrasound, and other imaging investigations
                    </p>
                </div>
                <div className="hms-rad-page__chips">
                    <div className="hms-rad-chip-row">
                        <span className="hms-rad-chip is-amber">
                            <ScanLine className="w-3 h-3" /> {stats.pendingScan} awaiting scan
                        </span>
                        <span className="hms-rad-chip is-slate">
                            <Clock className="w-3 h-3" /> {stats.awaitingReport} awaiting report
                        </span>
                        <span className="hms-rad-chip is-emerald">
                            <CheckCircle2 className="w-3 h-3" /> {stats.reportGenerated} done
                        </span>
                    </div>
                    <button onClick={() => setShowNewModal(true)} className="hms-btn-primary">
                        <Plus className="w-4 h-4" /> New Order
                    </button>
                </div>
            </div>

            <div className="hms-rad-stat-grid">
                <div className="hms-rad-stat is-amber">
                    <div>
                        <p className="hms-rad-stat__label">Pending Scans</p>
                        <p className="hms-rad-stat__value">{stats.pendingScan}</p>
                    </div>
                    <ScanLine className="hms-rad-stat__icon" />
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
                        Imaging Queue
                        <span className="hms-rad-section-tab__count">{pending.length}</span>
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
                    title="Imaging Queue (Ready for Scan)"
                    subtitle="Click Start Scan to begin the modality run"
                    colorMod="is-amber"
                    orders={filteredPending}
                    emptyText="No pending scans"
                    emptySubtext="New orders will appear here"
                    actionLabel="Start Scan"
                    actionMod="is-indigo"
                    onAction={handleMarkStarted}
                    loadingId={markingScanned}
                    onCollect={(o) => setCollectPayment(o)}
                    onCancel={handleCancel}
                />
            ) : activeSection === "inprogress" ? (
                <QueueSection
                    title="In Progress"
                    subtitle="Modality run started — mark scanned when the study is complete"
                    colorMod="is-emerald"
                    orders={filteredInProgress}
                    emptyText="No scans in progress"
                    emptySubtext="Started scans appear here until marked complete"
                    actionLabel="Mark Scanned"
                    actionMod="is-emerald"
                    onAction={handleMarkScanned}
                    loadingId={markingScanned}
                    onCollect={(o) => setCollectPayment(o)}
                    onCancel={handleCancel}
                />
            ) : (
                <QueueSection
                    title="Awaiting Reports"
                    subtitle="Scans completed — radiologist findings pending"
                    colorMod="is-slate"
                    orders={filteredAwaiting}
                    emptyText="No scans awaiting reports"
                    emptySubtext="Completed scans will appear here"
                    actionLabel="Write Report"
                    actionMod="is-slate"
                    onAction={(o) => setWriteReport(o)}
                    loadingId={markingScanned}
                    onMarkCompleted={handleMarkCompleted}
                    onCollect={(o) => setCollectPayment(o)}
                    onCancel={handleCancel}
                />
            )}

            {showNewModal && (
                <NewOrderModal
                    onClose={() => setShowNewModal(false)}
                    onCreated={() => {
                        setShowNewModal(false);
                        load();
                    }}
                />
            )}
            {writeReport && (
                <WriteReportModal
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
    onMarkCompleted,   // Phase 9 — Awaiting Reports only (radiology)
    onCancel,          // Phase 9 — all active sections
}) {
    return (
        <div className={`hms-rad-section ${colorMod}`}>
            <div className="hms-rad-section__head">
                <p className="hms-rad-section__title">{title}</p>
                <p className="hms-rad-section__sub">{subtitle}</p>
            </div>
            {orders.length === 0 ? (
                <div className="hms-rad-section__empty">
                    <ScanLine className="w-5 h-5 hms-rad-section__empty-icon" />
                    <p className="hms-rad-section__empty-title">{emptyText}</p>
                    <p className="hms-rad-section__empty-sub">{emptySubtext}</p>
                </div>
            ) : (
                <div className="hms-rad-section__list">
                    <div className="hms-rad-table-head">
                        {["Patient", "Investigation", "Technician", "Priority", "Payment", "Scheduled", "Status", ""].map((h) => (
                            <p key={h} className="hms-rad-table-head__cell">{h}</p>
                        ))}
                    </div>
                    {orders.map((order) => {
                        const pmeta = PRIORITY_META[order.priority];
                        const PIcon = pmeta.icon;
                        return (
                            <div key={order.id} className="hms-rad-row" onClick={(e) => e.stopPropagation()}>
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
                                            <p className="hms-rad-patient__uhid" title="IPD admission number">
                                                ADM: <code>{order.admissionNumber}</code>
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
                                    {order.technicianName ? (
                                        <div className="hms-rad-tech">
                                            <User className="w-3 h-3 hms-rad-tech__icon" />
                                            <p>{order.technicianName}</p>
                                        </div>
                                    ) : (
                                        <p className="hms-rad-tech-empty">Unassigned</p>
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
 * Build kebab menu items for a radiology row (Phase 9 — simplified):
 *
 *   Pending Scan      → Start Scan     · Cancel
 *   In Progress       → Mark Scanned   · Cancel
 *   Awaiting Reports  → Write Report   · Mark Completed · Cancel
 */
function buildActionItems({ order, actionLabel, onAction, loadingId, onMarkCompleted, onCancel }) {
    const items = [];

    if (onAction) {
        items.push({
            key: "primary",
            label: loadingId === order.id ? "Updating…" : actionLabel,
            icon: actionLabel === "Write Report"
                ? <Edit3 className="w-4 h-4" />
                : actionLabel === "Start Scan"
                    ? <PlayCircle className="w-4 h-4" />
                    : <CheckCircle2Icon className="w-4 h-4" />,
            disabled: loadingId === order.id,
            onClick: () => onAction(order),
        });
    }

    if (onMarkCompleted && order.status === "AWAITING_REPORT") {
        items.push({
            key: "complete",
            label: "Mark Completed",
            icon: <CheckCircle2Icon className="w-4 h-4" />,
            disabled: loadingId === order.id,
            onClick: () => onMarkCompleted(order),
        });
    }

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

export { RadiologyQueue as default };
