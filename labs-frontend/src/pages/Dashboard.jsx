import { useEffect, useState } from "react";
import { useAuth } from "@/context/AuthContext";
import { getLabsDashboard } from "@/api/labsClient";
import {
    FlaskConical,
    Clock,
    CheckCircle2,
    AlertTriangle,
    Loader2,
} from "lucide-react";

export default function Dashboard() {
    const { user } = useAuth();
    const [stats, setStats] = useState(null);
    const [loading, setLoading] = useState(true);
    const [err, setErr] = useState("");

    useEffect(() => {
        setLoading(true);
        getLabsDashboard()
            .then((r) => setStats(r.data))
            .catch((e) => setErr(e?.message || "Failed to load dashboard"))
            .finally(() => setLoading(false));
    }, []);

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
                <div className="hms-login__alert is-danger" role="alert">
                    <AlertTriangle className="w-4 h-4" /> {err}
                </div>
            )}

            {loading ? (
                <div className="hms-rad-section__loading">
                    <Loader2 className="w-5 h-5 animate-spin text-gray-400" />
                </div>
            ) : (
                <div className="hms-rad-stat-grid">
                    <div className="hms-rad-stat is-amber">
                        <div>
                            <p className="hms-rad-stat__label">Pending Collection</p>
                            <p className="hms-rad-stat__value">
                                {stats?.pendingCollection ?? "—"}
                            </p>
                        </div>
                        <FlaskConical className="hms-rad-stat__icon" />
                    </div>
                    <div className="hms-rad-stat is-slate">
                        <div>
                            <p className="hms-rad-stat__label">Awaiting Reports</p>
                            <p className="hms-rad-stat__value">{stats?.awaitingReport ?? "—"}</p>
                        </div>
                        <Clock className="hms-rad-stat__icon" />
                    </div>
                    <div className="hms-rad-stat is-emerald">
                        <div>
                            <p className="hms-rad-stat__label">Completed Reports</p>
                            <p className="hms-rad-stat__value">{stats?.completedToday ?? "—"}</p>
                        </div>
                        <CheckCircle2 className="hms-rad-stat__icon" />
                    </div>
                </div>
            )}
        </div>
    );
}
