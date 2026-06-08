import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";
import { getMe } from "@/api/labsClient";

const ERROR_MESSAGES = {
    sso_failed: "SSO login failed. Please try again.",
    user_not_found: "Your account is not registered in HMS. Contact your admin.",
    account_inactive: "Your account is inactive. Contact your admin.",
    no_labs_access: "Lab access is not enabled for your account.",
    no_hms_access: "Lab access is not enabled for your account.",
    role_missing: "Your account has no role assigned. Contact your admin.",
    internal_server_error: "An internal error occurred. Please try again.",
};

function SsoCallback() {
    const { loading, user } = useAuth();
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const [error, setError] = useState(null);

    useEffect(() => {
        const errorParam = searchParams.get("error");
        if (errorParam) {
            setError(ERROR_MESSAGES[errorParam] ?? "Login failed. Please try again.");
            const t = setTimeout(() => navigate("/login", { replace: true }), 3000);
            return () => clearTimeout(t);
        }

        if (!loading && user) {
            navigate("/labs/dashboard", { replace: true });
            return undefined;
        }

        if (!loading && !user) {
            getMe()
                .then(() => {
                    setTimeout(() => navigate("/labs/dashboard", { replace: true }), 500);
                })
                .catch(() => {
                    setError("Failed to validate your session. The cookie may have expired.");
                    setTimeout(
                        () =>
                            navigate("/login?error=session_validation_failed", {
                                replace: true,
                            }),
                        3000
                    );
                });
        }
        return undefined;
    }, [searchParams, loading, user, navigate]);

    if (error) {
        return (
            <div className="hms-sso">
                <div className="hms-sso__card">
                    <div className="hms-sso__error-icon">
                        <svg
                            className="w-5 h-5"
                            fill="none"
                            viewBox="0 0 24 24"
                            stroke="currentColor"
                        >
                            <path
                                strokeLinecap="round"
                                strokeLinejoin="round"
                                strokeWidth={2}
                                d="M6 18L18 6M6 6l12 12"
                            />
                        </svg>
                    </div>
                    <p className="hms-sso__title is-danger">{error}</p>
                    <p className="hms-sso__desc">Redirecting to login...</p>
                </div>
            </div>
        );
    }

    return (
        <div className="hms-sso">
            <div className="hms-sso__card">
                <div className="hms-sso__spinner" />
                <p className="hms-sso__title">Completing sign-in...</p>
                <p className="hms-sso__desc">Please wait while we set up your session.</p>
            </div>
        </div>
    );
}

export { SsoCallback as default };
