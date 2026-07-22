import { useCallback, useEffect, useRef, useState } from 'react';
import axios from 'axios';
import './CheckInWidget.css';

const DIRECTORY_API_URL = import.meta.env?.VITE_DIRECTORY_API_URL || 'https://api-directory.zenohosp.com';

const getAttendanceStatus = () =>
    axios.get(`${DIRECTORY_API_URL}/api/attendance/status`, { withCredentials: true });

const LaptopIcon = ({ className }) => (
    <svg className={className} width="15" height="15" viewBox="0 0 24 24" fill="none"
         stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="4" width="18" height="12" rx="2" />
        <path d="M2 20h20" />
    </svg>
);

/**
 * Suite-wide attendance display (top bar) — read-only.
 *
 * Attendance has exactly one writer: the biometric device, which reports into
 * People and is mirrored onto Directory's session record. This widget shows
 * that record and never changes it. There is deliberately no toggle — a browser
 * button was a second write path that disagreed with the device, and no amount
 * of syncing made two sources of truth agree.
 *
 * Renders nothing when Directory is unreachable, or when the hospital has no
 * People/HR app (`attendanceEnabled: false`) and therefore no device ingestion:
 * a permanent 00:00:00 reads as broken rather than not-purchased.
 */
export default function CheckInWidget() {
    const [status, setStatus] = useState(null); // { checkedIn, checkInAt, checkOutAt, todaySeconds, attendanceEnabled }
    // 1s ticker so the seconds counter re-renders while checked in.
    const [, setTick] = useState(0);
    // When the status snapshot was taken — live counter = snapshot + elapsed.
    const fetchedAtRef = useRef(Date.now());

    const refresh = useCallback(() => {
        getAttendanceStatus()
            .then((res) => {
                fetchedAtRef.current = Date.now();
                setStatus(res.data?.data ?? res.data);
            })
            .catch(() => setStatus(null)); // Directory unreachable — hide, never block the app
    }, []);

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

    if (status === null) return null;            // no state yet, or Directory down
    if (status.attendanceEnabled === false) return null; // hospital has no device attendance

    // Today's running total in seconds: server snapshot + live elapsed while in.
    const baseSeconds = status.todaySeconds ?? (status.todayMinutes ?? 0) * 60;
    const liveSeconds = status.checkedIn
        ? baseSeconds + Math.max(0, Math.floor((Date.now() - fetchedAtRef.current) / 1000))
        : baseSeconds;
    const hh = String(Math.floor(liveSeconds / 3600)).padStart(2, '0');
    const mm = String(Math.floor((liveSeconds % 3600) / 60)).padStart(2, '0');
    const ss = String(liveSeconds % 60).padStart(2, '0');
    const elapsed = `${hh}:${mm}:${ss}`;

    const state = status.checkedIn
        ? 'Checked in'
        : liveSeconds > 0
            ? 'Checked out'
            : 'Not checked in';

    const punchTime = (iso) => {
        if (!iso) return null;
        const d = new Date(iso);
        return Number.isNaN(d.getTime())
            ? null
            : d.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' });
    };
    const at = status.checkedIn ? punchTime(status.checkInAt) : punchTime(status.checkOutAt);
    const tooltip = `${state}${at ? ` at ${at}` : ''} — ${elapsed} today. Recorded by the biometric device.`;

    return (
        <div className="checkin-widget is-topbar" title={tooltip}>
            <LaptopIcon className={`checkin-icon${status.checkedIn ? ' is-in' : ''}`} />
            <span className="checkin-label">{elapsed} · {state}</span>
        </div>
    );
}
