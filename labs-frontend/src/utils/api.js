/**
 * Drop-in shim for HMS-copied components that import from "@/utils/api".
 * Re-exports the labs API surface under the same names HMS uses, so files
 * copied verbatim (Services.jsx, PackageManager.jsx, AddServiceModal,
 * ServiceFilters, etc.) work without edits.
 */
export {
    API_BASE_URL,
    getMe,
    logout,
    getLabsDashboard,
    radiologyApi,
    labApi,
    patientApi,
    staffApi,
    hospitalServiceApi,
    specializationApi,
    checkupApi,
    referenceRangeApi,
    admissionApi,
} from "@/api/labsClient";
