import { useAuth } from "@/context/AuthContext";
import { Menu, Bell, LogOut } from "lucide-react";

/**
 * Top navigation. 1:1 visual match with HMS Header.jsx — same {@code zu-topnav}
 * primitive, same burger / title / bell / divider / user-pill / logout layout —
 * so a hospital staff member moving between hms.zenohosp.com and
 * labs.zenohosp.com never sees a frame shift.
 */
function Header({ onMenuClick }) {
    const { user, logout } = useAuth();
    const initials = `${user?.firstName?.[0] ?? ""}${user?.lastName?.[0] ?? ""}`;
    return (
        <header className="zu-topnav">
            {onMenuClick && (
                <button
                    onClick={onMenuClick}
                    className="zu-topnav-burger"
                    aria-label="Toggle sidebar"
                >
                    <Menu className="w-5 h-5" />
                </button>
            )}
            <span className="zu-topnav-title">Laboratory Information System</span>
            <div className="zu-topnav-right">
                <button className="zu-topnav-bell" aria-label="Notifications">
                    <Bell className="w-4 h-4" />
                    <span className="zu-topnav-bell-dot" />
                </button>

                <div className="zu-topnav-divider" />

                <div className="zu-topnav-user">
                    <div className="zu-topnav-user-avatar">{initials || "U"}</div>
                    <div className="zu-topnav-user-text">
                        <span className="zu-topnav-user-name">
                            {user?.firstName} {user?.lastName}
                        </span>
                        <span className="zu-topnav-user-role">
                            {user?.roleDisplay || user?.role}
                        </span>
                    </div>
                    <button
                        onClick={logout}
                        title="Logout"
                        className="zu-topnav-logout"
                    >
                        <LogOut className="w-4 h-4" />
                    </button>
                </div>
            </div>
        </header>
    );
}

export { Header as default };
