/**
 * Open a fresh window with one printable label per specimen.
 *
 * Each label = patient name + UHID + accession (if any) + container type
 * + the barcode rendered as a tall CSS bar pattern + the raw alphanumeric
 * barcode below. No external lib — pure CSS so it survives any browser's
 * print preview without a barcode renderer dep.
 *
 * For a real lab we'd swap to ZPL / EPL output sent straight to a Zebra
 * GK420t — that's a Phase 6b enhancement.
 */
export function printBarcodes({ patient, specimens }) {
    if (!specimens || specimens.length === 0) return;
    const w = window.open("", "_blank", "width=540,height=720");
    if (!w) return;

    const stamp = new Date().toLocaleString();
    const rows = specimens
        .map(
            (s) => `
        <div class="label">
            <div class="hdr">
                <div class="patient">${escape(patient.name || "Patient")}</div>
                <div class="meta">${escape(patient.uhid || "—")} · ${escape(stamp)}</div>
            </div>
            <div class="container">
                <span class="tag">${escape(s.containerType || "OTHER")}</span>
                ${s.volumeMl ? `<span class="vol">${escape(String(s.volumeMl))} mL</span>` : ""}
                ${s.accessionNumber ? `<span class="acc">ACC ${escape(s.accessionNumber)}</span>` : ""}
            </div>
            <div class="bar">${barcodeBars(s.barcode || "")}</div>
            <div class="code">${escape(s.barcode || "—")}</div>
        </div>`,
        )
        .join("");

    w.document.write(`
        <!doctype html><html><head><title>Specimen labels</title>
        <style>
            @page { size: 60mm 30mm; margin: 0; }
            html, body { margin: 0; padding: 0; font-family: -apple-system, "Segoe UI", sans-serif; }
            .label {
                box-sizing: border-box;
                width: 60mm; height: 30mm; padding: 2mm 3mm;
                page-break-after: always; border-bottom: 1px dashed #ccc;
                display: flex; flex-direction: column; gap: 1mm;
            }
            .hdr { display: flex; justify-content: space-between; align-items: baseline; }
            .patient { font-weight: 700; font-size: 9pt; }
            .meta { font-size: 6pt; color: #555; }
            .container { display: flex; gap: 4px; align-items: baseline; font-size: 7pt; }
            .tag { background: #14b8a6; color: white; padding: 1px 5px; border-radius: 3px; font-weight: 700; font-size: 6.5pt; }
            .vol { color: #444; }
            .acc { color: #888; margin-left: auto; }
            .bar { height: 10mm; display: flex; align-items: stretch; gap: 0; }
            .bar span { display: inline-block; height: 100%; background: black; }
            .code { text-align: center; font-family: "SF Mono", "Consolas", monospace; font-size: 7pt; letter-spacing: 1px; }
            @media print {
                body { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
                .label { border-bottom: none; }
            }
        </style></head>
        <body onload="window.print(); setTimeout(function(){ window.close(); }, 800);">
            ${rows}
        </body></html>
    `);
    w.document.close();
}

// Render the barcode string as a stripe pattern. NOT scannable by a real
// barcode reader — visual identification only. The scannable value is the
// QR payload printed elsewhere. Phase 6b can swap this for a real Code128
// canvas-rendered barcode.
function barcodeBars(s) {
    if (!s) return "";
    let out = "";
    for (let i = 0; i < s.length; i++) {
        const code = s.charCodeAt(i);
        // Alternating widths driven by character value — visually distinct
        // per barcode so two adjacent labels are easy to tell apart.
        const w1 = 1 + (code % 3);
        const w2 = 1 + ((code >> 2) % 2);
        out += `<span style="width:${w1}px"></span><span style="width:${w2}px;background:transparent"></span>`;
    }
    return out;
}

function escape(s) {
    return String(s)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;");
}
