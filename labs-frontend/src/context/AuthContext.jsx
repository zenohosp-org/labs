import { createContext, useContext, useState, useCallback, useEffect } from "react";
import { getMe, logout as apiLogout } from "@/api/labsClient";

const AuthContext = createContext(null);
const LOGOUT_FLAG_KEY = "labs_logout_in_progress";

export function AuthProvider({ children }) {
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        if (import.meta.env.VITE_DEV_MOCK_AUTH === "true") {
            setUser({
                userId: import.meta.env.VITE_MOCK_USER_ID || "1",
                email: import.meta.env.VITE_MOCK_USER_EMAIL || "dev@zenohosp.com",
                firstName: import.meta.env.VITE_MOCK_USER_FIRSTNAME || "Dev",
                lastName: import.meta.env.VITE_MOCK_USER_LASTNAME || "User",
                role: import.meta.env.VITE_MOCK_USER_ROLE || "super_admin",
                roleDisplay: import.meta.env.VITE_MOCK_USER_ROLE_DISPLAY || "Super Admin",
                hospitalId: import.meta.env.VITE_MOCK_HOSPITAL_ID || "00000000-0000-0000-0000-000000000001",
                hospitalName: import.meta.env.VITE_MOCK_HOSPITAL_NAME || "ZenoLabs",
                modules: [],
            });
            setLoading(false);
            return;
        }

        const logoutInProgress = localStorage.getItem(LOGOUT_FLAG_KEY);
        if (logoutInProgress) {
            sessionStorage.removeItem("labs_user");
            setUser(null);
            setLoading(false);
            localStorage.removeItem(LOGOUT_FLAG_KEY);
            return;
        }

        // 10s hard timeout — if the backend is unreachable (Render cold start
        // hung, container dead, CDN black-holed), we land on /login instead
        // of stalling on the "Loading…" screen forever.
        const timeout = new Promise((_, reject) =>
            setTimeout(() => reject(new Error("auth-timeout")), 10_000)
        );
        Promise.race([getMe(), timeout])
            .then((res) => {
                const userData = res.data?.data || res.data;
                sessionStorage.setItem("labs_user", JSON.stringify(userData));
                setUser(userData);
            })
            .catch(() => {
                sessionStorage.removeItem("labs_user");
                setUser(null);
            })
            .finally(() => setLoading(false));
    }, []);

    useEffect(() => {
        const verifyOnFocus = async () => {
            if (!user) return;
            if (import.meta.env.VITE_DEV_MOCK_AUTH === "true") return;
            try {
                await getMe();
            } catch (_err) {
                sessionStorage.removeItem("labs_user");
                setUser(null);
                window.location.href = "/login?logged_out=1";
            }
        };
        window.addEventListener("focus", verifyOnFocus);
        return () => window.removeEventListener("focus", verifyOnFocus);
    }, [user]);

    useEffect(() => {
        const handleStorageChange = (event) => {
            if (event.key === "sso-logout") {
                sessionStorage.removeItem("labs_user");
                setUser(null);
                window.location.href = "/login?logged_out=1";
            }
        };
        const handleCustomLogoutEvent = () => {
            sessionStorage.removeItem("labs_user");
            setUser(null);
            window.location.href = "/login?logged_out=1";
        };
        window.addEventListener("storage", handleStorageChange);
        window.addEventListener("sso-logout", handleCustomLogoutEvent);
        return () => {
            window.removeEventListener("storage", handleStorageChange);
            window.removeEventListener("sso-logout", handleCustomLogoutEvent);
        };
    }, []);

    const logout = useCallback(async () => {
        localStorage.setItem(LOGOUT_FLAG_KEY, "1");
        sessionStorage.removeItem("labs_user");
        setUser(null);
        try {
            localStorage.setItem("sso-logout", `${Date.now()}`);
        } catch (_e) {
            // Storage broadcast is best-effort.
        }
        try {
            await apiLogout();
        } catch (_e) {
            // Best-effort cookie wipe; server may already be unreachable.
        }
        window.location.href = "/login?logged_out=1";
    }, []);

    const value = {
        user,
        loading,
        logout,
        isAuthenticated: !!user,
    };

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export const useAuth = () => useContext(AuthContext);
