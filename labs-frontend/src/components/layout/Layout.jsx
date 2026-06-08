import { useState } from "react";
import { Outlet } from "react-router-dom";
import Sidebar from "./Sidebar";
import Header from "./Header";

function Layout() {
    const [sidebarOpen, setSidebarOpen] = useState(true);
    return (
        <div className="hms-app-shell">
            <div className="no-print">
                <Sidebar isOpen={sidebarOpen} />
            </div>
            <div className="hms-app-shell__main">
                <div className="no-print">
                    <Header onMenuClick={() => setSidebarOpen((p) => !p)} />
                </div>
                <main className="hms-app-shell__content">
                    <Outlet />
                </main>
            </div>
        </div>
    );
}

export { Layout as default };
