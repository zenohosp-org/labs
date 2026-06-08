import { useEffect, useState } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
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
} from "lucide-react";

const ERROR_MESSAGES = {
    session_validation_failed: "Session validation failed. Please sign in again.",
    sso_failed: "Wrong email or password. Please try again.",
    user_not_found: "Your account is not registered in HMS. Contact your admin.",
    account_inactive: "Your account is inactive. Contact your admin.",
    no_labs_access: "Lab access is not enabled for your account. Ask your admin to grant the LABS module.",
    no_hms_access: "Lab access is not enabled for your account.",
    role_missing: "Your account has no role assigned. Contact your admin.",
    internal_server_error: "Something went wrong on our side. Please try again.",
};

const SLIDES = [
    {
        title: "Specimen workflow, one screen",
        sub: "Collection, processing and reporting — every sample, fully tracked.",
        Hero: TestTube2,
        side: [FlaskConical, Microscope, ClipboardList],
        tone: "is-blue",
    },
    {
        title: "Reports patients (and clinicians) trust",
        sub: "Standardised templates, signed off by your pathologists.",
        Hero: FileText,
        side: [Stethoscope, HeartPulse, Activity],
        tone: "is-violet",
    },
    {
        title: "Auto-billed at report generation",
        sub: "Walk-ins get a standalone invoice; admitted patients roll into the IPD bill.",
        Hero: ReceiptText,
        side: [BarChart2, ClipboardList, Activity],
        tone: "is-amber",
    },
    {
        title: "Microbiology to molecular — covered",
        sub: "Configure your test catalogue once; everything else stays in sync.",
        Hero: Microscope,
        side: [FlaskConical, TestTube2, Stethoscope],
        tone: "is-green",
    },
];

const SLIDE_INTERVAL_MS = 4500;

export default function Login() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const [slide, setSlide] = useState(0);

    useEffect(() => {
        if (import.meta.env.VITE_DEV_MOCK_AUTH === "true") {
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
        <div className="hms-login">
            <div className="hms-login__form-pane">
                <div className="hms-login__form-inner">
                    <div className="hms-login__brand">
                        <div className="hms-login__brand-icon">
                            <FlaskConical className="w-5 h-5" />
                        </div>
                        <div>
                            <h1 className="hms-login__brand-title">ZenoLabs</h1>
                        </div>
                    </div>

                    <div className="hms-login__heading">
                        <h2>Sign in</h2>
                        <p>to access the Laboratory Information System</p>
                    </div>

                    {loggedOut && (
                        <div className="hms-login__alert is-info">
                            You have been signed out successfully.
                        </div>
                    )}
                    {errorMessage && (
                        <div className="hms-login__alert is-danger">{errorMessage}</div>
                    )}

                    <button
                        type="button"
                        onClick={() => {
                            window.location.href = "/oauth2/authorization/directory";
                        }}
                        className="hms-login__sso-btn"
                    >
                        <Activity className="w-5 h-5" />
                        Sign in with ZenoHosp Directory
                    </button>

                    <p className="hms-login__terms">
                        Don&apos;t have a ZenoHosp account?{" "}
                        <span className="hms-login__terms-link">Contact your admin</span>
                    </p>
                </div>
            </div>

            <div className="hms-login__visual">
                <div className="hms-login__carousel">
                    {SLIDES.map((s, i) => {
                        const Hero = s.Hero;
                        return (
                            <div
                                key={i}
                                className={`hms-login__slide ${s.tone}${
                                    i === slide ? " is-active" : ""
                                }`}
                                aria-hidden={i !== slide}
                            >
                                <div className="hms-login__slide-stage">
                                    <div className="hms-login__slide-hero">
                                        <Hero size={56} strokeWidth={1.6} />
                                    </div>
                                    {s.side.map((Icon, idx) => (
                                        <div
                                            key={idx}
                                            className={`hms-login__slide-orb is-orb-${idx + 1}`}
                                        >
                                            <Icon size={18} strokeWidth={1.8} />
                                        </div>
                                    ))}
                                </div>
                                <div className="hms-login__slide-caption">
                                    <h3>{s.title}</h3>
                                    <p>{s.sub}</p>
                                </div>
                            </div>
                        );
                    })}
                </div>
                <div className="hms-login__dots">
                    {SLIDES.map((_, i) => (
                        <button
                            key={i}
                            type="button"
                            onClick={() => setSlide(i)}
                            className={`hms-login__dot${i === slide ? " is-active" : ""}`}
                            aria-label={`Slide ${i + 1}`}
                        />
                    ))}
                </div>
            </div>
        </div>
    );
}
