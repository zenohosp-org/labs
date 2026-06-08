import { ChevronLeft } from "lucide-react";

/**
 * Sticky page header — title, subtitle, optional back button, optional
 * action slot (typically <Button>s). The inner row maxes out at 1600px
 * so the title and actions line up with the page content beneath.
 *
 * Props
 *   title      string | node
 *   subtitle   string | node
 *   onBack     () => void  — when supplied, renders the back button on the left
 *   actions    node — typically right-aligned buttons
 *   className  extra classes appended to the wrapper
 */
export default function PageHeader({ title, subtitle, onBack, actions, className = "" }) {
    return (
        <div className={`hms-page-header ${className}`.trim()}>
            <div className="hms-page-header-inner">
                {onBack && (
                    <button
                        type="button"
                        className="hms-page-back-btn"
                        onClick={onBack}
                        aria-label="Back"
                    >
                        <ChevronLeft size={18} strokeWidth={2.2} />
                    </button>
                )}
                <div className="hms-page-header-titles">
                    {title && <h1 className="hms-page-title">{title}</h1>}
                    {subtitle && <p className="hms-page-subtitle">{subtitle}</p>}
                </div>
                {actions && <div className="hms-page-actions">{actions}</div>}
            </div>
        </div>
    );
}
