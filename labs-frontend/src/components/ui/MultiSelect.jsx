import { useEffect, useMemo, useRef, useState } from "react";
import { Check, ChevronDown, Search, X } from "lucide-react";

/**
 * Searchable multi-select dropdown.
 *
 * The sibling of SearchableSelect (single-value). Kept as a separate
 * primitive rather than a `multiple` prop on that one: SearchableSelect is
 * used across the app and its contract is "onChange receives the raw value",
 * which a multi-select fundamentally cannot honour (it emits an array). Two
 * clear components beat one with a mode flag that silently changes the
 * callback's shape.
 *
 * Reuses the .hms-select__* skin so it sits in the same visual family; only
 * the genuinely multi-specific bits (check column, toolbar, count pill) add
 * new classes.
 *
 * Props
 *   options   — [{ value, label, count? }]  count renders as a muted tally
 *   value     — array of selected values ([] = nothing selected)
 *   onChange  — (nextArray) => void
 *   placeholder     — trigger text when nothing is selected
 *   searchPlaceholder
 *   summaryNoun     — pluralised in the trigger, e.g. 3 categories
 *   disabled
 *
 * Behaviour
 *   • Empty selection means "no filter" — the caller decides what that shows.
 *   • Type to filter; Select all / Clear act on what's currently VISIBLE, so
 *     "search CHEM → Select all" selects the CHEM* classes and nothing else.
 *   • Full keyboard: ↑/↓ move, Enter/Space toggle, Home/End jump, Esc closes.
 *   • Stays open while toggling — picking several is the whole point.
 */
