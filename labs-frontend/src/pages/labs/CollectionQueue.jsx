import { useEffect, useMemo, useState } from "react";
import {
    Beaker,
    Clock,
    AlertTriangle,
    Zap,
    Droplet,
    Search,
    Loader2,
    User as UserIcon,
    Stethoscope,
    UtensilsCrossed,
    RefreshCw,
    CheckCircle2,
    XCircle,
    Inbox,
} from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import { useNotification } from "@/context/NotificationContext";
import { collectionApi } from "@/api/labsClient";
import { Badge, Button } from "@/components/ui";
import BulkCollectModal from "@/components/modals/BulkCollectModal";

const PRIORITY_META = {
    STAT:    { tone: "danger",  icon: Zap,            label: "STAT" },
    URGENT:  { tone: "warning", icon: AlertTriangle,  label: "URGENT" },
    ROUTINE: { tone: "neutral", icon: Clock,          label: "ROUTINE" },
};

const PRIORITY_FILTERS = ["ALL", "STAT", "URGENT", "ROUTINE"];

/**
 * Collection Console — front-of-house queue used by the phlebotomist
 * at the sample-collection desk.
 *
 * Layout:
 *   - Top: 4-tile stats strip (Patients waiting / Orders waiting /
 *     STAT chip / Collected today / Awaiting receive / Rejected today)
 *   - Filter row: priority pill + search box (UHID / name / phone)
 *   - Patient cards (sorted STAT → URGENT → ROUTINE → oldest first).
 *     Each card shows the patient header, full list of pending orders,
 *     and the resolved tube plan. One "Collect & print" button opens
 *     BulkCollectModal which fires the atomic bulk-collect.
 */
