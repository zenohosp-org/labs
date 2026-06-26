import { CheckCircle2, Circle, Beaker, Inbox, Activity, FileText, ShieldCheck } from "lucide-react";

/**
 * Horizontal HIPAA-grade status timeline for a lab or radiology order.
 *
 * Renders one pill per lifecycle stage. Each stage is in one of three
 * visual states:
 *   - DONE      → tone-coloured pill, icon + timestamp + actor name shown
 *   - CURRENT   → tone-coloured pill with subtle pulse, "active now"
 *   - PENDING   → grey outline, no timestamp
 *
 * Compact (single row) so it slots into a queue row without disrupting the
 * existing layout. For the long actor strings we truncate visually but keep
 * the full name in the `title` attribute (hover tooltip).
 *
 * Props
 *   order : the LabOrderDTO or RadiologyOrderDTO from the API
 *   kind  : "lab" | "radiology"
 *   compact (default true) — when false renders a vertical stack with full timestamps + names
 *
 * No external deps; pure CSS via <style> so it's self-contained.
 */
const FMT_TIME = new Intl.DateTimeFormat("en-IN", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: true,
});
const FMT_DATE = new Intl.DateTimeFormat("en-IN", {
    day: "2-digit",
    month: "short",
});

function fmt(ts) {
    if (!ts) return null;
    const d = new Date(ts);
    if (Number.isNaN(d.getTime())) return null;
    const now = new Date();
    const sameDay =
        d.getFullYear() === now.getFullYear() &&
        d.getMonth() === now.getMonth() &&
        d.getDate() === now.getDate();
    return sameDay ? FMT_TIME.format(d) : `${FMT_DATE.format(d)} ${FMT_TIME.format(d)}`;
}

export default function StatusTimeline({ order, kind = "lab", compact = true }) {
    if (!order) return null;
    const stages = kind === "radiology" ? buildRadiologyStages(order) : buildLabStages(order);

    return (
        <div className={`st-wrap ${compact ? "is-compact" : "is-stacked"}`}>
            <style>{css}</style>
            {stages.map((s, idx) => (
                <Stage
                    key={s.key}
                    stage={s}
                    isLast={idx === stages.length - 1}
                    compact={compact}
                />
            ))}
        </div>
    );
}

function Stage({ stage, isLast, compact }) {
    const Icon = stage.icon;
    const state = stage.state; // 'done' | 'current' | 'pending'
    const tone = stage.tone || "slate";
    return (
        <>
            <div
                className={`st-pill is-${tone} is-${state}`}
                title={stage.title || stage.label}
            >
                <span className="st-pill__dot">
                    {state === "done" ? <CheckCircle2 size={11} /> : <Icon size={11} />}
                </span>
                <span className="st-pill__label">{stage.label}</span>
                {compact && state === "done" && stage.at && (
                    <span className="st-pill__time">{fmt(stage.at)}</span>
                )}
                {!compact && stage.at && (
                    <span className="st-pill__detail">
                        {fmt(stage.at)}
                        {stage.by && <span className="st-pill__by">· {stage.by}</span>}
                    </span>
                )}
            </div>
            {!isLast && <span className={`st-rail is-${state}`} />}
        </>
    );
}

function buildLabStages(o) {
    const stages = [
        {
            key: "created", label: "Created", icon: Circle, tone: "slate",
            at: o.createdAt, by: o.createdByName,
        },
        {
            key: "collected", label: "Collected", icon: Beaker, tone: "amber",
            at: o.collectedAt, by: o.collectedByName,
        },
        {
            key: "received", label: "Received", icon: Inbox, tone: "blue",
            at: o.receivedAt, by: o.receivedByName,
        },
        {
            key: "started", label: "In Progress", icon: Activity, tone: "indigo",
            at: o.startedAt, by: o.startedByName,
        },
        {
            key: "reported", label: "Reported", icon: FileText, tone: "emerald",
            at: o.reportedAt, by: o.reportedByName,
        },
    ];
    return assignStates(stages, o.status, LAB_STATUS_TO_STAGE);
}

