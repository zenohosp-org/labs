import { useState, useEffect, useCallback } from "react";
import { useAuth } from "@/context/AuthContext";
import { useNotification } from "@/context/NotificationContext";
import { radiologyApi } from "@/api/labsClient";
import { fmtId } from "@/utils/idFormat";
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
import StatusTimeline from "@/components/StatusTimeline";

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
    const [priorityFilter, setPriorityFilter] = useState("ALL");
    const [showNewModal, setShowNewModal] = useState(false);
    const [writeReport, setWriteReport] = useState(null);
    const [collectPayment, setCollectPayment] = useState(null);
    const [actionMenu, setActionMenu] = useState(null);
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
        } catch {
            notify("Failed to load radiology queue", "error");
        } finally {
            setLoading(false);
        }
    }, [user?.hospitalId, notify]);

    useEffect(() => {
        load();
    }, [load]);

    const handleMarkScanned = async (order) => {
        setMarkingScanned(order.id);
        setActionMenu(null);
        try {
            await radiologyApi.markScanned(order.id);
            notify("Marked as scanned — moved to Awaiting Report", "success");
            load();
        } catch {
            notify("Failed to update status", "error");
        } finally {
            setMarkingScanned(null);
        }
    };

    // Phase 7 — start the modality run (PENDING_SCAN → IN_PROGRESS)
    const handleMarkStarted = async (order) => {
        setMarkingScanned(order.id);
        setActionMenu(null);
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

    const pending = orders.filter((o) => o.status === "PENDING_SCAN");
    const inProgress = orders.filter((o) => o.status === "IN_PROGRESS");   // Phase 7
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
    const filteredInProgress = applyFilters(inProgress);   // Phase 7
    const filteredAwaiting = applyFilters(awaiting);

    return (
        <div className="hms-rad-page" onClick={() => setActionMenu(null)}>
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
                        title="Imaging Queue (Ready for Scan)"
                        subtitle="Click Start Scan to begin the modality run — or Mark Scanned to record a completed walk-up scan in one step"
                        colorMod="is-amber"
                        orders={filteredPending}
                        emptyText="No pending scans"
                        emptySubtext="New orders will appear here"
                        actionLabel="Start Scan"
                        actionMod="is-indigo"
                        onAction={handleMarkStarted}
                        loadingId={markingScanned}
                        actionMenu={actionMenu}
                        setActionMenu={setActionMenu}
                        onCollect={(o) => setCollectPayment(o)}
                        showScanAction
                    />
                    {/* Phase 7 — IN_PROGRESS: scan started, awaiting completion */}
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
                        actionMenu={actionMenu}
                        setActionMenu={setActionMenu}
                        onCollect={(o) => setCollectPayment(o)}
                    />
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
                        loadingId={null}
                        actionMenu={actionMenu}
                        setActionMenu={setActionMenu}
                        onCollect={(o) => setCollectPayment(o)}
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
    showScanAction,
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
                        {["Patient", "Investigation", "Technician", "Priority", "Payment", "Scheduled", ""].map((h) => (
                            <p key={h} className="hms-rad-table-head__cell">{h}</p>
                        ))}
                    </div>
                    {orders.map((order) => {
                        const pmeta = PRIORITY_META[order.priority];
                        const PIcon = pmeta.icon;
                        return (
                            <div key={order.id} className="hms-rad-row" onClick={(e) => e.stopPropagation()}>
                                <div className="hms-rad-patient" style={{ flexDirection: "column", alignItems: "flex-start", gap: 4 }}>
                                    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                                        <div className="hms-rad-patient__avatar">{order.patientName[0]}</div>
                                        <div>
                                            <p className="hms-rad-patient__name">{order.patientName}</p>
                                            <p className="hms-rad-patient__uhid">{fmtId(order.patientUhid)}</p>
                                        </div>
                                    </div>
                                    {/* Phase 7 — HIPAA-grade lifecycle pills with timestamps */}
                                    <StatusTimeline order={order} kind="radiology" compact />
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
                                    <p className="hms-rad-row__date-empty">{order.scheduledDate ?? "—"}</p>
                                </div>
                                <div className="hms-rad-row__action">
                                    {showScanAction ? (
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

export { RadiologyQueue as default };