export default function CollectionQueue() {
    const { user } = useAuth();
    const { notify } = useNotification();

    const [queue, setQueue] = useState([]);
    const [stats, setStats] = useState({
        pendingPatients: 0,
        pendingOrders: 0,
        pendingStat: 0,
        pendingUrgent: 0,
        collectedToday: 0,
        rejectedToday: 0,
        awaitingReceiveToday: 0,
    });
    const [loading, setLoading] = useState(true);
    const [priorityFilter, setPriorityFilter] = useState("ALL");
    const [search, setSearch] = useState("");
    const [collectFor, setCollectFor] = useState(null);

    const load = async () => {
        if (!user?.hospitalId) return;
        setLoading(true);
        try {
            const [q, s] = await Promise.all([collectionApi.queue(), collectionApi.stats()]);
            setQueue(q ?? []);
            setStats(s ?? stats);
        } catch {
            notify("Failed to load collection queue", "error");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        load();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [user?.hospitalId]);

    const filtered = useMemo(() => {
        const q = search.trim().toLowerCase();
        return queue.filter((p) => {
            if (priorityFilter !== "ALL" && p.highestPriority !== priorityFilter) return false;
            if (!q) return true;
            return (
                (p.patientName || "").toLowerCase().includes(q) ||
                (p.patientUhid || "").toLowerCase().includes(q) ||
                (p.patientPhone || "").toLowerCase().includes(q) ||
                (p.orders || []).some((o) =>
                    (o.serviceName || "").toLowerCase().includes(q),
                )
            );
        });
    }, [queue, search, priorityFilter]);

    return (
        <div className="hms-rad-page">
            <div className="hms-rad-page__head">
                <div>
                    <h1 className="hms-rad-page__title">
                        <Beaker className="w-5 h-5 hms-rad-page__title-icon" /> Collection Queue
                    </h1>
                    <p className="hms-rad-page__sub">
                        Front-of-house · patient pickups · atomic bulk-collect with auto-printed labels
                    </p>
                </div>
                <div className="hms-rad-page__chips">
                    <div className="hms-rad-chip-row">
                        <span className="hms-rad-chip is-amber">
                            <UserIcon className="w-3 h-3" /> {stats.pendingPatients} waiting
                        </span>
                        <span className="hms-rad-chip is-rose">
                            <Zap className="w-3 h-3" /> {stats.pendingStat} STAT
                        </span>
                        <span className="hms-rad-chip is-slate">
                            <Droplet className="w-3 h-3" /> {stats.pendingOrders} orders
                        </span>
                    </div>
                </div>
            </div>

            <div className="hms-rad-stat-grid">
                <StatTile tone="amber" icon={UserIcon} label="Patients waiting" value={stats.pendingPatients} />
                <StatTile tone="slate" icon={Droplet} label="Orders pending" value={stats.pendingOrders} />
                <StatTile tone="emerald" icon={CheckCircle2} label="Collected today" value={stats.collectedToday} />
                <StatTile tone="rose" icon={XCircle} label="Rejected today" value={stats.rejectedToday} />
            </div>

            <div className="hms-rad-filterbar">
                <div className="hms-rad-priority-row">
                    {PRIORITY_FILTERS.map((p) => (
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
                        placeholder="Search patient, UHID, phone, test…"
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                    />
                </div>
                <button
                    className="hms-rad-row__view-btn"
                    onClick={load}
                    title="Refresh"
                    style={{ marginLeft: 8 }}
                >
                    <RefreshCw className="w-3 h-3" /> Refresh
                </button>
                <div className="text-12 text-gray-500 ml-auto inline-flex items-center gap-1">
                    <Inbox size={12} /> {stats.awaitingReceiveToday} awaiting receive at the lab
                </div>
            </div>

            {loading ? (
                <div className="hms-rad-section__loading">
                    <Loader2 className="w-5 h-5 animate-spin text-gray-400" />
                </div>
            ) : filtered.length === 0 ? (
                <div className="hms-rad-section">
                    <div className="hms-rad-section__empty">
                        <Beaker className="w-5 h-5 hms-rad-section__empty-icon" />
                        <p className="hms-rad-section__empty-title">No pending pickups</p>
                        <p className="hms-rad-section__empty-sub">
                            {search || priorityFilter !== "ALL"
                                ? "No patients match the current filters."
                                : "New orders from HMS will show up here as they're placed."}
                        </p>
                    </div>
                </div>
            ) : (
                <div className="flex flex-col gap-3 mt-2">
                    {filtered.map((p) => (
                        <PatientCard
                            key={p.patientId}
                            patient={p}
                            onCollect={() => setCollectFor(p)}
                        />
                    ))}
                </div>
            )}

            {collectFor && (
                <BulkCollectModal
                    patient={collectFor}
                    onClose={() => setCollectFor(null)}
                    onCollected={() => {
                        setCollectFor(null);
                        load();
                    }}
                />
            )}
        </div>
    );
}

function StatTile({ tone, icon: Icon, label, value }) {
    return (
        <div className={`hms-rad-stat is-${tone}`}>
            <div>
                <p className="hms-rad-stat__label">{label}</p>
                <p className="hms-rad-stat__value">{value}</p>
            </div>
            <Icon className="hms-rad-stat__icon" />
        </div>
    );
}

function PatientCard({ patient, onCollect }) {
    const priMeta = PRIORITY_META[patient.highestPriority] || PRIORITY_META.ROUTINE;
    const PIcon = priMeta.icon;
    const waitMin = patient.earliestPendingAt
        ? Math.max(0, Math.round((Date.now() - new Date(patient.earliestPendingAt).getTime()) / 60000))
        : null;
    const hasFasting = (patient.containerPlan || []).some((t) => t.fastingRequired);

    return (
        <div className="border border-gray-200 rounded-xl bg-white p-3 flex flex-col gap-3 shadow-sm">
            <div className="flex items-start justify-between gap-3 flex-wrap">
                <div className="flex items-start gap-3">
                    <div className="hms-rad-patient__avatar" style={{ marginTop: 2 }}>
                        {(patient.patientName || "?").charAt(0).toUpperCase()}
                    </div>
                    <div>
                        <div className="flex items-center gap-2 flex-wrap">
                            <span className="font-bold text-14 text-gray-900">{patient.patientName}</span>
                            <Badge tone={priMeta.tone === "danger" ? "danger" : priMeta.tone === "warning" ? "warning" : "neutral"} soft>
                                <PIcon size={11} className="inline mr-1" />
                                {priMeta.label}
                            </Badge>
                            {hasFasting && (
                                <Badge tone="warning" soft>
                                    <UtensilsCrossed size={11} className="inline mr-1" /> fasting
                                </Badge>
                            )}
                            {waitMin != null && (
                                <span className="text-11 text-gray-500">
                                    <Clock size={10} className="inline mr-1" /> waiting {waitMin} min
                                </span>
                            )}
                        </div>
                        <div className="text-11 text-gray-500 mt-0.5">
                            UHID {patient.patientUhid || "—"}
                            {patient.ageYears != null && ` · ${patient.ageYears} yrs`}
                            {patient.patientSex && ` · ${patient.patientSex}`}
                            {patient.patientPhone && ` · ${patient.patientPhone}`}
                        </div>
                    </div>
                </div>
                <Button variant="primary" onClick={onCollect}>
                    <Beaker size={14} /> Collect &amp; print ({(patient.containerPlan || []).length} tube{(patient.containerPlan || []).length === 1 ? "" : "s"})
                </Button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                <div>
                    <div className="text-11 font-bold text-gray-500 uppercase tracking-wide mb-1">
                        Orders ({patient.orders?.length || 0})
                    </div>
                    <div className="flex flex-col gap-1 text-12">
                        {(patient.orders || []).map((o) => {
                            const om = PRIORITY_META[o.priority] || PRIORITY_META.ROUTINE;
                            const OIcon = om.icon;
                            return (
                                <div key={o.id} className="flex items-center gap-2 py-0.5">
                                    <Badge tone={om.tone === "danger" ? "danger" : om.tone === "warning" ? "warning" : "neutral"} soft>
                                        <OIcon size={10} className="inline mr-0.5" />
                                        {o.priority}
                                    </Badge>
                                    <span className="font-bold text-gray-900 flex-1">{o.serviceName}</span>
                                    {o.resolvedContainer && (
                                        <Badge tone="info" soft>{o.resolvedContainer}</Badge>
                                    )}
                                    {o.referredByName && (
                                        <span className="inline-flex items-center gap-1 text-11 text-gray-500">
                                            <Stethoscope size={10} />
                                            {o.referredByName}
                                        </span>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                </div>

                <div>
                    <div className="text-11 font-bold text-gray-500 uppercase tracking-wide mb-1">
                        Tubes ({(patient.containerPlan || []).length})
                    </div>
                    <div className="flex flex-col gap-1.5">
                        {(patient.containerPlan || []).map((t) => (
                            <div
                                key={t.containerType}
                                className="border border-gray-200 rounded px-2 py-1.5 bg-gray-50/40"
                            >
                                <div className="flex items-center gap-2 flex-wrap">
                                    <Badge tone="info" soft>{t.containerType}</Badge>
                                    {t.volumeMl != null && (
                                        <span className="text-11 text-gray-700">{t.volumeMl} mL</span>
                                    )}
                                    {t.fastingRequired && (
                                        <Badge tone="warning" soft>fasting</Badge>
                                    )}
                                    <span className="text-11 text-gray-500 ml-auto">
                                        serves {t.servesOrderIds.length} order(s)
                                    </span>
                                </div>
                                <div className="text-11 text-gray-500 mt-0.5">
                                    {t.servesTestNames.join(" · ")}
                                </div>
                            </div>
                        ))}
                        {(patient.containerPlan || []).length === 0 && (
                            <div className="text-12 text-gray-400">— no tubes —</div>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}
