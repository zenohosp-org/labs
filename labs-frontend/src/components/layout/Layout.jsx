import { useState } from "react";
import { Outlet, useLocation } from "react-router-dom";
import Sidebar from "./Sidebar";
import Header from "./Header";
import ProductTour from "./ProductTour";

/**
 * App shell. Identical to the HMS Layout — mirrors the {@code zu-app-shell}
 * primitives so labs and HMS frame their content the same way (sidebar +
 * top nav, dashboard gets an extra padded surface). The Dashboard heuristic
 * matches the HMS path check verbatim so a future shared shell extraction
 * is a straight copy.
 */
function Layout() {
    const [sidebarOpen, setSidebarOpen] = useState(true);
    const location = useLocation();
    const isDashboard =
        location.pathname === "/labs/dashboard" || location.pathname === "/";

    return (
        <div className="zu-app-shell">
            <ProductTour />
            <div className="no-print">
                <Sidebar isOpen={sidebarOpen} />
            </div>
            <div className="zu-app-shell-main">
                <div className="no-print">
                    <Header onMenuClick={() => setSidebarOpen((p) => !p)} />
                </div>
                <main className={`zu-app-shell-content ${isDashboard ? "zu-dashboard" : ""}`.trim()}>
                    <Outlet />
                </main>
            </div>
        </div>
    );
}

export { Layout as default };
