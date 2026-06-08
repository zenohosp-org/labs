import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { Loader2 } from "lucide-react";
import { useAuth } from "@/context/AuthContext";

export default function ProtectedRoute({ children }) {
    const { user, loading } = useAuth();
    const navigate = useNavigate();

    useEffect(() => {
        if (!loading && user === null) {
            navigate("/login", { replace: true });
        }
    }, [user, loading, navigate]);

    if (loading) {
        return (
            <div className="hms-loading-screen">
                <Loader2 className="w-6 h-6 hms-loading-screen__icon" />
                <p className="hms-loading-screen__label">Loading…</p>
            </div>
        );
    }

    return user ? children : null;
}
