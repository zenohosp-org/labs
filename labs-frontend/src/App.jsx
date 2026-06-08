import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider } from "@/context/AuthContext";
import { NotificationProvider } from "@/context/NotificationContext";
import ProtectedRoute from "@/components/ProtectedRoute";
import Layout from "@/components/layout/Layout";
import Login from "@/pages/Login";
import SsoCallback from "@/pages/SsoCallback";
import Dashboard from "@/pages/Dashboard";
import LabsQueue from "@/pages/labs/LabsQueue";
import LabsReports from "@/pages/labs/LabsReports";
import LabsReportView from "@/pages/labs/LabsReportView";

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
                            <Route path="labs/queue" element={<LabsQueue />} />
                            <Route path="labs/reports" element={<LabsReports />} />
                            <Route path="labs/reports/:id" element={<LabsReportView />} />
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
