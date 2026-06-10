import { useState, useEffect, useMemo } from "react";
import {
    Plus,
    Edit2,
    Trash2,
    Power,
    MoreHorizontal,
    AlertTriangle,
    Activity,
    Filter,
} from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import { useNotification } from "@/context/NotificationContext";
import { referenceRangeApi } from "@/api/labsClient";
import {
    Alert,
    Badge,
    Button,
    Menu,
    Modal,
    PageHeader,
    Pagination,
    SearchBar,
    Table,
} from "@/components/ui";
import RangeEditorModal from "@/components/modals/RangeEditorModal";

const PAGE_SIZE = 30;

const SEX_TONE = {
    MALE: "info",
    FEMALE: "warning",
    ANY: "neutral",
};

/**
 * Reference Ranges admin — per-hospital lab + vitals catalogue. Lazy-seeded
 * by the backend the first time a hospital reads its list, so the table is
 * never empty on first open. Admins edit / disable bands here; the result
 * entry UI consumes the same catalogue via /api/reference-ranges/match.
 */
function ReferenceRanges() {
    const { user } = useAuth();
    const { notify } = useNotification();

    const [rows, setRows] = useState([]);
    const [loading, setLoading] = useState(true);
    const [search, setSearch] = useState("");
    const [page, setPage] = useState(1);
    const [modal, setModal] = useState({ open: false, range: null });
    const [confirmDelete, setConfirmDelete] = useState(null);
    const [categoryFilter, setCategoryFilter] = useState("ALL");

    const load = async () => {
        if (!user?.hospitalId) return;
        setLoading(true);
        try {
            const data = await referenceRangeApi.list(user.hospitalId);
            setRows(data);
        } catch {
            notify("Failed to load reference ranges", "error");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        load();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [user?.hospitalId]);

    const categories = useMemo(() => {
        const set = new Set(rows.map((r) => r.category).filter(Boolean));
        return ["ALL", ...Array.from(set).sort()];
    }, [rows]);

    const filtered = useMemo(() => {
        const q = search.trim().toLowerCase();
        return rows.filter((r) => {
            const matchesSearch =
                !q ||
                r.testName?.toLowerCase().includes(q) ||
                r.rangeText?.toLowerCase().includes(q) ||
                r.unit?.toLowerCase().includes(q);
            const matchesCat = categoryFilter === "ALL" || r.category === categoryFilter;
            return matchesSearch && matchesCat;
        });
    }, [rows, search, categoryFilter]);

    const paginated = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
    const totalPages = Math.ceil(filtered.length / PAGE_SIZE);

    const handleToggle = async (id) => {
        try {
            const updated = await referenceRangeApi.toggle(id);
            setRows((prev) => prev.map((r) => (r.id === id ? updated : r)));
            notify("Status updated", "success");
        } catch {
            notify("Failed to toggle status", "error");
        }
    };

    const handleDelete = async () => {
        if (!confirmDelete) return;
        try {
            await referenceRangeApi.delete(confirmDelete.id);
            setRows((prev) => prev.filter((r) => r.id !== confirmDelete.id));
            notify("Reference range deleted", "success");
        } catch {
            notify("Failed to delete", "error");
        } finally {
            setConfirmDelete(null);
        }
    };

    const renderAgeWindow = (r) => {
        const a = r.minAgeYears ?? null;
        const b = r.maxAgeYears ?? null;
        if (a == null && b == null) return "Any age";
        if (a != null && b != null) return `${a} – ${b} yrs`;
        if (a != null) return `${a}+ yrs`;
        return `≤ ${b} yrs`;
    };

    const renderBounds = (r) => {
        const min = r.minValue;
        const max = r.maxValue;
        if (min == null && max == null) return <span className="text-gray-300">—</span>;
        if (min != null && max != null) return `${min} – ${max}`;
        if (min != null) return `≥ ${min}`;
        return `≤ ${max}`;
    };

    const columns = [
        {
            header: "Test",
            width: "24%",
            render: (r) => (
                <div className="flex flex-col gap-0.5">
                    <span className="font-bold text-gray-900 text-14">{r.testName}</span>
                    {r.category && (
                        <span className="text-12 text-gray-500">{r.category}</span>
                    )}
                </div>
            ),
        },
        {
            header: "Sex",
            width: "10%",
            render: (r) => (
                <Badge tone={SEX_TONE[r.sex] ?? "neutral"} soft>
                    {r.sex}
                </Badge>
            ),
        },
        { header: "Age window", width: "14%", render: renderAgeWindow },
        {
            header: "Bounds",
            width: "16%",
            render: (r) => (
                <span className="font-bold text-gray-900">{renderBounds(r)}</span>
            ),
        },
        {
            header: "Unit",
            width: "10%",
            render: (r) => r.unit || <span className="text-gray-300">—</span>,
        },
        {
            header: "Display",
            width: "16%",
            render: (r) => <span className="text-13 text-gray-700">{r.rangeText}</span>,
        },
        {
            header: "Status",
            width: "10%",
            render: (r) => (
                <Badge tone={r.isActive ? "success" : "danger"} soft>
                    {r.isActive ? "Active" : "Inactive"}
                </Badge>
            ),
        },
        {
            header: "",
            width: "8%",
            align: "right",
            render: (r) => (
                <Menu
                    triggerIcon={<MoreHorizontal size={18} />}
                    triggerLabel="Row actions"
                    align="right"
                    items={[
                        {
                            label: "Edit band",
                            icon: <Edit2 size={14} />,
                            onClick: () => setModal({ open: true, range: r }),
                        },
                        {
                            label: r.isActive ? "Deactivate" : "Activate",
                            icon: <Power size={14} />,
                            onClick: () => handleToggle(r.id),
                        },
                        { divider: true },
                        {
                            label: "Delete band",
                            icon: <Trash2 size={14} />,
                            tone: "danger",
                            onClick: () => setConfirmDelete(r),
                        },
                    ]}
                />
            ),
        },
    ];

    const titleNode = (
        <span className="inline-flex items-center gap-3">
            Reference Ranges
            <Badge tone="info">{rows.length} bands</Badge>
        </span>
    );

    return (
        <div className="flex flex-col gap-4">
            <PageHeader
                title={titleNode}
                actions={
                    <Button
                        variant="primary"
                        onClick={() => setModal({ open: true, range: null })}
                    >
                        <Plus size={14} strokeWidth={2.4} /> New band
                    </Button>
                }
            />

            <div className="hms-page-content">
                <div className="flex items-center gap-3">
                    <div className="flex-1">
                        <SearchBar
                            value={search}
                            onChange={(v) => {
                                setSearch(v);
                                setPage(1);
                            }}
                            placeholder="Search by test name, display text, unit…"
                        />
                    </div>
                    <div className="flex gap-1.5">
                        {categories.map((c) => (
                            <button
                                key={c}
                                onClick={() => {
                                    setCategoryFilter(c);
                                    setPage(1);
                                }}
                                className={`hms-rad-priority-btn ${
                                    categoryFilter === c ? "is-on" : ""
                                }`}
                            >
                                <Filter className="w-3 h-3" /> {c}
                            </button>
                        ))}
                    </div>
                </div>

                <Table
                    columns={columns}
                    data={paginated}
                    loading={loading}
                    loadingMessage={
                        <span className="text-gray-500">Loading reference ranges…</span>
                    }
                    emptyMessage={
                        <div className="hms-cell-empty">
                            <span className="hms-cell-empty__icon">
                                <Activity size={22} />
                            </span>
                            <div className="hms-cell-empty__text">
                                {search || categoryFilter !== "ALL"
                                    ? "No bands match your filters."
                                    : "No reference ranges configured yet."}
                            </div>
                        </div>
                    }
                />

                {!loading && filtered.length > 0 && totalPages > 1 && (
                    <div className="pt-1">
                        <Pagination
                            currentPage={page}
                            totalPages={totalPages}
                            totalItems={filtered.length}
                            pageSize={PAGE_SIZE}
                            onPageChange={setPage}
                        />
                    </div>
                )}
            </div>

            {modal.open && (
                <RangeEditorModal
                    isOpen={modal.open}
                    onClose={() => setModal({ open: false, range: null })}
                    range={modal.range}
                    onSuccess={load}
                />
            )}

            <Modal
                isOpen={!!confirmDelete}
                onClose={() => setConfirmDelete(null)}
                size="sm"
                title="Delete reference band"
                footer={
                    <>
                        <Button variant="cancel" onClick={() => setConfirmDelete(null)}>
                            Keep band
                        </Button>
                        <Button variant="danger" onClick={handleDelete}>
                            Delete band
                        </Button>
                    </>
                }
            >
                <Alert tone="danger" icon={<AlertTriangle size={16} />}>
                    This will permanently remove the band. Already-recorded results stay as
                    they were entered.
                </Alert>
                {confirmDelete && (
                    <div className="hms-confirm-summary">
                        <span className="hms-icon-tile is-sm">
                            <Activity size={16} />
                        </span>
                        <div className="min-w-0">
                            <div className="hms-confirm-summary__title">
                                {confirmDelete.testName} · {confirmDelete.sex}
                            </div>
                            <div className="hms-confirm-summary__sub">
                                {confirmDelete.rangeText}
                            </div>
                        </div>
                    </div>
                )}
            </Modal>
        </div>
    );
}

export { ReferenceRanges as default };
