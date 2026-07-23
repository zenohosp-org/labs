import axios from "axios";
import { DEV_MOCK_AUTH } from "@/utils/devMockAuth";

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "";

const api = axios.create({
    baseURL: API_BASE_URL,
    withCredentials: true,
    headers: { "Content-Type": "application/json" },
});

// Local-dev SSO bypass: when VITE_DEV_MOCK_AUTH=true the .env.local file
// supplies a real JWT (signed with the shared jwt.secret). The backend
// JwtFilter accepts the Bearer header so no backend mock endpoint is needed.
// Same pattern as HMS [HMS-frontend/src/utils/api.js:40] and pharmacy.
const isMockAuth =
    DEV_MOCK_AUTH && import.meta.env.VITE_MOCK_JWT;
if (isMockAuth) {
    api.interceptors.request.use((config) => {
        config.headers.Authorization = `Bearer ${import.meta.env.VITE_MOCK_JWT}`;
        return config;
    });
}

api.interceptors.response.use(
    (response) => response,
    (error) => {
        if (error.response?.status === 401) {
            // In local mock-auth dev, never bounce to /login on a stray 401 —
            // it would clobber the mock session.
            if (!isMockAuth && !error.config?.url?.includes("/api/user/me")) {
                window.location.href = "/login";
            }
        }
        return Promise.reject(error);
    },
);

// ── Auth ───────────────────────────────────────────────
export const getMe = () => api.get("/api/user/me");
export const logout = () => api.post("/api/auth/logout");
export const getLabsDashboard = () => api.get("/api/labs/dashboard");

// ── Radiology (mirrors HMS radiologyApi character-for-character) ──
export const radiologyApi = {
    list: async (hospitalId, status) => {
        const params = { hospitalId };
        if (status) params.status = status;
        const { data } = await api.get("/api/radiology", { params });
        return data;
    },
    get: async (id) => {
        const { data } = await api.get(`/api/radiology/${id}`);
        return data;
    },
    getByPatient: async (patientId) => {
        const { data } = await api.get(`/api/radiology/patient/${patientId}`);
        return data;
    },
    getByAdmission: async (admissionId) => {
        const { data } = await api.get(`/api/radiology/admission/${admissionId}`);
        return data;
    },
    getStats: async (hospitalId) => {
        const { data } = await api.get("/api/radiology/stats", { params: { hospitalId } });
        return data;
    },
    create: async (payload) => {
        const { data } = await api.post("/api/radiology", payload);
        return data;
    },
    markScanned: async (id) => {
        const { data } = await api.patch(`/api/radiology/${id}/scan`);
        return data;
    },
    /** Phase 7 — PENDING_SCAN → IN_PROGRESS. Stamps started_at + actor server-side. */
    markStarted: async (id) => {
        const { data } = await api.patch(`/api/radiology/${id}/start`);
        return data;
    },
    generateReport: async (id, findings, observation) => {
        const { data } = await api.patch(`/api/radiology/${id}/report`, { findings, observation });
        return data;
    },
    /** Phase 9 — IN_PROGRESS/AWAITING_REPORT → REPORT_GENERATED, guarded on findings text. */
    markCompleted: async (id) => {
        const { data } = await api.patch(`/api/radiology/${id}/complete`);
        return data;
    },
    /** Phase 9 — soft cancel. Allowed from PENDING_SCAN / AWAITING_REPORT / IN_PROGRESS. */
    cancelOrder: async (id, reason) => {
        const { data } = await api.patch(`/api/radiology/${id}/cancel`, reason ? { reason } : {});
        return data;
    },
};

