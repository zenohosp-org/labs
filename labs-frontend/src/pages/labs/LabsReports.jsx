import { useState, useEffect, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";
import { useNotification } from "@/context/NotificationContext";
import { labApi } from "@/api/labsClient";
import { fmtId } from "@/utils/idFormat";
import Pagination from "@/components/ui/Pagination";
import { FileText, Search, Loader2, CheckCircle2, User, Clock, ExternalLink } from "lucide-react";
import { fmtDateTime } from "@/utils/date";

const PAGE_SIZE = 30;
const PRIORITY_CLS = {
    ROUTINE: "is-routine",
    URGENT: "is-urgent",
    STAT: "is-stat",
};

function LabsReports() {
    const { user } = useAuth();
    const { notify } = useNotification();
    const navigate = useNavigate();
    const [orders, setOrders] = useState([]);
    const [stats, setStats] = useState({ pendingCollection: 0, awaitingReport: 0, reportGenerated: 0 });
    const [loading, setLoading] = useState(true);
    const [search, setSearch] = useState("");
    const [page, setPage] = useState(1);

    const load = useCallback(async () => {
        if (!user?.hospitalId) return;
        setLoading(true);
        try {
            // "COMPLETED" is a backend alias matching REPORT_GENERATED + BILLED so
            // auto-billed reports don't disappear from this view.
            const [reports, statsData] = await Promise.all([
                labApi.list(user.hospitalId, "COMPLETED"),
                labApi.getStats(user.hospitalId),
            ]);
            setOrders(reports);
            setStats(statsData);
        } catch {
            notify("Failed to load reports", "error");
        } finally {
            setLoading(false);
        }
    }, [user?.hospitalId, notify]);

    useEffect(() => {
        load();
    }, [load]);

    const filtered = orders.filter((o) => {
        if (!search.trim()) return true;
        const q = search.toLowerCase();
        return (
            o.patientName.toLowerCase().includes(q) ||
            o.patientUhid.toLowerCase().includes(q) ||
            o.serviceName.toLowerCase().includes(q) ||
            o.reportId?.toLowerCase().includes(q)
        );
    });

    return (
        <div className="hms-rad-page">
            <div className="hms-rad-page__head">
                <div>
                    <h1 className="hms-rad-page__title">
                        <FileText className="w-5 h-5 hms-rad-page__title-icon" /> Lab Reports
                    </h1>
                    <p className="hms-rad-page__sub">View and manage completed lab reports</p>
                </div>
                <div className="hms-rad-chip-row">
                    <span className="hms-rad-chip is-emerald">
                        <CheckCircle2 className="w-3 h-3" /> {stats.reportGenerated} total reports
                    </span>
                </div>
            </div>

            <div className="hms-rad-rep-search">
                <Search className="w-4 h-4 hms-rad-rep-search__icon" />
                <input
                    className="hms-rad-rep-search__input"
                    placeholder="Search by patient, test, UHID, report ID…"
                    value={search}
                    onChange={(e) => {
                        setSearch(e.target.value);
                        setPage(1);
                    }}
                />
            </div>

            <div className="hms-rad-section is-slate">
                <div className="hms-rad-section__head">
                    <p className="hms-rad-section__title">Completed Reports</p>
                    <p className="hms-rad-section__sub">Lab reports with findings</p>
                </div>
                {loading ? (
                    <div className="hms-rad-section__loading">
                        <Loader2 className="w-5 h-5 animate-spin text-gray-400" />
                    </div>
                ) : filtered.length === 0 ? (
                    <div className="hms-rad-rep-empty">
                        <FileText className="w-5 h-5 opacity-30" />
                        <p className="text-13">
                            {search ? "No reports match your search" : "No completed reports yet"}
                        </p>
                    </div>
                ) : (
                    <>
                        <div className="hms-rad-table-head is-reports">
                            {["Patient", "Test", "Referred By", "Completed", "Priority", "Action"].map((h) => (
                                <p key={h} className="hms-rad-table-head__cell">{h}</p>
                            ))}
                        </div>
                        <div className="hms-rad-section__list">
                            {filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE).map((order) => (
                                <div key={order.id} className="hms-rad-row is-reports">
                                    <div className="hms-rad-patient">
                                        <div className="hms-rad-patient__avatar">
                                            <User className="w-4 h-4 text-gray-400" />
                                        </div>
                                        <div>
                                            <p className="hms-rad-patient__name">{order.patientName}</p>
                                            <p className="hms-rad-patient__uhid">{fmtId(order.patientUhid)}</p>
                                        </div>
                                    </div>
                                    <div>
                                        <p className="hms-rad-row__svc-name">{order.serviceName}</p>
                                        {order.billNo && <p className="hms-rad-row__svc-bill">{order.billNo}</p>}
                                    </div>
                                    <div className="hms-rad-tech">
                                        <p>{order.referredByName ?? "—"}</p>
                                    </div>
                                    <div className="hms-rad-row__date">
                                        <Clock className="w-3 h-3 shrink-0" />
                                        <span>{fmtDateTime(order.reportedAt)}</span>
                                    </div>
                                    <div>
                                        <span className={`hms-rad-priority ${PRIORITY_CLS[order.priority]}`}>
                                            {order.priority}
                                        </span>
                                    </div>
                                    <div>
                                        <button
                                            onClick={() => navigate(`/labs/reports/${order.id}`)}
                                            className="hms-rad-row__view-btn"
                                        >
                                            <ExternalLink className="w-3 h-3" /> View Report
                                        </button>
                                    </div>
                                </div>
                            ))}
                        </div>
                        <div className="hms-rad-rep-pagination">
                            <Pagination
                                currentPage={page}
                                totalPages={Math.ceil(filtered.length / PAGE_SIZE)}
                                totalItems={filtered.length}
                                pageSize={PAGE_SIZE}
                                onPageChange={setPage}
                            />
                        </div>
                    </>
                )}
            </div>
        </div>
    );
}

export { LabsReports as default };
