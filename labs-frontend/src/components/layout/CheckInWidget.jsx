import { useCallback, useEffect, useRef, useState } from 'react';
import axios from 'axios';
import './CheckInWidget.css';

const DIRECTORY_API_URL = import.meta.env?.VITE_DIRECTORY_API_URL || 'https://api-directory.zenohosp.com';

const getAttendanceStatus = () =>
    axios.get(`${DIRECTORY_API_URL}/api/attendance/status`, { withCredentials: true });
const attendanceCheckIn = (mode) =>
    axios.post(`${DIRECTORY_API_URL}/api/attendance/check-in`, mode ? { mode } : {}, { withCredentials: true });
const attendanceCheckOut = () =>
    axios.post(`${DIRECTORY_API_URL}/api/attendance/check-out`, {}, { withCredentials: true });

const LaptopIcon = ({ className }) => (
    <svg className={className} width="15" height="15" viewBox="0 0 24 24" fill="none"
         stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="4" width="18" height="12" rx="2" />
        <path d="M2 20h20" />
    </svg>
);

/**
 * Suite-wide Remote Work check-in toggle (top bar). State lives in Directory
 * and is shared by every ZenoHosp app via the SSO cookie, so the checked-in
 * state stays consistent wherever the user is.
 *
 * The timer shows TODAY'S ACCUMULATED total as HH:MM:SS — it ticks every
 * second while checked in and, after a check-out/in cycle, resumes from the
 * day's total (Directory sums all of today's sessions into todaySeconds).
 *
 * Directory being unreachable must never block the app: a failed poll hides
 * the widget rather than erroring.
 */
export default function CheckInWidget() {
    const [status, setStatus] = useState(null); // { checkedIn, checkInAt, todaySeconds, todayMinutes }
    const [busy, setBusy] = useState(false);
    // 1s ticker so the seconds counter re-renders while checked in.
    const [, setTick] = useState(0);
    // When the status snapshot was taken — live counter = snapshot + elapsed.
    const fetchedAtRef = useRef(Date.now());

    const applyStatus = useCallback((data) => {
        fetchedAtRef.current = Date.now();
        setStatus(data);
    }, []);

    const refresh = useCallback(() => {
        getAttendanceStatus()
            .then((res) => applyStatus(res.data?.data ?? res.data))
            .catch(() => setStatus(null)); // Directory unreachable — hide, never block the app
    }, [applyStatus]);

    useEffect(() => {
        refresh();
        window.addEventListener('focus', refresh);
        return () => window.removeEventListener('focus', refresh);
    }, [refresh]);

    useEffect(() => {
        if (!status?.checkedIn) return;
        const t = setInterval(() => setTick((n) => n + 1), 1000);
        return () => clearInterval(t);
    }, [status?.checkedIn]);

    if (status === null) return null; // no state yet (or Directory down) — render nothing

    // Today's running total in seconds: server snapshot + live elapsed while in.
    const baseSeconds = status.todaySeconds ?? (status.todayMinutes ?? 0) * 60;
    const liveSeconds = status.checkedIn
        ? baseSeconds + Math.max(0, Math.floor((Date.now() - fetchedAtRef.current) / 1000))
        : baseSeconds;
    const hh = String(Math.floor(liveSeconds / 3600)).padStart(2, '0');
    const mm = String(Math.floor((liveSeconds % 3600) / 60)).padStart(2, '0');
    const ss = String(liveSeconds % 60).padStart(2, '0');
    const label = `${hh}:${mm}:${ss}`;
    const tooltip = status.checkedIn
        ? `Checked in — ${label} worked today`
        : `Checked out — ${label} worked today. Toggle to check in.`;

    const toggle = async () => {
        if (busy) return;
        if (status.checkedIn && !window.confirm(`Check out? You've logged ${label} today.`)) return;
        setBusy(true);
        try {
            const res = status.checkedIn ? await attendanceCheckOut() : await attendanceCheckIn('REMOTE');
            applyStatus(res.data?.data ?? res.data);
        } catch {
            refresh(); // converge on server truth rather than guessing
        } finally {
            setBusy(false);
        }
    };

    return (
        <div className="checkin-widget is-topbar" title={tooltip}>
            <LaptopIcon className={`checkin-icon${status.checkedIn ? ' is-in' : ''}`} />
            <span className="checkin-label">{label}</span>
            <button
                type="button"
                role="switch"
                aria-checked={status.checkedIn}
                aria-label={status.checkedIn ? 'Check out' : 'Check in'}
                className={`checkin-toggle${status.checkedIn ? ' is-on' : ''}`}
                onClick={toggle}
                disabled={busy}
            >
                <span className="checkin-knob" />
            </button>
        </div>
    );
}