// Pathology / lab-orders endpoints. Mirrors radiologyApi shape so the queue
// page can be built from the same primitives.
export const labApi = {
    list: async (hospitalId, status) => {
        const params = { hospitalId };
        if (status) params.status = status;
        const { data } = await api.get("/api/lab", { params });
        return data;
    },
    get: async (id) => {
        const { data } = await api.get(`/api/lab/${id}`);
        return data;
    },
    getByPatient: async (patientId) => {
        const { data } = await api.get(`/api/lab/patient/${patientId}`);
        return data;
    },
    getByAdmission: async (admissionId) => {
        const { data } = await api.get(`/api/lab/admission/${admissionId}`);
        return data;
    },
    getStats: async (hospitalId) => {
        const { data } = await api.get("/api/lab/stats", { params: { hospitalId } });
        return data;
    },
    markCollected: async (id) => {
        const { data } = await api.patch(`/api/lab/${id}/collect`);
        return data;
    },
    /** Phase 7 — lab receiving desk takes custody. Stamps received_at + actor; status stays AWAITING_REPORT. */
    markReceived: async (id) => {
        const { data } = await api.patch(`/api/lab/${id}/receive`);
        return data;
    },
    /** Phase 7 — AWAITING_REPORT → IN_PROGRESS (tech ran the analyser). Stamps started_at + actor. */
    markStarted: async (id) => {
        const { data } = await api.patch(`/api/lab/${id}/start`);
        return data;
    },
    generateReport: async (id, findings, observation) => {
        const { data } = await api.patch(`/api/lab/${id}/report`, { findings, observation });
        return data;
    },
    /** Phase 9 — IN_PROGRESS → REPORT_GENERATED, guarded on report data presence. */
    markCompleted: async (id) => {
        const { data } = await api.patch(`/api/lab/${id}/complete`);
        return data;
    },
    /** Phase 9 — soft cancel. Allowed from PENDING_COLLECTION / AWAITING_REPORT / IN_PROGRESS. */
    cancelOrder: async (id, reason) => {
        const { data } = await api.patch(`/api/lab/${id}/cancel`, reason ? { reason } : {});
        return data;
    },
    /** Legacy hard-DELETE — only used for PENDING_COLLECTION orphans (zero clinical data). */
    cancel: async (id) => {
        await api.delete(`/api/lab/${id}`);
    },
};

// ── Patients (shared HMS DB) ───────────────────────────
export const patientApi = {
    search: async (hospitalId, q) => {
        const { data } = await api.get("/api/patients/search", { params: { hospitalId, q } });
        return data;
    },
    get: async (id) => {
        const { data } = await api.get(`/api/patients/${id}`);
        return data;
    },
    create: async (payload) => {
        const { data } = await api.post("/api/patients", payload);
        return data;
    },
};

// ── Staff (technicians for radiology assignment) ───────
export const staffApi = {
    list: async (hospitalId) => {
        const { data } = await api.get("/api/users", { params: { hospitalId } });
        return data;
    },
};

// ── Hospital services (proxied to HMS) ─────────────────
// Method shape mirrors HMS hospitalServiceApi: list/create/update/delete/toggleStatus.
export const hospitalServiceApi = {
    list: async (hospitalId) => {
        const { data } = await api.get("/api/hospital-services", { params: { hospitalId } });
        return data;
    },
    create: async (payload) => {
        const { data } = await api.post("/api/hospital-services", payload);
        return data;
    },
    update: async (id, payload) => {
        const { data } = await api.put(`/api/hospital-services/${id}`, payload);
        return data;
    },
    delete: async (id) => {
        await api.delete(`/api/hospital-services/${id}`);
    },
    toggleStatus: async (id) => {
        await api.patch(`/api/hospital-services/${id}/toggle-status`);
    },
};

// ── Equipment (proxied read-only to asset-manager) ─────
// Powers the "equipment used" picker on result entry. asset-manager resolves
// hospitalId from the JWT itself and returns that hospital's non-disposed
// assets — no client param needed.
export const equipmentApi = {
    list: async () => {
        const { data } = await api.get("/api/equipment");
        return data;
    },
};

// ── Billing (proxied to HMS) ───────────────────────────
// Bank accounts list + payment collection. HMS owns the invoice lifecycle;
// labs just gives lab/radiology staff a counter-side payment action.
export const billingApi = {
    listBankAccounts: async (hospitalId) => {
        const { data } = await api.get("/api/bank-accounts", { params: { hospitalId } });
        return data;
    },
    /**
     * Collect a payment against an invoice the labs auto-bill flow already
     * created. Mirrors HMS BillingController PaymentRequest:
     *   { amount, paymentMethod, bankAccountId, collectedBy }
     */
    collectPayment: async (invoiceId, payload) => {
        const { data } = await api.post(
            `/api/billing/invoices/${invoiceId}/payments`,
            payload,
        );
        return data;
    },
};

// ── Departments (proxied read-only to HMS) ─────────────
// Primary catalogue for the Services page now that HMS migrated
// hospital_services from specialization_id → department_id.
export const departmentApi = {
    list: async (hospitalId, activeOnly = false) => {
        const { data } = await api.get("/api/departments", {
            params: { hospitalId, activeOnly },
        });
        return data;
    },
};

// ── Specializations (proxied read-only to HMS) ─────────
// Retained for callers that still reference specialization names; not used
// by the Services page anymore.
export const specializationApi = {
    list: async (hospitalId) => {
        const { data } = await api.get("/api/specializations", { params: { hospitalId } });
        return data;
    },
};

