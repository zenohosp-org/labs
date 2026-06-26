import { useMemo } from "react";

/**
 * Faithful HTML mirror of the Thymeleaf lab_report.html template — pulls
 * the same structural choices (accent strip, two-column header with logo,
 * patient grid, sample results table, signatory block with QR placeholder,
 * footer) so what the admin sees while editing the template matches what
 * the PDF renderer actually produces.
 *
 * Smooth movement of objects:
 *   - accent strip + headings transition colour via CSS (250 ms ease)
 *   - logo + signature image fade-in when their URL is set
 *   - text content updates instantly (text transitions look glitchy)
 *
 * Sample patient + results are hard-coded so the preview shows realistic
 * filling without depending on any live order.
 */
export default function ReportTemplatePreview({ template, scale = 0.62 }) {
    const t = template || {};
    const accent = t.accentColor || "#14b8a6";

    const safeHeader = useMemo(() => t.headerHtml || "", [t.headerHtml]);
    const safeFooter = useMemo(() => t.footerHtml || "", [t.footerHtml]);

    return (
        <div className="rtp-stage" data-scale={scale}>
            <style>{css(scale)}</style>

            <div className="rtp-paper" key={accent}>
                {/* Accent strip */}
                <div className="rtp-accent" style={{ background: accent }} />

                {/* Header */}
                <div className="rtp-hdr">
                    <div className="rtp-hdr-left">
                        {t.logoUrl ? (
                            <img
                                className="rtp-logo"
                                src={t.logoUrl}
                                alt="logo"
                                onError={(e) => { e.currentTarget.style.display = "none"; }}
                            />
                        ) : (
                            <div className="rtp-logo-ph">LOGO</div>
                        )}
                        <div className="rtp-hospital">{t.hospitalName || "Hospital Name"}</div>
                        {safeHeader && (
                            <div
                                className="rtp-header-html"
                                dangerouslySetInnerHTML={{ __html: safeHeader }}
                            />
                        )}
                    </div>
                    <div className="rtp-hdr-right">
                        <div className="rtp-report-title" style={{ color: accent }}>
                            LABORATORY REPORT
                        </div>
                        <div>Accession: <span className="rtp-strong">1001-ACC-2026-000123</span></div>
                        <div>Reported: <span className="rtp-strong">26 Jun 2026 14:32</span></div>
                    </div>
                </div>

                {/* Patient grid */}
                <table className="rtp-patient">
                    <tbody>
                        <tr>
                            <td className="k">Patient</td><td className="v">V. Kumari</td>
                            <td className="k">UHID</td><td className="v">UHID-001234</td>
                        </tr>
                        <tr>
                            <td className="k">Age / Sex</td><td className="v">32 yrs · Female</td>
                            <td className="k">Referring Dr.</td><td className="v">Dr. Sharma</td>
                        </tr>
                        <tr>
                            <td className="k">Collected</td><td className="v">26 Jun 2026 09:15</td>
                            <td className="k">Order ID</td><td className="v">#101</td>
                        </tr>
                    </tbody>
                </table>

                {/* Section + results sample */}
                <h2 className="rtp-section" style={{ borderColor: accent + "55" }}>
                    Complete Blood Count
                </h2>
                <table className="rtp-results">
                    <thead>
                        <tr>
                            <th>Analyte</th><th>Value</th><th>Unit</th>
                            <th>Flag</th><th>Reference</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td><strong>Hemoglobin</strong><div className="rtp-loinc">LOINC 718-7</div></td>
                            <td className="num">10.2</td><td>g/dL</td>
                            <td><span className="flag-L">L</span></td>
                            <td>12.0 – 15.5 g/dL</td>
                        </tr>
                        <tr>
                            <td><strong>WBC Count</strong><div className="rtp-loinc">LOINC 6690-2</div></td>
                            <td className="num">8.4</td><td>10^3/µL</td>
                            <td><span className="flag-N">N</span></td>
                            <td>4.0 – 11.0</td>
                        </tr>
                        <tr>
                            <td><strong>Platelets</strong><div className="rtp-loinc">LOINC 777-3</div></td>
                            <td className="num">245</td><td>10^3/µL</td>
                            <td><span className="flag-N">N</span></td>
                            <td>150 – 450</td>
                        </tr>
                    </tbody>
                </table>

                {/* Footer block + signatory */}
                <div className="rtp-sig-block">
                    <div className="rtp-qr">
                        <div className="rtp-qr-tile">QR</div>
                        <div className="rtp-qr-caption">Verify online</div>
                    </div>
                    <div className="rtp-sig">
                        {t.signatureImageUrl ? (
                            <img
                                className="rtp-sig-img"
                                src={t.signatureImageUrl}
                                alt="signature"
                                onError={(e) => { e.currentTarget.style.display = "none"; }}
                            />
                        ) : (
                            <div className="rtp-sig-ph">[signature image]</div>
                        )}
                        <div className="rtp-sig-name">
                            {t.signatoryName || "Authorised Signatory"}
                        </div>
                        {t.signatoryQualification && (
                            <div className="rtp-sig-small">{t.signatoryQualification}</div>
                        )}
                        {t.signatoryRegistration && (
                            <div className="rtp-sig-small">Reg. No. {t.signatoryRegistration}</div>
                        )}
                    </div>
                </div>

                {safeFooter && (
                    <div
                        className="rtp-footer-html"
                        style={{ borderColor: accent + "33" }}
                        dangerouslySetInnerHTML={{ __html: safeFooter }}
                    />
                )}
                <div className="rtp-end">*** End of Report ***</div>
            </div>
        </div>
    );
}

