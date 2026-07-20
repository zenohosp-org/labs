import { useEffect, useRef, useState } from "react";
import { Search, Loader2, FlaskConical } from "lucide-react";
import { labServiceApi } from "@/api/labsClient";
import { useDebounce } from "@/utils/hooks";

/**
 * Search the global LOINC master catalog (~60k terms) and pick one to add to
 * this hospital's offered-tests list.
 *
 * Distinct from TestPicker (which searches THIS hospital's rows to link a
 * free-text field): this searches the shared master, is not hospital-scoped,
 * and its only job is to hand a chosen catalog row back via onPick — the parent
 * seeds its editor form from it so the admin can set hospital-specific price /
 * GST before saving.
 *
 * Lives inside a Modal, so it owns just the search box + results list. Server
 * search is debounced and capped; the 60k catalog is never pulled client-side.
 */
export default function CatalogPicker({ onPick, excludeCodes }) {
    const [query, setQuery] = useState("");
    const [results, setResults] = useState([]);
    const [loading, setLoading] = useState(false);
    const [highlight, setHighlight] = useState(0);
    const inputRef = useRef(null);
    const debounced = useDebounce(query, 250);

    useEffect(() => {
        inputRef.current?.focus();
    }, []);

    useEffect(() => {
        const q = debounced.trim();
        if (q.length < 2) {
            setResults([]);
            setLoading(false);
            return;
        }
        let cancelled = false;
        setLoading(true);
        (async () => {
            try {
                const data = await labServiceApi.catalogSearch(q, { limit: 25 });
                if (!cancelled) {
                    setResults(data ?? []);
                    setHighlight(0);
                }
            } catch {
                if (!cancelled) setResults([]);
            } finally {
                if (!cancelled) setLoading(false);
            }
        })();
        return () => {
            cancelled = true;
        };
    }, [debounced]);

    // A test the hospital already offers is shown but not re-addable — upsert is
    // keyed on testCode so picking it would edit, not add; flag it instead.
    const already = (row) => excludeCodes?.has(row.testCode ?? row.loincCode);

    const choose = (row) => {
        if (!row || already(row)) return;
        onPick(row);
    };

    const onKeyDown = (e) => {
        if (results.length === 0) return;
        if (e.key === "ArrowDown") {
            e.preventDefault();
            setHighlight((h) => Math.min(h + 1, results.length - 1));
        } else if (e.key === "ArrowUp") {
            e.preventDefault();
            setHighlight((h) => Math.max(h - 1, 0));
        } else if (e.key === "Enter") {
            e.preventDefault();
            choose(results[highlight]);
        }
    };

    const q = debounced.trim();

    return (
        <div className="hms-catpick">
            <div className="hms-catpick__search">
                <Search className="w-4 h-4 shrink-0 text-gray-400" />
                <input
                    ref={inputRef}
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    onKeyDown={onKeyDown}
                    placeholder="Search 60,000 LOINC tests by name, code, or LOINC…"
                    className="hms-catpick__input"
                />
                {loading && <Loader2 className="w-4 h-4 animate-spin text-gray-400" />}
            </div>

            <div className="hms-catpick__results">
                {q.length < 2 ? (
                    <div className="hms-catpick__hint">
                        <FlaskConical className="w-5 h-5 text-gray-300" />
                        <p>Type at least 2 characters to search the master catalog.</p>
                    </div>
                ) : !loading && results.length === 0 ? (
                    <div className="hms-catpick__hint">
                        <p>No tests match “{q}”.</p>
                    </div>
                ) : (
                    results.map((row, i) => {
                        const dup = already(row);
                        return (
                            <button
                                type="button"
                                key={row.loincCode ?? row.testCode}
                                disabled={dup}
                                onMouseEnter={() => setHighlight(i)}
                                onClick={() => choose(row)}
                                className={`hms-catpick__row ${i === highlight ? "is-active" : ""} ${
                                    dup ? "is-dup" : ""
                                }`}
                            >
                                <div className="hms-catpick__row-main">
                                    <span className="hms-catpick__name">{row.name}</span>
                                    {row.isPanel && <span className="hms-catpick__panel">PANEL</span>}
                                    {dup && <span className="hms-catpick__added">Already added</span>}
                                </div>
                                <div className="hms-catpick__row-sub">
                                    {row.category && <span>{row.category}</span>}
                                    {row.specimenKind && <span>· {row.specimenKind}</span>}
                                    {row.valueType && <span>· {row.valueType}</span>}
                                    {row.loincCode && <span>· LOINC {row.loincCode}</span>}
                                </div>
                            </button>
                        );
                    })
                )}
            </div>
        </div>
    );
}