export default function MultiSelect({
    options = [],
    value = [],
    onChange,
    placeholder = "All",
    searchPlaceholder = "Search…",
    summaryNoun = "selected",
    disabled = false,
}) {
    const [open, setOpen] = useState(false);
    const [query, setQuery] = useState("");
    const [activeIndex, setActiveIndex] = useState(0);
    const containerRef = useRef(null);
    const searchRef = useRef(null);
    const listRef = useRef(null);

    const selected = useMemo(() => new Set(value.map(String)), [value]);

    const filtered = useMemo(() => {
        const q = query.trim().toLowerCase();
        if (!q) return options;
        return options.filter((o) => String(o.label).toLowerCase().includes(q));
    }, [options, query]);

    useEffect(() => {
        const onDocMouseDown = (e) => {
            if (containerRef.current && !containerRef.current.contains(e.target)) {
                setOpen(false);
                setQuery("");
            }
        };
        document.addEventListener("mousedown", onDocMouseDown);
        return () => document.removeEventListener("mousedown", onDocMouseDown);
    }, []);

    useEffect(() => {
        if (open) searchRef.current?.focus();
        else setQuery("");
    }, [open]);

    // A stale active index after filtering would highlight the wrong row —
    // or nothing at all once the list shrinks.
    useEffect(() => setActiveIndex(0), [query, open]);

    // Keep the keyboard-active option in view when arrowing past the fold.
    useEffect(() => {
        if (!open || !listRef.current) return;
        const el = listRef.current.querySelector(`[data-idx="${activeIndex}"]`);
        el?.scrollIntoView({ block: "nearest" });
    }, [activeIndex, open]);

    const toggle = (val) => {
        const key = String(val);
        const next = selected.has(key)
            ? value.filter((v) => String(v) !== key)
            : [...value, val];
        onChange(next);
    };

    const clear = (e) => {
        e?.stopPropagation();
        onChange([]);
    };

    // Scoped to the visible list on purpose — see the header comment.
    const selectAllVisible = () => onChange([...new Set([...value, ...filtered.map((o) => o.value)])]);

    const onKeyDown = (e) => {
        if (!open) {
            if (e.key === "ArrowDown" || e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                setOpen(true);
            }
            return;
        }
        switch (e.key) {
            case "Escape":
                e.preventDefault();
                setOpen(false);
                break;
            case "ArrowDown":
                e.preventDefault();
                setActiveIndex((i) => Math.min(i + 1, filtered.length - 1));
                break;
            case "ArrowUp":
                e.preventDefault();
                setActiveIndex((i) => Math.max(i - 1, 0));
                break;
            case "Home":
                e.preventDefault();
                setActiveIndex(0);
                break;
            case "End":
                e.preventDefault();
                setActiveIndex(filtered.length - 1);
                break;
            case "Enter":
            case " ":
                // Space must still type into the search box; only claim it
                // when the caret isn't in a text field.
                if (e.key === " " && e.target === searchRef.current) return;
                e.preventDefault();
                if (filtered[activeIndex]) toggle(filtered[activeIndex].value);
                break;
            default:
                break;
        }
    };

    const summary = () => {
        if (value.length === 0) return placeholder;
        if (value.length === 1) {
            const only = options.find((o) => String(o.value) === String(value[0]));
            return only?.label ?? String(value[0]);
        }
        return `${value.length} ${summaryNoun}`;
    };

    return (
        <div ref={containerRef} className="hms-searchable-select" onKeyDown={onKeyDown}>
            <button
                type="button"
                disabled={disabled}
                onClick={() => setOpen((o) => !o)}
                className="hms-select__trigger"
                aria-haspopup="listbox"
                aria-expanded={open}
            >
                <span className={`hms-select__value ${value.length ? "" : "is-placeholder"}`}>
                    {summary()}
                </span>
                <span className="hms-select__icons">
                    {value.length > 0 && !disabled && (
                        <span
                            role="button"
                            tabIndex={-1}
                            aria-label="Clear selection"
                            className="hms-select__clear"
                            onClick={clear}
                        >
                            <X className="w-3 h-3" />
                        </span>
                    )}
                    <ChevronDown className={`hms-select__chevron w-4 h-4 ${open ? "is-open" : ""}`} />
                </span>
            </button>

            {open && (
                <div className="hms-select__dropdown">
                    <div className="hms-select__search-row">
                        <Search className="w-3 h-3 shrink-0" />
                        <input
                            ref={searchRef}
                            type="text"
                            value={query}
                            onChange={(e) => setQuery(e.target.value)}
                            placeholder={searchPlaceholder}
                            className="hms-select__search-input"
                        />
                    </div>

                    <div className="hms-multiselect__toolbar">
                        <button type="button" onClick={selectAllVisible} disabled={filtered.length === 0}>
                            {query ? `Select these ${filtered.length}` : "Select all"}
                        </button>
                        <span className="hms-multiselect__toolbar-count">
                            {value.length} selected
                        </span>
                        <button type="button" onClick={clear} disabled={value.length === 0}>
                            Clear
                        </button>
                    </div>

                    <ul className="hms-select__list" role="listbox" aria-multiselectable="true" ref={listRef}>
                        {filtered.length === 0 && <li className="hms-select__empty">No matches</li>}
                        {filtered.map((opt, i) => {
                            const on = selected.has(String(opt.value));
                            return (
                                <li
                                    key={opt.value}
                                    data-idx={i}
                                    role="option"
                                    aria-selected={on}
                                    onMouseEnter={() => setActiveIndex(i)}
                                    onClick={() => toggle(opt.value)}
                                    className={`hms-select__option hms-multiselect__option ${
                                        on ? "is-selected" : ""
                                    } ${i === activeIndex ? "is-active" : ""}`}
                                >
                                    <span className={`hms-multiselect__check ${on ? "is-on" : ""}`}>
                                        {on && <Check className="w-3 h-3" />}
                                    </span>
                                    <span className="hms-multiselect__label">{opt.label}</span>
                                    {opt.count != null && (
                                        <span className="hms-multiselect__count">{opt.count}</span>
                                    )}
                                </li>
                            );
                        })}
                    </ul>
                </div>
            )}
        </div>
    );
}
