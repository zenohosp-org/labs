import { useState, useEffect, useCallback } from "react";
import { useAuth } from "@/context/AuthContext";
import { useNotification } from "@/context/NotificationContext";
import { labApi } from "@/api/labsClient";
import { fmtId } from "@/utils/idFormat";
import {
    FlaskConical,
    Clock,
    CheckCircle2,
    Plus,
    Search,
    Loader2,
    User,
    Stethoscope,
    AlertTriangle,
    Zap,
} from "lucide-react";
import NewOrderModal from "./NewOrderModal";
import WriteReportModal from "./WriteReportModal";

const PRIORITY_META = {
    ROUTINE: { cls: "is-routine", icon: Clock },
    URGENT: { cls: "is-urgent", icon: AlertTriangle },
    STAT: { cls: "is-stat", icon: Zap },
};

function LabsQueue() {
    const { user } = useAuth();
    const { notify } = useNotification();
    const [stats, setStats] = useState({ pendingCollection: 0, awaitingReport: 0, reportGenerated: 0 });
    const [orders, setOrders] = useState([]);
    const [loading, setLoading] = useState(true);
    const [search, setSearch] = useState("");
    const [priorityFilter, setPriorityFilter] = useState("ALL");
    const [showNewModal, setShowNewModal] = useState(false);
    const [writeReport, setWriteReport] = useState(null);
    const [actionMenu, setActionMenu] = useState(null);
    const [markingCollected, setMarkingCollected] = useState(null);

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
        setActionMenu(null);
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

    const pending = orders.filter((o) => o.status === "PENDING_COLLECTION");
    const awaiting = orders.filter((o) => o.status === "AWAITING_REPORT");

    const applyFilters = (list) => {
        let result = list;
        if (priorityFilter !== "ALL") result = result.filter((o) => o.priority === priorityFilter);
        if (search.trim()) {
            const q = search.toLowerCase();
            result = result.filter(
                (o) =>
                    o.patientName.toLowerCase().includes(q) ||
                    o.patientUhid.toLowerCase().includes(q) ||
                    o.serviceName.toLowerCase().includes(q) ||
                    o.technicianName?.toLowerCase().includes(q)
            );
        }
        return result;
    };

    const filteredPending = applyFilters(pending);
    const filteredAwaiting = applyFilters(awaiting);

    return (
        <div className="hms-rad-page" onClick={() => setActionMenu(null)}>
            {/* Page header */}
            <div className="hms-rad-page__head">
                <div>
                    <h1 className="hms-rad-page__title">
                        <FlaskConical className="w-5 h-5 hms-rad-page__title-icon" /> Lab Queue
                    </h1>
                    <p className="hms-rad-page__sub">
                        Blood work, urinalysis, microbiology, biochemistry and other diagnostic tests
                    </p>
                </div>
                <div className="hms-rad-page__chips">
                    <div className="hms-rad-chip-row">
                        <span className="hms-rad-chip is-amber">
                            <FlaskConical className="w-3 h-3" /> {stats.pendingCollection} pending collection
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

            {/* Stat cards */}
            <div className="hms-rad-stat-grid">
                <div className="hms-rad-stat is-amber">
                    <div>
                        <p className="hms-rad-stat__label">Pending Collection</p>
                        <p className="hms-rad-stat__value">{stats.pendingCollection}</p>
                    </div>
                    <FlaskConical className="hms-rad-stat__icon" />
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

            {/* Search + filters */}
            <div className="hms-rad-filterbar">
                <div className="hms-rad-priority-row">
                    {["ALL", "ROUTINE", "URGENT", "STAT"].map((p) => (
                        <button
                            key={p}
                            onClick={() => setPriorityFilter(p)}
                            className={`hms-rad-priority-btn ${priorityFilter === p ? "is-on" : ""}`}
                        >
                            {p}
                        </button>
                    ))}
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
            ) : (
                <>
                    <QueueSection
                        title="Collection Queue (Ready for Sample)"
                        subtitle="Orders waiting for the sample to be drawn or collected"
                        colorMod="is-amber"
                        orders={filteredPending}
                        emptyText="No pending collections"
                        emptySubtext="New orders will appear here"
                        actionLabel="Mark Collected"
                        actionMod="is-amber"
                        onAction={handleMarkCollected}
                        loadingId={markingCollected}
                        actionMenu={actionMenu}
                        setActionMenu={setActionMenu}
                        onWriteReport={() => {}}
                        showCollectAction
                    />
                    <QueueSection
                        title="Awaiting Reports"
                        subtitle="Samples processed — pathologist findings pending"
                        colorMod="is-slate"
                        orders={filteredAwaiting}
                        emptyText="No samples awaiting reports"
                        emptySubtext="Processed samples will appear here"
                        actionLabel="Write Report"
                        actionMod="is-slate"
                        onAction={(o) => setWriteReport(o)}
                        loadingId={null}
                        actionMenu={actionMenu}
                        setActionMenu={setActionMenu}
                        onWriteReport={(o) => setWriteReport(o)}
                    />
                </>
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
    showCollectAction,
}) {
    return (
        <div className={`hms-rad-section ${colorMod}`}>
            <div className="hms-rad-section__head">
                <p className="hms-rad-section__title">{title}</p>
                <p className="hms-rad-section__sub">{subtitle}</p>
            </div>
            {orders.length === 0 ? (
                <div className="hms-rad-section__empty">
                    <FlaskConical className="w-5 h-5 hms-rad-section__empty-icon" />
                    <p className="hms-rad-section__empty-title">{emptyText}</p>
                    <p className="hms-rad-section__empty-sub">{emptySubtext}</p>
                </div>
            ) : (
                <div className="hms-rad-section__list">
                    <div className="hms-rad-table-head">
                        {["Patient", "Investigation", "Technician", "Priority", "Scheduled", ""].map((h) => (
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
                                        <p className="hms-rad-patient__name">{order.patientName}</p>
                                        <p className="hms-rad-patient__uhid">{fmtId(order.patientUhid)}</p>
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
                                    <p className="hms-rad-row__date-empty">{order.scheduledDate ?? "—"}</p>
                                </div>
                                <div className="hms-rad-row__action">
                                    {showCollectAction ? (
                                        <button
                                            onClick={() => onAction(order)}
                                            disabled={loadingId === order.id}
                                            className={`hms-rad-row__action-btn ${actionMod}`}
                                        >
                                            {loadingId === order.id ? "Updating…" : actionLabel}
                                        </button>
                                    ) : (
                                        <button
                                            onClick={() => onAction(order)}
                                            className={`hms-rad-row__action-btn ${actionMod}`}
                                        >
                                            {actionLabel}
                                        </button>
                                    )}
                                </div>
                            </div>
                        );
                    })}
                </div>
            )}
        </div>
    );
}

export { LabsQueue as default };