// ── Health Checkups (labs-owned) ───────────────────────
export const checkupApi = {
    getPackages: async (hospitalId, activeOnly = false) => {
        const { data } = await api.get("/api/health-checkups/packages", {
            params: { hospitalId, activeOnly },
        });
        return data;
    },
    savePackage: async (hospitalId, payload) => {
        const { data } = await api.post("/api/health-checkups/packages", payload, {
            params: { hospitalId },
        });
        return data;
    },
    togglePackage: async (id) => api.patch(`/api/health-checkups/packages/${id}/toggle`),
    deletePackage: async (id) => api.delete(`/api/health-checkups/packages/${id}`),

    getBookings: async (hospitalId, params = {}) => {
        const { data } = await api.get("/api/health-checkups/bookings", {
            params: { hospitalId, ...params },
        });
        return data;
    },
    getBooking: async (id) => {
        const { data } = await api.get(`/api/health-checkups/bookings/${id}`);
        return data;
    },
    createBooking: async (hospitalId, payload) => {
        const { data } = await api.post("/api/health-checkups/bookings", payload, {
            params: { hospitalId },
        });
        return data;
    },
    updateStatus: async (id, status) => {
        const { data } = await api.patch(`/api/health-checkups/bookings/${id}/status`, { status });
        return data;
    },
    updateResult: async (bookingId, resultId, payload) => {
        const { data } = await api.patch(
            `/api/health-checkups/bookings/${bookingId}/results/${resultId}`,
            payload
        );
        return data;
    },
    saveDoctorNotes: async (bookingId, payload) => {
        const { data } = await api.patch(
            `/api/health-checkups/bookings/${bookingId}/doctor-notes`,
            payload
        );
        return data;
    },
    assignDoctor: async (bookingId, doctorId) => {
        const { data } = await api.patch(`/api/health-checkups/bookings/${bookingId}/doctor`, {
            doctorId: doctorId || null,
        });
        return data;
    },
    getStats: async (hospitalId) => {
        const { data } = await api.get("/api/health-checkups/stats", { params: { hospitalId } });
        return data;
    },
};

// ── Lab Packages (labs-owned) ──────────────────────────
// Ad-hoc investigation bundles ("Liver Profile" = SGPT + SGOT + Bilirubin
// + ALP + Albumin at a flat combo rate). Distinct from health-checkup
// packages, which are wellness bundles.
export const labPackageApi = {
    list: async (hospitalId, activeOnly = false) => {
        const { data } = await api.get("/api/lab-packages", {
            params: { hospitalId, activeOnly },
        });
        return data;
    },
    save: async (hospitalId, payload) => {
        const { data } = await api.post("/api/lab-packages", payload, {
            params: { hospitalId },
        });
        return data;
    },
    toggle: async (id) => api.patch(`/api/lab-packages/${id}/toggle`),
    delete: async (id) => api.delete(`/api/lab-packages/${id}`),
};

// ── Lab Reference Ranges (labs-owned) ──────────────────
// Per-hospital catalogue of normal bands per (testName, sex, age window).
// Lazy-seeded on the first GET so a fresh hospital sees defaults immediately.
export const referenceRangeApi = {
    list: async (hospitalId) => {
        const { data } = await api.get("/api/reference-ranges", { params: { hospitalId } });
        return data;
    },
    create: async (payload) => {
        const { data } = await api.post("/api/reference-ranges", payload);
        return data;
    },
    update: async (id, payload) => {
        const { data } = await api.put(`/api/reference-ranges/${id}`, payload);
        return data;
    },
    delete: async (id) => {
        await api.delete(`/api/reference-ranges/${id}`);
    },
    toggle: async (id) => {
        const { data } = await api.patch(`/api/reference-ranges/${id}/toggle`);
        return data;
    },
    /** Match a measured value against the catalogue and tag it LOW/NORMAL/HIGH. */
    match: async ({ testName, sex, ageYears, value }) => {
        const params = { testName };
        if (sex) params.sex = sex;
        if (ageYears != null) params.ageYears = ageYears;
        if (value != null && value !== "") params.value = value;
        try {
            const { data } = await api.get("/api/reference-ranges/match", { params });
            return data;
        } catch (e) {
            if (e?.response?.status === 204) return null;
            throw e;
        }
    },
};

// ── Admissions ─────────────────────────────────────────
export const admissionApi = {
    byPatient: async (patientId) => {
        const { data } = await api.get(`/api/patients/${patientId}/admissions`);
        return data;
    },
};