function buildRadiologyStages(o) {
    const stages = [
        {
            key: "created", label: "Created", icon: Circle, tone: "slate",
            at: o.createdAt, by: o.createdByName,
        },
        {
            key: "started", label: "In Progress", icon: Activity, tone: "indigo",
            at: o.startedAt, by: o.startedByName,
        },
        {
            key: "scanned", label: "Scanned", icon: Beaker, tone: "amber",
            at: o.scannedAt, by: o.scannedByName,
        },
        {
            key: "reported", label: "Reported", icon: FileText, tone: "emerald",
            at: o.reportedAt, by: o.reportedByName,
        },
    ];
    return assignStates(stages, o.status, RAD_STATUS_TO_STAGE);
}

// Map status string → the stage key that is currently the "current" one.
const LAB_STATUS_TO_STAGE = {
    PENDING_COLLECTION: "collected",   // collected is the next thing to happen
    AWAITING_REPORT:    "started",
    IN_PROGRESS:        "reported",
    REPORT_GENERATED:   null,          // everything done
    BILLED:             null,
};
const RAD_STATUS_TO_STAGE = {
    PENDING_SCAN:     "started",
    IN_PROGRESS:      "scanned",
    AWAITING_REPORT:  "reported",
    REPORT_GENERATED: null,
    BILLED:           null,
};

function assignStates(stages, status, statusMap) {
    // Anything with a timestamp = done. Anything without timestamp + status
    // points at it = current. Otherwise pending.
    const currentKey = statusMap[status] ?? null;
    return stages.map((s) => {
        if (s.at) return { ...s, state: "done" };
        if (s.key === currentKey) return { ...s, state: "current" };
        return { ...s, state: "pending" };
    });
}

const css = `
.st-wrap { display: flex; align-items: center; flex-wrap: wrap; gap: 2px; font-size: 11px; }
.st-wrap.is-stacked { flex-direction: column; align-items: stretch; gap: 4px; }
.st-pill {
    display: inline-flex; align-items: center; gap: 4px;
    padding: 2px 6px; border-radius: 9999px;
    border: 1px solid transparent;
    line-height: 1.2;
    transition: all 200ms ease;
}
.st-pill__dot { display: inline-flex; }
.st-pill__label { font-weight: 600; }
.st-pill__time { color: rgba(0,0,0,0.55); font-variant-numeric: tabular-nums; margin-left: 2px; }
.st-pill__detail { color: rgba(0,0,0,0.55); margin-left: 4px; }
.st-pill__by { margin-left: 4px; color: rgba(0,0,0,0.45); }
.st-rail { width: 12px; height: 1px; background: #e5e7eb; flex-shrink: 0; transition: background 200ms ease; }
.st-rail.is-done { background: #cbd5e1; }

/* Pending state — outlined / muted */
.st-pill.is-pending { background: #fff; border-color: #e5e7eb; color: #94a3b8; }

/* Done states — tone-coloured filled */
.st-pill.is-done.is-slate    { background: #f1f5f9; color: #475569; }
.st-pill.is-done.is-amber    { background: #fef3c7; color: #92400e; }
.st-pill.is-done.is-blue     { background: #dbeafe; color: #1e40af; }
.st-pill.is-done.is-indigo   { background: #e0e7ff; color: #3730a3; }
.st-pill.is-done.is-emerald  { background: #d1fae5; color: #065f46; }
.st-pill.is-done.is-rose     { background: #ffe4e6; color: #9f1239; }

/* Current = same colours as done but with a subtle ring + pulse */
.st-pill.is-current.is-slate    { background: #e2e8f0; color: #1e293b; box-shadow: 0 0 0 2px rgba(100,116,139,0.18); }
.st-pill.is-current.is-amber    { background: #fde68a; color: #78350f; box-shadow: 0 0 0 2px rgba(217,119,6,0.22); }
.st-pill.is-current.is-blue     { background: #bfdbfe; color: #1e3a8a; box-shadow: 0 0 0 2px rgba(37,99,235,0.22); }
.st-pill.is-current.is-indigo   { background: #c7d2fe; color: #312e81; box-shadow: 0 0 0 2px rgba(79,70,229,0.22); animation: st-pulse 1.6s ease-in-out infinite; }
.st-pill.is-current.is-emerald  { background: #a7f3d0; color: #064e3b; box-shadow: 0 0 0 2px rgba(16,185,129,0.22); }

@keyframes st-pulse {
    0%, 100% { box-shadow: 0 0 0 2px rgba(79,70,229,0.22); }
    50%      { box-shadow: 0 0 0 4px rgba(79,70,229,0.30); }
}

.st-wrap.is-stacked .st-rail { width: 1px; height: 8px; margin-left: 10px; }
`;
