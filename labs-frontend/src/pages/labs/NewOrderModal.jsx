import { useState, useEffect } from "react";
import { useAuth } from "@/context/AuthContext";
import { useNotification } from "@/context/NotificationContext";
import SearchableSelect from "@/components/ui/SearchableSelect";
import {
    patientApi,
    staffApi,
    hospitalServiceApi,
    labApi,
    admissionApi,
} from "@/api/labsClient";
import { fmtId } from "@/utils/idFormat";
import { X, Search, Loader2, UserPlus, ChevronLeft, CheckCircle2, BedDouble } from "lucide-react";

const PRIORITIES = ["ROUTINE", "URGENT", "STAT"];
const BLOOD_GROUPS = ["A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-"];

export default function NewOrderModal({ onClose, onCreated }) {
    const { user } = useAuth();
    const { notify } = useNotification();

    const [patientSearch, setPatientSearch] = useState("");
    const [patients, setPatients] = useState([]);
    const [patientSearching, setPatientSearching] = useState(false);
    const [selectedPatient, setSelectedPatient] = useState(null);

    const [activeAdmission, setActiveAdmission] = useState(null);
    const [checkingAdmission, setCheckingAdmission] = useState(false);

    const [showRegister, setShowRegister] = useState(false);
    const [registering, setRegistering] = useState(false);
    const [quickForm, setQuickForm] = useState({
        firstName: "", lastName: "", phone: "", gender: "MALE", bloodGroup: "", dob: "",
    });

    const [services, setServices] = useState([]);
    const [technicians, setTechnicians] = useState([]);
    const [form, setForm] = useState({
        serviceName: "",
        specializationName: "",
        technicianId: "",
        technicianName: "",
        priority: "ROUTINE",
        scheduledDate: "",
        sampleType: "",
        price: "",
    });
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        if (!user?.hospitalId) return;
        hospitalServiceApi.list(user.hospitalId).then(setServices).catch(() => {});
        staffApi
            .list(user.hospitalId)
            .then((users) =>
                setTechnicians(users.filter((u) => u.role?.toLowerCase() === "technician"))
            )
            .catch(() => {});
    }, [user?.hospitalId]);

    useEffect(() => {
        if (!patientSearch.trim() || patientSearch.length < 2 || !user?.hospitalId) {
            setPatients([]);
            return;
        }
        const t = setTimeout(async () => {
            setPatientSearching(true);
            try {
                const res = await patientApi.search(user.hospitalId, patientSearch);
                setPatients(res.slice(0, 6));
            } catch {
                setPatients([]);
            } finally {
                setPatientSearching(false);
            }
        }, 300);
        return () => clearTimeout(t);
    }, [patientSearch, user?.hospitalId]);

    useEffect(() => {
        if (!selectedPatient) {
            setActiveAdmission(null);
            return;
        }
        setCheckingAdmission(true);
        admissionApi
            .byPatient(selectedPatient.id)
            .then((admissions) => {
                const active = admissions.find((a) => a.status === "ADMITTED");
                setActiveAdmission(active ?? null);
            })
            .catch(() => setActiveAdmission(null))
            .finally(() => setCheckingAdmission(false));
    }, [selectedPatient]);

    const selectPatient = (p) => {
        setSelectedPatient(p);
        setPatients([]);
        setPatientSearch("");
    };

    const clearPatient = () => {
        setSelectedPatient(null);
        setActiveAdmission(null);
        setPatientSearch("");
    };

    const setQ = (field, value) => setQuickForm((f) => ({ ...f, [field]: value }));

    const handleQuickRegister = async (e) => {
        e.preventDefault();
        if (!quickForm.firstName.trim()) {
            notify("First name is required", "error");
            return;
        }
        setRegistering(true);
        try {
            const created = await patientApi.create({
                hospitalId: user.hospitalId,
                firstName: quickForm.firstName.trim(),
                lastName: quickForm.lastName.trim() || null,
                phone: quickForm.phone.trim() || null,
                gender: quickForm.gender || null,
                bloodGroup: quickForm.bloodGroup || null,
                dob: quickForm.dob || null,
            });
            notify(`${created.firstName} registered successfully`, "success");
            selectPatient(created);
            setShowRegister(false);
        } catch {
            notify("Failed to register patient", "error");
        } finally {
            setRegistering(false);
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!selectedPatient || !user?.hospitalId) return;
        if (!form.serviceName.trim()) {
            notify("Test is required", "error");
            return;
        }
        setSaving(true);
        try {
            await labApi.create({
                hospitalId: user.hospitalId,
                patientId: selectedPatient.id,
                admissionId: activeAdmission?.id ?? undefined,
                serviceName: form.serviceName,
                specializationName: form.specializationName || undefined,
                technicianId: form.technicianId || undefined,
                technicianName: form.technicianName || undefined,
                priority: form.priority,
                scheduledDate: form.scheduledDate || undefined,
                sampleType: form.sampleType || undefined,
                // Captured at order time so report generation can auto-bill.
                price: form.price ? Number(form.price) : undefined,
            });
            notify("Lab order created", "success");
            onCreated();
        } catch {
            notify("Failed to create order", "error");
        } finally {
            setSaving(false);
        }
    };

    const noResults = patientSearch.length >= 2 && !patientSearching && patients.length === 0;

    return (
        <div className="hms-rad-modal-overlay">
            <div className="hms-rad-modal">
                <div className="hms-rad-modal__hdr">
                    <div className="hms-rad-modal__hdr-left">
                        {showRegister && (
                            <button
                                type="button"
                                onClick={() => setShowRegister(false)}
                                className="hms-rad-modal__back-btn"
                            >
                                <ChevronLeft className="w-4 h-4" />
                            </button>
                        )}
                        <div>
                            <h2 className="hms-rad-modal__title">
                                {showRegister ? "Register New Patient" : "New Lab Order"}
                            </h2>
                            <p className="hms-rad-modal__sub">
                                {showRegister
                                    ? "Quick registration — patient will be added to the system"
                                    : "Create a diagnostic test request for a patient"}
                            </p>
                        </div>
                    </div>
                    <button onClick={onClose} className="hms-rad-modal__close">
                        <X className="w-4 h-4" />
                    </button>
                </div>

                {showRegister ? (
                    <form onSubmit={handleQuickRegister} className="hms-rad-modal__body">
                        <div className="hms-rad-info-bar">
                            <UserPlus className="w-4 h-4 hms-rad-info-bar__icon" />
                            <span>
                                Walk-in patient — minimum info needed. Full profile can be completed
                                later from the HMS Patients section.
                            </span>
                        </div>
                        <div className="hms-rad-grid">
                            <div>
                                <label className="hms-rad-label">First Name *</label>
                                <input
                                    required
                                    type="text"
                                    className="hms-rad-input"
                                    placeholder="e.g. Ravi"
                                    value={quickForm.firstName}
                                    onChange={(e) => setQ("firstName", e.target.value)}
                                />
                            </div>
                            <div>
                                <label className="hms-rad-label">Last Name</label>
                                <input
                                    type="text"
                                    className="hms-rad-input"
                                    placeholder="e.g. Kumar"
                                    value={quickForm.lastName}
                                    onChange={(e) => setQ("lastName", e.target.value)}
                                />
                            </div>
                            <div>
                                <label className="hms-rad-label">Phone</label>
                                <input
                                    type="text"
                                    className="hms-rad-input"
                                    placeholder="+91 98765 43210"
                                    value={quickForm.phone}
                                    onChange={(e) => setQ("phone", e.target.value)}
                                />
                            </div>
                            <div>
                                <label className="hms-rad-label">Gender</label>
                                <SearchableSelect
                                    value={quickForm.gender}
                                    onChange={(v) => setQ("gender", v)}
                                    options={[
                                        { value: "MALE", label: "Male" },
                                        { value: "FEMALE", label: "Female" },
                                        { value: "OTHER", label: "Other" },
                                    ]}
                                />
                            </div>
                            <div>
                                <label className="hms-rad-label">Date of Birth</label>
                                <input
                                    type="date"
                                    className="hms-rad-input"
                                    value={quickForm.dob}
                                    onChange={(e) => setQ("dob", e.target.value)}
                                />
                            </div>
                            <div>
                                <label className="hms-rad-label">Blood Group</label>
                                <SearchableSelect
                                    value={quickForm.bloodGroup}
                                    onChange={(v) => setQ("bloodGroup", v)}
                                    options={[
                                        { value: "", label: "Unknown" },
                                        ...BLOOD_GROUPS.map((b) => ({ value: b, label: b })),
                                    ]}
                                />
                            </div>
                        </div>
                        <div className="hms-rad-modal__foot">
                            <button
                                type="button"
                                onClick={() => setShowRegister(false)}
                                className="hms-btn-secondary"
                            >
                                Back
                            </button>
                            <button type="submit" disabled={registering} className="hms-btn-primary">
                                {registering ? (
                                    <>
                                        <Loader2 className="w-4 h-4 animate-spin" /> Registering…
                                    </>
                                ) : (
                                    <>
                                        <UserPlus className="w-4 h-4" /> Register &amp; Continue
                                    </>
                                )}
                            </button>
                        </div>
                    </form>
                ) : (
                    <form onSubmit={handleSubmit} className="hms-rad-modal__body">
                        {/* Patient */}
                        <div>
                            <label className="hms-rad-label">Patient *</label>
                            {selectedPatient ? (
                                <div className="hms-rad-pat-picked">
                                    <div className="hms-rad-pat-picked__row">
                                        <div className="hms-rad-pat-picked__body">
                                            <div className="hms-rad-pat-picked__avatar">
                                                {selectedPatient.firstName[0]}
                                                {selectedPatient.lastName?.[0] ?? ""}
                                            </div>
                                            <div>
                                                <p className="hms-rad-pat-picked__name">
                                                    {selectedPatient.firstName} {selectedPatient.lastName}
                                                </p>
                                                <p className="hms-rad-pat-picked__sub">
                                                    {fmtId(selectedPatient.uhid) ?? "New patient"}
                                                    {selectedPatient.phone ? ` · ${selectedPatient.phone}` : ""}
                                                </p>
                                            </div>
                                        </div>
                                        <button
                                            type="button"
                                            onClick={clearPatient}
                                            className="hms-rad-pat-picked__clear"
                                        >
                                            <X className="w-4 h-4" />
                                        </button>
                                    </div>

                                    {checkingAdmission && (
                                        <div className="hms-rad-admit-check">
                                            <Loader2 className="w-3 h-3 animate-spin text-gray-400" />
                                            <span className="hms-rad-admit-check__text">
                                                Checking admission status…
                                            </span>
                                        </div>
                                    )}
                                    {!checkingAdmission && activeAdmission && (
                                        <div className="hms-rad-admit-banner">
                                            <BedDouble className="w-3 h-3 hms-rad-admit-banner__icon" />
                                            <div>
                                                <p className="hms-rad-admit-banner__title">
                                                    Currently admitted · {fmtId(activeAdmission.admissionNumber)}
                                                </p>
                                                <p className="hms-rad-admit-banner__sub">
                                                    This order will be linked to their active IPD admission and
                                                    included in the discharge bill automatically.
                                                </p>
                                            </div>
                                        </div>
                                    )}
                                    {!checkingAdmission && !activeAdmission && (
                                        <div className="hms-rad-admit-outpatient">
                                            <span>Outpatient — order will not be linked to any admission.</span>
                                        </div>
                                    )}
                                </div>
                            ) : (
                                <div className="hms-rad-pat-search">
                                    <Search className="w-4 h-4 hms-rad-pat-search__icon" />
                                    <input
                                        className="hms-rad-input has-icon"
                                        placeholder="Search by name or UHID…"
                                        value={patientSearch}
                                        onChange={(e) => setPatientSearch(e.target.value)}
                                        autoFocus
                                    />
                                    {patientSearching && (
                                        <Loader2 className="w-4 h-4 animate-spin hms-rad-pat-search__spinner" />
                                    )}

                                    {patients.length > 0 && (
                                        <div className="hms-rad-pat-suggest">
                                            {patients.map((p) => (
                                                <button
                                                    key={p.id}
                                                    type="button"
                                                    onClick={() => selectPatient(p)}
                                                    className="hms-rad-pat-suggest__item"
                                                >
                                                    <div className="hms-rad-pat-suggest__avatar">
                                                        {p.firstName[0]}
                                                        {p.lastName?.[0] ?? ""}
                                                    </div>
                                                    <div>
                                                        <p className="hms-rad-pat-suggest__name">
                                                            {p.firstName} {p.lastName}
                                                        </p>
                                                        <p className="hms-rad-pat-suggest__sub">
                                                            {fmtId(p.uhid)}
                                                            {p.phone ? ` · ${p.phone}` : ""}
                                                        </p>
                                                    </div>
                                                </button>
                                            ))}
                                        </div>
                                    )}

                                    {noResults && (
                                        <div className="hms-rad-pat-suggest">
                                            <div className="hms-rad-pat-suggest__notice">
                                                No patient found for "{patientSearch}"
                                            </div>
                                            <button
                                                type="button"
                                                onClick={() => {
                                                    setPatients([]);
                                                    const parts = patientSearch.trim().split(" ");
                                                    setQuickForm((f) => ({
                                                        ...f,
                                                        firstName: parts[0] ?? "",
                                                        lastName: parts.slice(1).join(" ") ?? "",
                                                    }));
                                                    setShowRegister(true);
                                                }}
                                                className="hms-rad-pat-suggest__reg"
                                            >
                                                <div className="hms-rad-pat-suggest__reg-icon">
                                                    <UserPlus className="w-3 h-3" />
                                                </div>
                                                <div>
                                                    <p className="hms-rad-pat-suggest__reg-title">
                                                        Register as new patient
                                                    </p>
                                                    <p className="hms-rad-pat-suggest__sub">
                                                        Walk-in — add to system and continue
                                                    </p>
                                                </div>
                                            </button>
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>

                        {/* Test */}
                        <div>
                            <label className="hms-rad-label">Test *</label>
                            {services.length > 0 ? (
                                <SearchableSelect
                                    value={form.serviceName}
                                    onChange={(v) => setForm((f) => ({ ...f, serviceName: v }))}
                                    options={[
                                        { value: "", label: "Select test…" },
                                        ...services
                                            .filter((s) => s.isActive)
                                            .map((s) => ({ value: s.name, label: s.name })),
                                    ]}
                                />
                            ) : (
                                <input
                                    className="hms-rad-input"
                                    placeholder="e.g. CBC, Urine Routine, Liver Function Test…"
                                    value={form.serviceName}
                                    required
                                    onChange={(e) =>
                                        setForm((f) => ({ ...f, serviceName: e.target.value }))
                                    }
                                />
                            )}
                        </div>

                        {/* Sample type */}
                        <div>
                            <label className="hms-rad-label">Sample Type</label>
                            <input
                                className="hms-rad-input"
                                placeholder="e.g. Blood, Urine, Sputum, Stool…"
                                value={form.sampleType}
                                onChange={(e) =>
                                    setForm((f) => ({ ...f, sampleType: e.target.value }))
                                }
                            />
                        </div>

                        {/* Technician + Priority */}
                        <div className="hms-rad-grid">
                            <div>
                                <label className="hms-rad-label">Technician</label>
                                <SearchableSelect
                                    value={form.technicianId}
                                    onChange={(v) => {
                                        const tech = technicians.find((t) => t.id === v);
                                        setForm((f) => ({
                                            ...f,
                                            technicianId: v,
                                            technicianName: tech
                                                ? `${tech.firstName} ${tech.lastName ?? ""}`.trim()
                                                : "",
                                        }));
                                    }}
                                    options={[
                                        { value: "", label: "Unassigned" },
                                        ...technicians.map((t) => ({
                                            value: t.id,
                                            label: `${t.firstName} ${t.lastName ?? ""}`.trim(),
                                        })),
                                    ]}
                                />
                            </div>
                            <div>
                                <label className="hms-rad-label">Priority</label>
                                <SearchableSelect
                                    value={form.priority}
                                    onChange={(v) => setForm((f) => ({ ...f, priority: v }))}
                                    options={PRIORITIES.map((p) => ({ value: p, label: p }))}
                                />
                            </div>
                        </div>

                        {/* Scheduled date + Price */}
                        <div className="hms-rad-grid">
                            <div>
                                <label className="hms-rad-label">Scheduled Date</label>
                                <input
                                    type="date"
                                    className="hms-rad-input"
                                    value={form.scheduledDate}
                                    onChange={(e) =>
                                        setForm((f) => ({ ...f, scheduledDate: e.target.value }))
                                    }
                                />
                            </div>
                            <div>
                                <label className="hms-rad-label">Price (₹)</label>
                                <input
                                    type="number"
                                    min="0"
                                    step="0.01"
                                    className="hms-rad-input"
                                    placeholder="e.g. 350"
                                    value={form.price}
                                    onChange={(e) => setForm((f) => ({ ...f, price: e.target.value }))}
                                />
                                <p className="hms-rad-price-hint">
                                    {activeAdmission
                                        ? "Will be added to the IPD bill when the report is generated."
                                        : "A standalone OPD lab invoice will be created when the report is generated."}
                                </p>
                            </div>
                        </div>
                    </form>
                )}

                {!showRegister && (
                    <div className="hms-rad-modal__foot">
                        <button type="button" onClick={onClose} className="hms-btn-secondary">
                            Cancel
                        </button>
                        <button
                            onClick={handleSubmit}
                            disabled={saving || !selectedPatient || checkingAdmission}
                            className="hms-btn-primary"
                        >
                            {saving ? (
                                <>
                                    <Loader2 className="w-4 h-4 animate-spin" /> Creating…
                                </>
                            ) : (
                                <>
                                    <CheckCircle2 className="w-4 h-4" /> Create Order
                                </>
                            )}
                        </button>
                    </div>
                )}
            </div>
        </div>
    );
}
