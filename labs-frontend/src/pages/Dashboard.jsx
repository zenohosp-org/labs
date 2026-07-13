import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";
import { getLabsDashboard, labApi } from "@/api/labsClient";
import { fmtId } from "@/utils/idFormat";
import { fmtDateTime } from "@/utils/date";
import {
    FlaskConical,
    Clock,
    CheckCircle2,
    AlertTriangle,
    Loader2,
    User,
    ExternalLink,
    FileText,
} from "lucide-react";

const RECENT_LIMIT = 5;

export default function Dashboard() {
    const { user } = useAuth();
    const navigate = useNavigate();
    const [stats, setStats] = useState(null);
    const [recent, setRecent] = useState([]);
    const [loading, setLoading] = useState(true);
    const [err, setErr] = useState("");

    useEffect(() => {
        if (!user?.hospitalId) return;
        setLoading(true);
        Promise.all([
            getLabsDashboard().then((r) => r.data),
            labApi.list(user.hospitalId, "COMPLETED").catch(() => []),
        ])
            .then(([statsData, reports]) => {
                setStats(statsData);
                setRecent(reports.slice(0, RECENT_LIMIT));
            })
            .catch((e) => setErr(e?.message || "Failed to load dashboard"))
            .finally(() => setLoading(false));
    }, [user?.hospitalId]);

    return (
        <div className="hms-rad-page">
            <div className="hms-rad-page__head">
                <div>
                    <h1 className="hms-rad-page__title">
                        <FlaskConical className="w-5 h-5 hms-rad-page__title-icon" />
                        ZenoLabs Dashboard
                    </h1>
                    <p className="hms-rad-page__sub">
                        Signed in as <strong>{user?.email}</strong>
                        {user?.role ? ` · ${user.role}` : ""}
                    </p>
                </div>
            </div>

            {err && (
                <div className="hms-alert is-red" role="alert">
                    <AlertTriangle className="hms-alert__icon w-4 h-4" />
                    <span>{err}</span>
                </div>
            )}

            {loading ? (
                <div className="hms-rad-section__loading">
                    <Loader2 className="w-5 h-5 animate-spin text-gray-400" />
                </div>
            ) : (
                <>
                    <div className="hms-rad-stat-grid">
                        <Link to="/lab/queue" className="hms-rad-stat is-amber">
                            <div>
                                <p className="hms-rad-stat__label">Pending Collection</p>
                                <p className="hms-rad-stat__value">
                                    {stats?.pendingCollection ?? "—"}
                                </p>
                            </div>
                            <FlaskConical className="hms-rad-stat__icon" />
                        </Link>
                        <Link to="/lab/queue" className="hms-rad-stat is-slate">
                            <div>
                                <p className="hms-rad-stat__label">Awaiting Reports</p>
                                <p className="hms-rad-stat__value">{stats?.awaitingReport ?? "—"}</p>
                            </div>
                            <Clock className="hms-rad-stat__icon" />
                        </Link>
                        <Link to="/lab/reports" className="hms-rad-stat is-emerald">
                            <div>
                                <p className="hms-rad-stat__label">Completed Reports</p>
                                <p className="hms-rad-stat__value">{stats?.completedToday ?? "—"}</p>
                            </div>
                            <CheckCircle2 className="hms-rad-stat__icon" />
                        </Link>
                    </div>

                    <div className="hms-rad-section">
                        <div className="hms-rad-section__head hms-dash-recent__head">
                            <div>
                                <p className="hms-rad-section__title">Recent Reports</p>
                                <p className="hms-rad-section__sub">
                                    Latest completed pathology reports
                                </p>
                            </div>
                            <Link to="/lab/reports" className="hms-dash-recent__all">
                                View all <ExternalLink className="w-3 h-3" />
                            </Link>
                        </div>
                        {recent.length === 0 ? (
                            <div className="hms-rad-section__empty">
                                <FileText className="w-5 h-5 hms-rad-section__empty-icon" />
                                <p className="hms-rad-section__empty-title">No reports yet</p>
                                <p className="hms-rad-section__empty-sub">
                                    Completed reports will appear here
                                </p>
                            </div>
                        ) : (
                            <div className="hms-rad-section__list">
                                {recent.map((order) => (
                                    <div
                                        key={order.id}
                                        className="hms-rad-row hms-dash-recent__row"
                                        onClick={() => navigate(`/lab/reports/${order.id}`)}
                                    >
                                        <div className="hms-rad-patient">
                                            <div className="hms-rad-patient__avatar">
                                                <User className="w-4 h-4 text-gray-400" />
                                            </div>
                                            <div>
                                                <p className="hms-rad-patient__name">{order.patientName}</p>
                                                <p className="hms-rad-patient__uhid">{fmtId(order.patientUhid)}</p>
                                            </div>
                                        </div>
                                        <p className="hms-rad-row__svc-name">{order.serviceName}</p>
                                        <div className="hms-rad-row__date">
                                            <Clock className="w-3 h-3 shrink-0" />
                                            <span>{fmtDateTime(order.reportedAt)}</span>
                                        </div>
                                        <ExternalLink className="w-3.5 h-3.5 hms-dash-recent__go" />
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                </>
            )}
        </div>
    );
}