// ── Specimens (Phase 1 — per-container chain of custody) ───────────────
// One LabOrder can have many specimens (CBC + LFT + Lipid from one draw =
// 3 tubes). Each specimen flows collected → received → accessioned, or
// gets terminated by reject(reasonCode). Backend auto-creates a default
// specimen on the legacy /collect path when nothing is posted explicitly,
// so this API is additive for new UIs that want explicit barcode entry.
export const specimenApi = {
    listForOrder: async (labOrderId) => {
        const { data } = await api.get(`/api/lab/${labOrderId}/specimens`);
        return data;
    },
    create: async (labOrderId, payload) => {
        const { data } = await api.post(`/api/lab/${labOrderId}/specimens`, payload);
        return data;
    },
    get: async (id) => {
        const { data } = await api.get(`/api/specimens/${id}`);
        return data;
    },
    receive: async (id, payload = {}) => {
        const { data } = await api.patch(`/api/specimens/${id}/receive`, payload);
        return data;
    },
    accession: async (id, accessionedByUserId) => {
        const params = accessionedByUserId ? { accessionedByUserId } : {};
        const { data } = await api.patch(`/api/specimens/${id}/accession`, null, { params });
        return data;
    },
    reject: async (id, payload) => {
        const { data } = await api.patch(`/api/specimens/${id}/reject`, payload);
        return data;
    },
};

// ── Rejection reasons (Phase 1 — controlled vocab seeded by V4) ────────
export const rejectionReasonApi = {
    list: async (activeOnly = true) => {
        const { data } = await api.get("/api/lab-rejection-reasons", { params: { activeOnly } });
        return data;
    },
};

// ── Test catalog (Phase 2 — per-hospital LOINC-coded test list) ────────
// Lazy-seeded on first GET with ~47 Indian-lab analytes across 7 panels.
// Phase 3 — also the source of truth pickers consume from when adding
// tests to ranges, lab packages, and health checkup packages.
export const labServiceApi = {
    list: async (hospitalId, activeOnly = true) => {
        const { data } = await api.get("/api/lab-services", {
            params: { hospitalId, activeOnly },
        });
        return data;
    },
    expandPanel: async (panelCode, hospitalId) => {
        const { data } = await api.get(`/api/lab-services/panel/${panelCode}`, {
            params: { hospitalId },
        });
        return data;
    },
    /** Phase 3 — fuzzy search for the test picker (name / code / aliases / LOINC). */
    search: async (q, { hospitalId, limit = 20 } = {}) => {
        if (!q || !q.trim()) return [];
        const params = { q, limit };
        if (hospitalId) params.hospitalId = hospitalId;
        const { data } = await api.get("/api/lab-services/search", { params });
        return data;
    },
    /**
     * Phase 11 — search the GLOBAL LOINC master catalog (hospital-agnostic).
     * Backs the "Add from catalog" picker; the picked row seeds the editor form
     * and is created for this hospital via upsert(). Not hospital-scoped.
     */
    catalogSearch: async (q, { limit = 20 } = {}) => {
        if (!q || !q.trim()) return [];
        const { data } = await api.get("/api/lab-services/catalog", {
            params: { q, limit },
        });
        return data;
    },
    /** Phase 3 — ranges that belong to a specific test row. */
    rangesFor: async (testId) => {
        const { data } = await api.get(`/api/lab-services/${testId}/ranges`);
        return data;
    },
    upsert: async (payload) => {
        const { data } = await api.post("/api/lab-services", payload);
        return data;
    },
    toggle: async (id) => {
        const { data } = await api.patch(`/api/lab-services/${id}/toggle`);
        return data;
    },
    delete: async (id) => {
        await api.delete(`/api/lab-services/${id}`);
    },
};

// ── Per-analyte results (Phase 2 — replaces findings blob) ─────────────
// Coexists with labApi.generateReport — both can be used; viewers prefer
// structured per-analyte rows when present and fall back to the blob.
export const resultApi = {
    listForOrder: async (labOrderId) => {
        const { data } = await api.get(`/api/lab/${labOrderId}/results`);
        return data;
    },
    create: async (labOrderId, payload) => {
        const { data } = await api.post(`/api/lab/${labOrderId}/results`, payload);
        return data;
    },
    createBulk: async (labOrderId, results) => {
        const { data } = await api.post(`/api/lab/${labOrderId}/results/bulk`, { results });
        return data;
    },
    get: async (id) => {
        const { data } = await api.get(`/api/results/${id}`);
        return data;
    },
    verify: async (id, payload = {}) => {
        const { data } = await api.patch(`/api/results/${id}/verify`, payload);
        return data;
    },
    authorise: async (id, payload = {}) => {
        const { data } = await api.patch(`/api/results/${id}/authorise`, payload);
        return data;
    },
    amend: async (id, payload) => {
        const { data } = await api.post(`/api/results/${id}/amend`, payload);
        return data;
    },
    cancel: async (id, reason) => {
        const { data } = await api.patch(`/api/results/${id}/cancel`, { reason });
        return data;
    },
    panicCall: async (id, payload) => {
        const { data } = await api.patch(`/api/results/${id}/panic-call`, payload);
        return data;
    },
};

