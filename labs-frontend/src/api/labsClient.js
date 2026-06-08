import axios from "axios";

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "";

const api = axios.create({
    baseURL: API_BASE_URL,
    withCredentials: true,
    headers: { "Content-Type": "application/json" },
});

api.interceptors.response.use(
    (response) => response,
    (error) => {
        if (error.response?.status === 401) {
            if (!error.config?.url?.includes("/api/user/me")) {
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
    generateReport: async (id, findings, observation) => {
        const { data } = await api.patch(`/api/radiology/${id}/report`, { findings, observation });
        return data;
    },
};

// Lab-orders endpoints kept for backward compat with the in-flight specimen
// workflow that originally seeded labs. The new radiology flow above is what
// the UI uses now; this will be wound down once migration completes.
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
    getStats: async (hospitalId) => {
        const { data } = await api.get("/api/lab/stats", { params: { hospitalId } });
        return data;
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

// ── Specializations (proxied read-only to HMS) ─────────
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

// ── Admissions ─────────────────────────────────────────
export const admissionApi = {
    byPatient: async (patientId) => {
        const { data } = await api.get(`/api/patients/${patientId}/admissions`);
        return data;
    },
};

export default api;
