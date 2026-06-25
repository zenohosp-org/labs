import { useState } from "react";
import { NavLink, useLocation } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";
import {
    Home,
    Activity,
    FlaskConical,
    ScanLine,
    FileText,
    HeartPulse,
    Settings2,
    ChevronDown,
    BookOpen,
    BarChart2,
    Boxes,
    LayoutGrid,
    TestTube,
    History,
} from "lucide-react";

const DASHBOARD_LINK = { label: "Dashboard", to: "/labs/dashboard", icon: Home };

const PATHOLOGY_LINKS = [
    { label: "Lab Queue", to: "/lab/queue", icon: TestTube },
    { label: "Reports", to: "/lab/reports", icon: FileText },
];

const RADIOLOGY_LINKS = [
    { label: "Imaging Queue", to: "/radiology/queue", icon: ScanLine },
    { label: "Reports", to: "/radiology/reports", icon: FileText },
];

const CHECKUP_LINKS = [
    { label: "Packages", to: "/checkups/packages", icon: HeartPulse },
];

const PACKAGE_LINKS = [
    { label: "Lab Packages", to: "/packages/lab", icon: FlaskConical },
];

const ADMIN_LINKS = [
    { label: "Services", to: "/services", icon: Settings2 },
];

const SETTINGS_LINKS = [
    { label: "Reference Ranges", to: "/settings/reference-ranges", icon: Activity },
    { label: "Test Catalog", to: "/settings/test-catalog", icon: FlaskConical },
    { label: "Audit Trail", to: "/settings/audit", icon: History },
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

    const pathActive = location.pathname.startsWith("/lab/");
    const radActive = location.pathname.startsWith("/radiology");
    const checkupActive = location.pathname.startsWith("/checkups");
    const packagesActive = location.pathname.startsWith("/packages");
    const settingsActive = location.pathname.startsWith("/settings");
    const [pathOpen, setPathOpen] = useState(() => pathActive);
    const [radOpen, setRadOpen] = useState(() => radActive);
    const [checkupOpen, setCheckupOpen] = useState(() => checkupActive);
    const [packagesOpen, setPackagesOpen] = useState(() => packagesActive);
    const [settingsOpen, setSettingsOpen] = useState(() => settingsActive);

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
            <a key={app.href} href={app.href} target="_blank" rel="noopener noreferrer" className={baseCls}>
                <Icon className="hms-sidebar__link-icon" />
                <span className="hms-sidebar__link-label">{app.label}</span>
            </a>
        ) : (
            <a key={app.href} href={app.href} target="_blank" rel="noopener noreferrer" title={app.label} className={baseCls}>
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
                    <ChevronDown size={15} className={`hms-sidebar__acc-chevron${open ? " is-open" : ""}`} />
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

                {isOpen && <div className="hms-sidebar__section-label is-spaced">Diagnostics</div>}
                {renderAccordionSection(PATHOLOGY_LINKS, "Pathology", TestTube, pathOpen, setPathOpen, pathActive)}
                {renderAccordionSection(RADIOLOGY_LINKS, "Radiology", ScanLine, radOpen, setRadOpen, radActive)}
                {renderAccordionSection(CHECKUP_LINKS, "Health Checkups", HeartPulse, checkupOpen, setCheckupOpen, checkupActive)}
                {renderAccordionSection(PACKAGE_LINKS, "Packages", FlaskConical, packagesOpen, setPackagesOpen, packagesActive)}

                {isOpen && <div className="hms-sidebar__section-label is-spaced">Admin</div>}
                {ADMIN_LINKS.map((link) => renderLink(link))}
                {renderAccordionSection(SETTINGS_LINKS, "Settings", Settings2, settingsOpen, setSettingsOpen, settingsActive)}
            </nav>

            <div className="hms-sidebar__footer">
                {isOpen && <div className="hms-sidebar__section-label">Other Apps</div>}
                {EXTERNAL_APPS.map(renderExternalApp)}
            </div>
        </aside>
    );
}

export { Sidebar as default };