// ── Report templates (Phase 5 — per-hospital branding for PDF reports) ─
export const reportTemplateApi = {
    list: async () => {
        const { data } = await api.get("/api/report-templates");
        return data;
    },
    create: async (payload) => {
        const { data } = await api.post("/api/report-templates", payload);
        return data;
    },
    update: async (id, payload) => {
        const { data } = await api.put(`/api/report-templates/${id}`, payload);
        return data;
    },
    delete: async (id) => {
        await api.delete(`/api/report-templates/${id}`);
    },
};

// ── Report PDFs (Phase 5 — sign, render, version, cumulative) ──────────
// PDF bytes are rendered on demand by the backend so every download
// reflects current result state including amendments.
export const reportPdfApi = {
    sign: async (labOrderId, { cumulative = false } = {}) => {
        const { data } = await api.post(
            `/api/lab/${labOrderId}/report/sign`,
            null,
            { params: { cumulative } }
        );
        return data;
    },
    versions: async (labOrderId) => {
        const { data } = await api.get(`/api/lab/${labOrderId}/report/versions`);
        return data;
    },
    cumulative: async (labOrderId) => {
        const { data } = await api.get(`/api/lab/${labOrderId}/cumulative`);
        return data;
    },
    /** Direct browser-href to the latest signed PDF (bypasses axios so the
     *  browser handles the application/pdf inline render). */
    latestPdfUrl: (labOrderId) =>
        `${API_BASE_URL}/api/lab/${labOrderId}/report.pdf`,
    versionPdfUrl: (pdfId) =>
        `${API_BASE_URL}/api/report-pdf/${pdfId}.pdf`,
    revoke: async (pdfId, reason) => {
        const { data } = await api.post(`/api/report-pdf/${pdfId}/revoke`, { reason });
        return data;
    },
};

// ── Public report verify (Phase 5 — no auth required) ──────────────────
// Used by the public verification page rendered after a QR scan.
// Uses the top-level axios import (NOT the `api` instance) so the dev
// mock-auth Bearer header isn't attached — the verify endpoint is public
// and we don't want to imply caller identity.
export const reportVerifyApi = {
    verify: async (token) => {
        const { data } = await axios.get(
            `${API_BASE_URL}/api/report-verify/${encodeURIComponent(token)}`
        );
        return data;
    },
};

// ── Collection console (Phase 6 — front-of-house) ─────────────────────
// Patient-grouped pending orders with a pre-computed tube plan; one
// bulk-collect call atomically marks every named order collected and
// materialises the tubes (one specimen per (order, tube) pair) so the
// chain of custody starts populating immediately.
export const collectionApi = {
    queue: async () => {
        const { data } = await api.get("/api/collection/queue");
        return data;
    },
    patientPlan: async (patientId) => {
        const { data } = await api.get(`/api/collection/queue/${patientId}`);
        return data;
    },
    bulkCollect: async (payload) => {
        const { data } = await api.post("/api/collection/bulk-collect", payload);
        return data;
    },
    stats: async () => {
        const { data } = await api.get("/api/collection/stats");
        return data;
    },
    /**
     * Date-windowed log of every specimen collected. Drives the Collections
     * page (post-collection audit log). `from` / `to` are ISO yyyy-MM-dd
     * strings — backend defaults both to today if omitted.
     */
    log: async ({ from, to } = {}) => {
        const params = {};
        if (from) params.from = from;
        if (to) params.to = to;
        const { data } = await api.get("/api/collection/log", { params });
        return data;
    },
};

// ── Audit trail (Phase 0 — read-only viewer) ───────────────────────────
export const auditApi = {
    list: async ({ entityType, entityId, from, to, page = 0, size = 50 } = {}) => {
        const params = { page, size };
        if (entityType) params.entityType = entityType;
        if (entityId)   params.entityId   = entityId;
        if (from)       params.from       = from;
        if (to)         params.to         = to;
        const { data } = await api.get("/api/audit-log", { params });
        return data;
    },
};

export default api;
