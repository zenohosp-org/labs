import { useEffect, useMemo, useRef, useState } from "react";
import { Search, Check, Loader2, X } from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import { labServiceApi } from "@/api/labsClient";

/**
 * Reusable picker that resolves a free-text test entry to a row in
 * lab_test_catalog. Used in three places:
 *
 *   - RangeEditorModal (Settings → Reference Ranges): picking the test a
 *     band belongs to → sets labServiceId + auto-fills testName/unit/loinc
 *   - LabPackages item editor: picking the investigation in a lab package
 *     → sets labServiceId on the item + denormalised investigation name
 *   - PackageManager test editor (health checkups): picking a test in a
 *     health package → sets labServiceId on the row
 *
 * Behaviour:
 *   - Two-way bound: caller passes `value` (string = current display name)
 *     and `labServiceId` (number | null). User typing updates `value`; clicking
 *     a suggestion calls onPick({ labServiceId, name, testCode, defaultUnit,
 *     loincCode, category }) and the caller decides which of those fields
 *     to denormalise into its row.
 *   - Free-text fallback: if the user types something that doesn't match,
 *     they can still save — labServiceId stays null and the legacy free-text
 *     column carries the value.
 *   - 250 ms debounced search; closes on outside click / Escape; arrow keys
 *     navigate, Enter selects.
 */
export default function TestPicker({
    value,
    labServiceId,
    onChange,
    onPick,
    onClear,
    placeholder = "Search lab services…",
    className = "",
    autoFocus = false,
}) {
    const { user } = useAuth();
    const [open, setOpen] = useState(false);
    const [results, setResults] = useState([]);
    const [loading, setLoading] = useState(false);
    const [highlight, setHighlight] = useState(0);
    const wrapRef = useRef(null);
    const inputRef = useRef(null);

    // Debounced search
    useEffect(() => {
        const q = (value ?? "").trim();
        if (!open || q.length < 1) {
            setResults([]);
            return;
        }
        let cancelled = false;
        setLoading(true);
        const h = setTimeout(async () => {
            try {
                const data = await labServiceApi.search(q, {
                    hospitalId: user?.hospitalId,
                    limit: 20,
                });
                if (!cancelled) {
                    setResults(data ?? []);
                    setHighlight(0);
                }
            } catch {
                if (!cancelled) setResults([]);
            } finally {
                if (!cancelled) setLoading(false);
            }
        }, 250);
        return () => {
            cancelled = true;
            clearTimeout(h);
        };
    }, [value, open, user?.hospitalId]);

    // Outside-click + Escape close
    useEffect(() => {
        if (!open) return;
        const onDoc = (e) => {
            if (wrapRef.current && !wrapRef.current.contains(e.target)) setOpen(false);
        };
        const onKey = (e) => {
            if (e.key === "Escape") setOpen(false);
        };
        document.addEventListener("mousedown", onDoc);
        document.addEventListener("keydown", onKey);
        return () => {
            document.removeEventListener("mousedown", onDoc);
            document.removeEventListener("keydown", onKey);
        };
    }, [open]);

    const linked = labServiceId != null;

    const pick = (row) => {
        onPick?.({
            labServiceId: row.id,
            name: row.name,
            testCode: row.testCode,
            defaultUnit: row.defaultUnit,
            defaultMethod: row.defaultMethod,
            loincCode: row.loincCode,
            category: row.category,
            specimenKind: row.specimenKind,
        });
        setOpen(false);
        inputRef.current?.blur();
    };

    const handleKey = (e) => {
        if (!open || results.length === 0) return;
        if (e.key === "ArrowDown") {
            e.preventDefault();
            setHighlight((h) => Math.min(h + 1, results.length - 1));
        } else if (e.key === "ArrowUp") {
            e.preventDefault();
            setHighlight((h) => Math.max(h - 1, 0));
        } else if (e.key === "Enter") {
            e.preventDefault();
            pick(results[highlight]);
        }
    };

    const wrapperCls = useMemo(
        () => `relative ${className}`.trim(),
        [className],
    );

    return (
        <div className={wrapperCls} ref={wrapRef}>
            <div className="relative">
                <Search
                    size={12}
                    className="absolute left-2 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none"
                />
                <input
                    ref={inputRef}
                    value={value ?? ""}
                    onChange={(e) => {
                        onChange?.(e.target.value);
                        // typing breaks the catalogue link
                        if (linked) onClear?.();
                        if (!open) setOpen(true);
                    }}
                    onFocus={() => setOpen(true)}
                    onKeyDown={handleKey}
                    placeholder={placeholder}
                    autoFocus={autoFocus}
                    className="w-full pl-7 pr-7 py-1.5 border border-gray-200 rounded text-13 bg-white"
                />
                {linked && (
                    <button
                        type="button"
                        onClick={() => {
                            onClear?.();
                            onChange?.("");
                            inputRef.current?.focus();
                        }}
                        className="absolute right-1.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-700"
                        title="Unlink from catalogue (keep as free text)"
                    >
                        <X size={12} />
                    </button>
                )}
                {linked && !open && (
                    <Check
                        size={12}
                        className="absolute right-6 top-1/2 -translate-y-1/2 text-emerald-600"
                    />
                )}
            </div>

            {open && (value ?? "").trim().length >= 1 && (
                <div className="absolute z-30 left-0 right-0 mt-1 bg-white border border-gray-200 rounded shadow-lg max-h-60 overflow-auto">
                    {loading ? (
                        <div className="px-3 py-2 text-12 text-gray-500 flex items-center gap-1">
                            <Loader2 size={12} className="animate-spin" /> Searching…
                        </div>
                    ) : results.length === 0 ? (
                        <div className="px-3 py-2 text-12 text-gray-500">
                            No catalogue match — will save as free text.
                        </div>
                    ) : (
                        results.map((row, i) => (
                            <button
                                type="button"
                                key={row.id}
                                onMouseDown={(e) => {
                                    // mouseDown so blur doesn't close before click fires
                                    e.preventDefault();
                                    pick(row);
                                }}
                                onMouseEnter={() => setHighlight(i)}
                                className={`w-full text-left px-3 py-1.5 text-13 ${
                                    i === highlight ? "bg-blue-50" : "hover:bg-gray-50"
                                }`}
                            >
                                <div className="flex items-center gap-2">
                                    <span className="font-bold text-gray-900">{row.name}</span>
                                    <span className="font-mono text-11 text-gray-500">
                                        {row.testCode}
                                    </span>
                                    {row.isPanel && (
                                        <span className="text-10 px-1.5 py-0.5 rounded bg-blue-100 text-blue-800 font-bold">
                                            PANEL
                                        </span>
                                    )}
                                </div>
                                <div className="text-11 text-gray-500">
                                    {row.category}
                                    {row.defaultUnit && ` · ${row.defaultUnit}`}
                                    {row.loincCode && ` · LOINC ${row.loincCode}`}
                                </div>
                            </button>
                        ))
                    )}
                </div>
            )}
        </div>
    );
}
