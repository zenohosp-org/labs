import { useEffect } from "react";
import { driver } from "driver.js";
import "driver.js/dist/driver.css";

export const startProductTour = () => {
    // Force open the Pathology accordion so Collection / Lab Queue / Reports are visible.
    // Read aria-expanded rather than probing for the child links: the accordion
    // keeps its panel mounted (hidden via visibility) so the height can animate,
    // so the links are in the DOM whether the group is open or not.
    const pathologyBtn = document.getElementById('tour-pathology-accordion');
    if (pathologyBtn && pathologyBtn.getAttribute('aria-expanded') !== 'true') {
        pathologyBtn.click();
    }

    // Wait out the accordion's spring (420ms) plus the child stagger before
    // driver.js measures anything — highlighting mid-animation lands the
    // spotlight in the wrong place.
    setTimeout(() => {
        const steps = [
            {
                element: '.zu-topnav-title',
                popover: {
                    title: 'Welcome to ZenoLabs',
                    description: "Welcome to the Laboratory Information System! Let's take a quick look around.",
                    side: "bottom",
                    align: 'start'
                }
            }
        ];

        const addSidebarStep = (tourId, title, description) => {
            if (document.querySelector(`[data-tour="${tourId}"]`)) {
                steps.push({
                    element: `[data-tour="${tourId}"]`,
                    popover: {
                        title,
                        description,
                        side: "right",
                        align: 'start'
                    },
                    onHighlightStarted: () => {
                        const el = document.querySelector(`[data-tour="${tourId}"]`);
                        if (el) {
                            el.scrollIntoView({ block: 'center', behavior: 'instant' });
                        }
                    }
                });
            }
        };

        addSidebarStep("dashboard", "Dashboard", "Get a quick overview of your lab's activity and pending work here.");
        addSidebarStep("collection", "Collection", "Log and track sample collection for pathology orders here.");
        addSidebarStep("lab-queue", "Lab Queue", "See tests awaiting processing and move them through the lab workflow.");
        addSidebarStep("reports", "Reports", "Review and release finalized pathology reports from here.");

        steps.push({
            element: '.zu-topnav-user',
            popover: {
                title: 'Profile & Settings',
                description: 'Manage your profile, preferences, and sign out from here.',
                side: "bottom",
                align: 'end'
            }
        });

        const driverObj = driver({
            showProgress: true,
            allowClose: true,
            popoverClass: 'zu-driver-popover',
            onDestroyed: () => {
                localStorage.setItem("labs_tour_completed", "true");
            },
            steps: steps
        });

        driverObj.drive();
    }, 650);
};

export default function ProductTour() {
    useEffect(() => {
        // Small delay to ensure the DOM is fully painted
        const timer = setTimeout(() => {
            const isCompleted = localStorage.getItem("labs_tour_completed");
            if (isCompleted === "true") return;
            startProductTour();
        }, 500);

        return () => clearTimeout(timer);
    }, []);

    return null;
}
