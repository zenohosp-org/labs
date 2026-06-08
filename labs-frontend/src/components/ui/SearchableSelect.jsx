import { useState, useEffect, useRef } from "react";
import { ChevronDown, Search, X } from "lucide-react";

/**
 * Generic searchable dropdown — replaces every <select> in the app.
 *
 * Props:
 *   options   — [{ value, label }]
 *   value     — currently selected value (string/number)
 *   onChange  — (value) => void   ← receives the raw value, NOT an event
 *   placeholder — trigger text when nothing is selected
 *   disabled / loading — passed through
 *   className — extra classes on the trigger (unused in hms-* version)
 *
 * Behaviour:
 *   • Shows all options before any typing.
 *   • Filters the full list as the user types.
 *   • Closes + resets query on outside click or after selection.
 *   • X clears the current value (calls onChange("")).
 */
export default function SearchableSelect({
  options = [],
  value = "",
  onChange,
  placeholder = "Select…",
  disabled = false,
  loading = false,
}) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const containerRef = useRef(null);
  const searchRef = useRef(null);

  useEffect(() => {
    const handler = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setOpen(false);
        setQuery("");
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  useEffect(() => {
    if (open) searchRef.current?.focus();
  }, [open]);

  const lower = query.toLowerCase();
  const filtered = query
    ? options.filter((o) => o.label.toLowerCase().includes(lower))
    : options;

  const selected = options.find((o) => String(o.value) === String(value));

  const pick = (opt) => {
    onChange(opt.value);
    setOpen(false);
    setQuery("");
  };

  const clear = (e) => {
    e.stopPropagation();
    onChange("");
    setQuery("");
  };

  return (
    <div ref={containerRef} className="hms-searchable-select">
      <button
        type="button"
        disabled={disabled || loading}
        onClick={() => setOpen((o) => !o)}
        className="hms-select__trigger"
      >
        <span className={`hms-select__value ${selected ? "" : "is-placeholder"}`}>
          {loading ? "Loading…" : (selected?.label ?? placeholder)}
        </span>
        <span className="hms-select__icons">
          {value && !disabled && (
            <button type="button" className="hms-select__clear" onClick={clear}>
              <X className="w-3 h-3" />
            </button>
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
              placeholder="Search…"
              className="hms-select__search-input"
            />
          </div>
          <ul className="hms-select__list">
            {filtered.length === 0 && (
              <li className="hms-select__empty">No results</li>
            )}
            {filtered.map((opt) => (
              <li
                key={opt.value}
                onClick={() => pick(opt)}
                className={`hms-select__option ${String(value) === String(opt.value) ? "is-selected" : ""}`}
              >
                {opt.label}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
