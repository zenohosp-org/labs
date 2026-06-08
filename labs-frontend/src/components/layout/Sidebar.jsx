import { useState } from "react";
import { NavLink, useLocation } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";
import {
    Home,
    Activity,
    FlaskConical,
    FileText,
    ChevronDown,
    BookOpen,
    BarChart2,
    Boxes,
    LayoutGrid,
} from "lucide-react";

const DASHBOARD_LINK = { label: "Dashboard", to: "/labs/dashboard", icon: Home };

const LAB_LINKS = [
    { label: "Specimen Queue", to: "/labs/queue", icon: FlaskConical },
    { label: "Reports", to: "/labs/reports", icon: FileText },
];

const EXTERNAL_APPS = [
    { label: "Hospital (HMS)", href: "https://hms.zenohosp.com", icon: Activity },
    { label: "Finance", href: "https://finance.zenohosp.com", icon: BarChart2 },
    { label: "Inventory", href: "https://inventory.zenohosp.com", icon: Boxes },
    { label: "Directory", href: "https://directory.zenohosp.com", icon: BookOpen },
    { label: "Assets", href: "https://asset.zenohosp.com", icon: LayoutGrid },
];

function Sidebar({ isOpen }) {
    const { user } = useAuth();
    const location = useLocation();
    const labActive = location.pathname.startsWith("/labs/queue") || location.pathname.startsWith("/labs/reports");
    const [labOpen, setLabOpen] = useState(() => labActive);

    const renderLink = (link, indent = false) => {
        const Icon = link.icon;
        const baseCls = `hms-sidebar__link${indent ? " is-indent" : ""}${
            isOpen ? "" : " is-icon-only"
        }`;
        return isOpen ? (
            <NavLink
                key={link.to}
                to={link.to}
                end
                className={({ isActive }) => `${baseCls}${isActive ? " is-active" : ""}`}
            >
                {!indent && <Icon className="hms-sidebar__link-icon" />}
                <span className="hms-sidebar__link-label">{link.label}</span>
            </NavLink>
        ) : (
            <NavLink
                key={link.to}
                to={link.to}
                end
                title={link.label}
                className={({ isActive }) => `${baseCls}${isActive ? " is-active" : ""}`}
            >
                <Icon className="hms-sidebar__link-icon" />
            </NavLink>
        );
    };

    const renderExternalApp = (app) => {
        const Icon = app.icon;
        const baseCls = `hms-sidebar__ext${isOpen ? "" : " is-icon-only"}`;
        return isOpen ? (
            <a
                key={app.href}
                href={app.href}
                target="_blank"
                rel="noopener noreferrer"
                className={baseCls}
            >
                <Icon className="hms-sidebar__link-icon" />
                <span className="hms-sidebar__link-label">{app.label}</span>
            </a>
        ) : (
            <a
                key={app.href}
                href={app.href}
                target="_blank"
                rel="noopener noreferrer"
                title={app.label}
                className={baseCls}
            >
                <Icon className="hms-sidebar__link-icon" />
            </a>
        );
    };

    const renderAccordionSection = (links, label, AccIcon, open, setOpen, active) => {
        if (!isOpen) return links.map((link) => renderLink(link));
        return (
            <div>
                <button
                    onClick={() => setOpen((o) => !o)}
                    className={`hms-sidebar__acc-btn${active ? " is-active" : ""}`}
                >
                    <AccIcon className="hms-sidebar__link-icon" />
                    <span className="hms-sidebar__link-label">{label}</span>
                    <ChevronDown
                        size={15}
                        className={`hms-sidebar__acc-chevron${open ? " is-open" : ""}`}
                    />
                </button>
                {open && (
                    <div className="hms-sidebar__acc-body">
                        {links.map((link) => renderLink(link, true))}
                    </div>
                )}
            </div>
        );
    };

    return (
        <aside className={`hms-sidebar${isOpen ? "" : " is-collapsed"}`}>
            <div className="hms-sidebar__logo">
                <div className="hms-sidebar__logo-icon">
                    <FlaskConical className="w-4 h-4" />
                </div>
                {isOpen && (
                    <div className="hms-sidebar__brand">
                        <p className="hms-sidebar__brand-name">ZenoLabs</p>
                        <p className="hms-sidebar__brand-sub">{user?.hospitalName || "Laboratory"}</p>
                    </div>
                )}
            </div>

            <nav className="hms-sidebar__nav">
                {isOpen && <div className="hms-sidebar__section-label">Main Menu</div>}
                {renderLink(DASHBOARD_LINK)}

                {isOpen && <div className="hms-sidebar__section-label is-spaced">Laboratory</div>}
                {renderAccordionSection(LAB_LINKS, "Lab Orders", FlaskConical, labOpen, setLabOpen, labActive)}
            </nav>

            <div className="hms-sidebar__footer">
                {isOpen && <div className="hms-sidebar__section-label">Other Apps</div>}
                {EXTERNAL_APPS.map(renderExternalApp)}
            </div>
        </aside>
    );
}

export { Sidebar as default };