// Scale via transform: scale(X). Keeps a "real" A4 layout inside but shrinks
// to fit the preview pane without changing any font sizes / layout breakpoints.
function css(scale) {
    return `
.rtp-stage {
    width: 100%; height: 100%;
    overflow: auto;
    background:
        linear-gradient(135deg, #f8fafc 25%, transparent 25%) -10px 0/20px 20px,
        linear-gradient(225deg, #f8fafc 25%, transparent 25%) -10px 0/20px 20px,
        linear-gradient(315deg, #f8fafc 25%, transparent 25%) 0px 0/20px 20px,
        linear-gradient(45deg,  #f8fafc 25%, transparent 25%) 0px 0/20px 20px,
        #ffffff;
    padding: 20px;
    display: flex; justify-content: center;
}
.rtp-paper {
    width: 595px;   /* A4 in pt at 72dpi */
    min-height: 842px;
    background: #fff;
    box-shadow: 0 10px 40px -10px rgba(0,0,0,0.15);
    padding: 26px 24px 32px;
    transform-origin: top center;
    transform: scale(${scale});
    font-family: "Helvetica","Arial",sans-serif;
    color: #111;
    font-size: 10pt;
    transition: box-shadow 250ms ease;
}
.rtp-paper:hover { box-shadow: 0 14px 50px -12px rgba(0,0,0,0.18); }
.rtp-accent {
    height: 4px; margin: -26px -24px 8px;
    transition: background 250ms ease;
}
.rtp-hdr {
    display: table; width: 100%;
}
.rtp-hdr-left  { display: table-cell; vertical-align: middle; width: 60%; }
.rtp-hdr-right { display: table-cell; vertical-align: middle; width: 40%;
                 text-align: right; font-size: 9pt; color: #555; }
.rtp-logo {
    max-height: 40px; max-width: 200px; display: block;
    opacity: 0; animation: rtpFade 280ms ease forwards;
    transition: opacity 200ms ease;
}
.rtp-logo-ph {
    display: inline-block;
    width: 90px; height: 36px;
    border: 1px dashed #cbd5e1;
    color: #94a3b8;
    text-align: center; line-height: 36px;
    font-size: 9pt; letter-spacing: 0.1em;
    border-radius: 4px;
    margin-bottom: 4px;
}
.rtp-hospital { font-size: 14pt; font-weight: bold; color: #111; transition: color 250ms ease; }
.rtp-header-html { margin-top: 4px; font-size: 9pt; color: #444; }
.rtp-report-title { font-weight: bold; transition: color 250ms ease; }
.rtp-strong { font-weight: bold; color: #000; }

.rtp-patient { width: 100%; border-collapse: collapse; margin-top: 10px; margin-bottom: 8px; }
.rtp-patient td { padding: 3px 6px; font-size: 9.5pt; }
.rtp-patient td.k { color: #666; width: 18%; }
.rtp-patient td.v { color: #000; font-weight: bold; }

.rtp-section {
    font-size: 11pt; color: #111; padding-bottom: 3px;
    margin: 14px 0 6px; border-bottom: 1px solid #ccc;
    transition: border-color 250ms ease;
}
.rtp-results { width: 100%; border-collapse: collapse; font-size: 9.5pt; }
.rtp-results th, .rtp-results td {
    border-bottom: 1px solid #eee;
    padding: 5px 6px; vertical-align: top; text-align: left;
}
.rtp-results th { background: #f6f7f8; font-size: 9pt; color: #444;
                  text-transform: uppercase; letter-spacing: 0.03em; }
.rtp-results td.num { text-align: right; font-weight: bold; }
.rtp-loinc { font-size: 8pt; color: #888; }
.flag-N { color: #16a34a; font-weight: bold; }
.flag-L, .flag-H { color: #d97706; font-weight: bold; }

.rtp-sig-block { display: table; width: 100%; margin-top: 28px; }
.rtp-qr { display: table-cell; vertical-align: top; width: 30%; }
.rtp-qr-tile {
    display: inline-block; width: 64px; height: 64px;
    background: repeating-linear-gradient(45deg,#111 0 4px,#fff 4px 8px);
    border: 1px solid #111;
    font-size: 0;
    border-radius: 2px;
}
.rtp-qr-caption { font-size: 7.5pt; color: #666; margin-top: 2px; }
.rtp-sig { display: table-cell; text-align: right; vertical-align: top; }
.rtp-sig-img {
    max-height: 50px; max-width: 220px; display: inline-block;
    opacity: 0; animation: rtpFadeUp 320ms ease forwards;
    transition: opacity 200ms ease, transform 240ms ease;
}
.rtp-sig-ph {
    display: inline-block;
    width: 160px; height: 36px;
    border: 1px dashed #cbd5e1;
    color: #94a3b8; text-align: center; line-height: 36px;
    font-size: 9pt; border-radius: 4px;
}
.rtp-sig-name { font-weight: bold; margin-top: 4px; transition: color 200ms ease; }
.rtp-sig-small { font-size: 8.5pt; color: #555; }

.rtp-footer-html {
    margin-top: 18px; padding-top: 8px;
    border-top: 1px solid #ccc;
    font-size: 8.5pt; color: #555;
    transition: border-color 250ms ease;
}
.rtp-end { font-size: 8.5pt; color: #555; text-align: center; margin-top: 14px; }

@keyframes rtpFade {
    from { opacity: 0; }
    to   { opacity: 1; }
}
@keyframes rtpFadeUp {
    from { opacity: 0; transform: translateY(4px); }
    to   { opacity: 1; transform: translateY(0); }
}
`;
}
