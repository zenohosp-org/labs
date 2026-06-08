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

                            {/* Radiology */}
                            <Route path="radiology" element={<Navigate to="/radiology/queue" replace />} />
                            <Route path="radiology/queue" element={<RadiologyQueue />} />
                            <Route path="radiology/reports" element={<RadiologyReports />} />
                            <Route path="radiology/reports/:id" element={<RadiologyReportView />} />

                            {/* Health Checkups */}
                            <Route path="checkups" element={<Navigate to="/checkups/packages" replace />} />
                            <Route path="checkups/packages" element={<PackageManager />} />

                            {/* Hospital Services (proxied to HMS) */}
                            <Route path="services" element={<Services />} />
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
