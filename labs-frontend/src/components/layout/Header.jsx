import { useAuth } from "@/context/AuthContext";
import { Menu, Bell, LogOut } from "lucide-react";

function Header({ onMenuClick }) {
    const { user, logout } = useAuth();
    const initials = `${user?.firstName?.[0] ?? ""}${user?.lastName?.[0] ?? ""}`;
    return (
        <header className="hms-header">
            {onMenuClick && (
                <button
                    onClick={onMenuClick}
                    className="hms-header__burger"
                    aria-label="Toggle sidebar"
                >
                    <Menu className="w-5 h-5" />
                </button>
            )}
            <span className="hms-header__title">Laboratory Information System</span>
            <div className="hms-header__right">
                <button className="hms-header__bell" aria-label="Notifications">
                    <Bell className="w-4 h-4" />
                    <span className="hms-header__bell-dot" />
                </button>

                <div className="hms-header__divider" />

                <div className="hms-header__user">
                    <div className="hms-header__user-avatar">{initials || "U"}</div>
                    <div className="hms-header__user-text">
                        <span className="hms-header__user-name">
                            {user?.firstName} {user?.lastName}
                        </span>
                        <span className="hms-header__user-role">{user?.roleDisplay || user?.role}</span>
                    </div>
                    <button
                        onClick={logout}
                        title="Logout"
                        className="hms-header__logout"
                    >
                        <LogOut className="w-4 h-4" />
                    </button>
                </div>
            </div>
        </header>
    );
}

export { Header as default };
