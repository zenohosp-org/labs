import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider } from "@/context/AuthContext";
import { NotificationProvider } from "@/context/NotificationContext";
import ProtectedRoute from "@/components/ProtectedRoute";
import Layout from "@/components/layout/Layout";
import Login from "@/pages/Login";
import SsoCallback from "@/pages/SsoCallback";
import Dashboard from "@/pages/Dashboard";
import RadiologyQueue from "@/pages/radiology/RadiologyQueue";
import RadiologyReports from "@/pages/radiology/RadiologyReports";
import RadiologyReportView from "@/pages/radiology/RadiologyReportView";
import PackageManager from "@/pages/checkups/PackageManager";
import Services from "@/pages/services/Services";
import ReferenceRanges from "@/pages/settings/ReferenceRanges";
import LabPackages from "@/pages/packages/LabPackages";
import LabReportView from "@/pages/labs/LabReportView";
import LabQueue from "@/pages/labs/LabQueue";
import LabReports from "@/pages/labs/LabReports";

function App() {
    return (
        <AuthProvider>
            <NotificationProvider>
                <BrowserRouter>
                    <Routes>
                        {/* Public */}
                        <Route path="/login" element={<Login />} />
                        <Route path="/sso/callback" element={<SsoCallback />} />

                        {/* Protected — shell + nested routes */}
                        <Route
                            element={
                                <ProtectedRoute>
                                    <Layout />
                                </ProtectedRoute>
                            }
                        >
                            <Route index element={<Navigate to="/labs/dashboard" replace />} />
                            <Route path="labs" element={<Navigate to="/labs/dashboard" replace />} />
                            <Route path="labs/dashboard" element={<Dashboard />} />

                            {/* Pathology — order-listing + reports view used by lab bench staff.
                                /lab/reports/:id is also deep-linked from HMS Consultation View
                                + IPD Labs tab. */}
                            <Route path="lab" element={<Navigate to="/lab/queue" replace />} />
                            <Route path="lab/queue" element={<LabQueue />} />
                            <Route path="lab/reports" element={<LabReports />} />
                            <Route path="lab/reports/:id" element={<LabReportView />} />

                            {/* Radiology */}
                            <Route path="radiology" element={<Navigate to="/radiology/queue" replace />} />
                            <Route path="radiology/queue" element={<RadiologyQueue />} />
                            <Route path="radiology/reports" element={<RadiologyReports />} />
                            <Route path="radiology/reports/:id" element={<RadiologyReportView />} />

                            {/* Health Checkups */}
                            <Route path="checkups" element={<Navigate to="/checkups/packages" replace />} />
                            <Route path="checkups/packages" element={<PackageManager />} />

                            {/* Lab Packages */}
                            <Route path="packages" element={<Navigate to="/packages/lab" replace />} />
                            <Route path="packages/lab" element={<LabPackages />} />

                            {/* Hospital Services (proxied to HMS) */}
                            <Route path="services" element={<Services />} />

                            {/* Settings */}
                            <Route path="settings" element={<Navigate to="/settings/reference-ranges" replace />} />
                            <Route path="settings/reference-ranges" element={<ReferenceRanges />} />
                        </Route>

                        {/* Fallback */}
                        <Route path="*" element={<Navigate to="/labs/dashboard" replace />} />
                    </Routes>
                </BrowserRouter>
            </NotificationProvider>
        </AuthProvider>
    );
}

export { App as default };
