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
    }
);

// ── Auth ───────────────────────────────────────────────
export const getMe = () => api.get("/api/user/me");
export const logout = () => api.post("/api/auth/logout");
export const getLabsDashboard = () => api.get("/api/labs/dashboard");

// ── Lab orders (mirrors HMS radiologyApi) ──────────────
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
    create: async (payload) => {
        const { data } = await api.post("/api/lab", payload);
        return data;
    },
    markCollected: async (id) => {
        const { data } = await api.patch(`/api/lab/${id}/collect`);
        return data;
    },
    generateReport: async (id, findings, observation) => {
        const { data } = await api.patch(`/api/lab/${id}/report`, { findings, observation });
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

// ── Staff (technicians for lab assignment) ─────────────
export const staffApi = {
    list: async (hospitalId) => {
        const { data } = await api.get("/api/users", { params: { hospitalId } });
        return data;
    },
};

// ── Hospital services (lab tests catalog) ──────────────
export const hospitalServiceApi = {
    list: async (hospitalId) => {
        const { data } = await api.get("/api/hospital-services", { params: { hospitalId } });
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
