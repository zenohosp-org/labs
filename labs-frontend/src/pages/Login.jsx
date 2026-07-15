import { useEffect, useState } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import { API_BASE_URL } from "@/api/labsClient";
import { DEV_MOCK_AUTH } from "@/utils/devMockAuth";
import {
    FlaskConical,
    Microscope,
    TestTube2,
    Activity,
    FileText,
    BarChart2,
    ClipboardList,
    HeartPulse,
    Stethoscope,
    ReceiptText,
    Beaker,
    Droplet,
} from "lucide-react";

const ERROR_MESSAGES = {
    session_validation_failed: "Session validation failed. Please sign in again.",
    sso_failed: "Wrong email or password. Please try again.",
    user_not_found: "Your account is not registered in HMS. Contact your admin.",
    account_inactive: "Your account is inactive. Contact your admin.",
    no_labs_access:
        "Lab access is not enabled for your account. Ask your admin to grant the LABS module.",
    no_hms_access: "Lab access is not enabled for your account.",
    role_missing: "Your account has no role assigned. Contact your admin.",
    internal_server_error: "Something went wrong on our side. Please try again.",
};

// Right-pane carousel slides — laboratory domain.
// Same SLIDES shape as HMS (title, sub, Hero, side[3], tone), only the
// copy + icons change so the visual feels native to a lab workflow.
const SLIDES = [
    {
        title: "Specimen workflow, one screen",
        sub: "Collection, processing and reporting — every sample tracked from draw to result.",
        Hero: TestTube2,
        side: [FlaskConical, Microscope, ClipboardList],
        tone: "is-blue",
    },
    {
        title: "Reports clinicians trust",
        sub: "Standardised templates, pathologist sign-off, instant patient delivery.",
        Hero: FileText,
        side: [Stethoscope, HeartPulse, Activity],
        tone: "is-violet",
    },
    {
        title: "Auto-billed at report generation",
        sub: "OPD walk-ins get a standalone invoice; admitted patients roll into the IPD bill.",
        Hero: ReceiptText,
        side: [BarChart2, ClipboardList, Activity],
        tone: "is-amber",
    },
    {
        title: "Microbiology to molecular — covered",
        sub: "Configure your lab services catalogue once; every department stays in sync.",
        Hero: Microscope,
        side: [Beaker, Droplet, FlaskConical],
        tone: "is-green",
    },
];

const SLIDE_INTERVAL_MS = 4500;

export default function Login() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const [slide, setSlide] = useState(0);

    useEffect(() => {
        if (DEV_MOCK_AUTH) {
            navigate("/", { replace: true });
        }
    }, [navigate]);

    useEffect(() => {
        const id = setInterval(
            () => setSlide((s) => (s + 1) % SLIDES.length),
            SLIDE_INTERVAL_MS
        );
        return () => clearInterval(id);
    }, []);

    const loggedOut = searchParams.get("logged_out");
    const error = searchParams.get("error");
    const errorMessage = error
        ? ERROR_MESSAGES[error] ?? "Login failed. Please try again."
        : null;

    return (
        <div className="labs-login">
            <div className="labs-login__form-pane">
                <div className="labs-login__form-inner">
                    <div className="labs-login__brand">
                        <div className="labs-login__brand-icon">
                            <FlaskConical className="w-5 h-5" />
                        </div>
                        <div>
                            <h1 className="labs-login__brand-title">ZenoHosp</h1>
                            <p className="labs-login__brand-sub">ZenoLabs</p>
                        </div>
                    </div>

                    <div className="labs-login__heading">
                        <h2>Sign in</h2>
                        <p>to access the Laboratory Information System</p>
                    </div>

                    {loggedOut && (
                        <div className="labs-login__alert is-info">
                            You have been signed out successfully.
                        </div>
                    )}
                    {errorMessage && (
                        <div className="labs-login__alert is-danger">{errorMessage}</div>
                    )}

                    <button
                        type="button"
                        onClick={() => {
                            // Must go to the labs-backend host (Spring Security owns
                            // /oauth2/authorization/*). A relative path would dead-end
                            // at the Vercel SPA rewrite in production.
                            window.location.href = `${API_BASE_URL}/oauth2/authorization/directory`;
                        }}
                        className="labs-login__sso-btn"
                    >
                        <Activity className="w-5 h-5" />
                        Sign in with ZenoHosp Directory
                    </button>

                    <p className="labs-login__terms">
                        Don&apos;t have a ZenoHosp account?{" "}
                        <span className="labs-login__terms-link">Contact your admin</span>
                    </p>
                </div>
            </div>

            <div className="labs-login__visual">
                <div className="labs-login__carousel">
                    {SLIDES.map((s, i) => {
                        const Hero = s.Hero;
                        return (
                            <div
                                key={i}
                                className={`labs-login__slide ${s.tone}${
                                    i === slide ? " is-active" : ""
                                }`}
                                aria-hidden={i !== slide}
                            >
                                <div className="labs-login__slide-stage">
                                    <div className="labs-login__slide-hero">
                                        <Hero size={56} strokeWidth={1.6} />
                                    </div>
                                    {s.side.map((Icon, idx) => (
                                        <div
                                            key={idx}
                                            className={`labs-login__slide-orb is-orb-${idx + 1}`}
                                        >
                                            <Icon size={18} strokeWidth={1.8} />
                                        </div>
                                    ))}
                                </div>
                                <div className="labs-login__slide-caption">
                                    <h3>{s.title}</h3>
                                    <p>{s.sub}</p>
                                </div>
                            </div>
                        );
                    })}
                </div>
                <div className="labs-login__dots">
                    {SLIDES.map((_, i) => (
                        <button
                            key={i}
                            type="button"
                            onClick={() => setSlide(i)}
                            className={`labs-login__dot${i === slide ? " is-active" : ""}`}
                            aria-label={`Slide ${i + 1}`}
                        />
                    ))}
                </div>
            </div>
        </div>
    );
}
