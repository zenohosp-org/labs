package com.labs.server.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Canonical default reference bands seeded the first time a hospital reads
 * its catalogue. Conservative ranges drawn from standard Indian-lab
 * references (Henry's Clinical Diagnosis, NIN guidelines). Admins edit
 * these from the Settings → Reference Ranges page.
 *
 * Numbers chosen for safety: where the literature has a "borderline" zone,
 * we use the conservative cutoff so an out-of-range flag prompts review.
 * Editable per-hospital — no global authority.
 */
@Component
public class LabReferenceRangeSeed {

    public record Default(
            String testName,
            String category,
            String sex,
            Integer minAge,
            Integer maxAge,
            BigDecimal minValue,
            BigDecimal maxValue,
            String unit,
            String rangeText
    ) {}

    private static Default d(String testName, String category, String sex,
                             Integer minAge, Integer maxAge,
                             String min, String max,
                             String unit, String rangeText) {
        return new Default(testName, category, sex, minAge, maxAge,
                min != null ? new BigDecimal(min) : null,
                max != null ? new BigDecimal(max) : null,
                unit, rangeText);
    }

    private final List<Default> defaults = List.of(
        // ── Haematology ──────────────────────────────────────────────
        d("Hemoglobin",      "HAEMATOLOGY", "MALE",    18, 200, "13.5", "17.5", "g/dL",  "13.5 – 17.5 g/dL"),
        d("Hemoglobin",      "HAEMATOLOGY", "FEMALE",  18, 200, "12.0", "15.5", "g/dL",  "12.0 – 15.5 g/dL"),
        d("Hemoglobin",      "HAEMATOLOGY", "ANY",      6,  17, "11.5", "15.5", "g/dL",  "11.5 – 15.5 g/dL"),
        d("Hemoglobin",      "HAEMATOLOGY", "ANY",      0,   5, "11.0", "14.0", "g/dL",  "11.0 – 14.0 g/dL"),
        d("Total WBC Count", "HAEMATOLOGY", "ANY",      0, 200, "4000", "11000", "/µL",  "4 000 – 11 000 /µL"),
        d("Platelet Count",  "HAEMATOLOGY", "ANY",      0, 200, "150000", "450000", "/µL", "1.5 – 4.5 lakh /µL"),
        d("RBC Count",       "HAEMATOLOGY", "MALE",    18, 200, "4.7", "6.1",  "M/µL",  "4.7 – 6.1 M/µL"),
        d("RBC Count",       "HAEMATOLOGY", "FEMALE",  18, 200, "4.2", "5.4",  "M/µL",  "4.2 – 5.4 M/µL"),
        d("PCV / Hematocrit","HAEMATOLOGY", "MALE",    18, 200, "40",   "50",   "%",     "40 – 50 %"),
        d("PCV / Hematocrit","HAEMATOLOGY", "FEMALE",  18, 200, "36",   "46",   "%",     "36 – 46 %"),
        d("ESR",             "HAEMATOLOGY", "MALE",     0, 200, "0",    "15",   "mm/hr", "0 – 15 mm/hr"),
        d("ESR",             "HAEMATOLOGY", "FEMALE",   0, 200, "0",    "20",   "mm/hr", "0 – 20 mm/hr"),

        // ── Biochemistry — sugar / diabetes ──────────────────────────
        d("Fasting Blood Sugar", "BIOCHEMISTRY", "ANY", 0, 200, "70",  "100", "mg/dL", "70 – 100 mg/dL"),
        d("Post-Prandial Blood Sugar", "BIOCHEMISTRY", "ANY", 0, 200, "70", "140", "mg/dL", "70 – 140 mg/dL"),
        d("Random Blood Sugar",  "BIOCHEMISTRY", "ANY", 0, 200, "70",  "140", "mg/dL", "70 – 140 mg/dL"),
        d("HbA1c",               "BIOCHEMISTRY", "ANY", 0, 200, "4.0", "5.7", "%",     "4.0 – 5.7 %"),

        // ── Renal ─────────────────────────────────────────────────────
        d("Creatinine",      "BIOCHEMISTRY", "MALE",   18, 200, "0.7", "1.3", "mg/dL", "0.7 – 1.3 mg/dL"),
        d("Creatinine",      "BIOCHEMISTRY", "FEMALE", 18, 200, "0.6", "1.1", "mg/dL", "0.6 – 1.1 mg/dL"),
        d("Urea",            "BIOCHEMISTRY", "ANY",     0, 200, "15",  "40",  "mg/dL", "15 – 40 mg/dL"),
        d("Uric Acid",       "BIOCHEMISTRY", "MALE",    0, 200, "3.4", "7.0", "mg/dL", "3.4 – 7.0 mg/dL"),
        d("Uric Acid",       "BIOCHEMISTRY", "FEMALE",  0, 200, "2.4", "6.0", "mg/dL", "2.4 – 6.0 mg/dL"),

        // ── Liver function ────────────────────────────────────────────
        d("Bilirubin Total", "BIOCHEMISTRY", "ANY", 0, 200, "0.1", "1.2",  "mg/dL", "0.1 – 1.2 mg/dL"),
        d("Bilirubin Direct","BIOCHEMISTRY", "ANY", 0, 200, "0.0", "0.3",  "mg/dL", "0.0 – 0.3 mg/dL"),
        d("SGPT (ALT)",      "BIOCHEMISTRY", "ANY", 0, 200, "5",   "40",   "U/L",   "5 – 40 U/L"),
        d("SGOT (AST)",      "BIOCHEMISTRY", "ANY", 0, 200, "5",   "40",   "U/L",   "5 – 40 U/L"),
        d("Alkaline Phosphatase", "BIOCHEMISTRY", "ANY", 18, 200, "40", "129", "U/L", "40 – 129 U/L"),

        // ── Lipid profile ────────────────────────────────────────────
        d("Total Cholesterol", "BIOCHEMISTRY", "ANY", 0, 200, null,  "200", "mg/dL", "< 200 mg/dL"),
        d("LDL Cholesterol",   "BIOCHEMISTRY", "ANY", 0, 200, null,  "100", "mg/dL", "< 100 mg/dL"),
        d("HDL Cholesterol",   "BIOCHEMISTRY", "MALE",   0, 200, "40", null, "mg/dL", "> 40 mg/dL"),
        d("HDL Cholesterol",   "BIOCHEMISTRY", "FEMALE", 0, 200, "50", null, "mg/dL", "> 50 mg/dL"),
        d("Triglycerides",     "BIOCHEMISTRY", "ANY", 0, 200, null,  "150", "mg/dL", "< 150 mg/dL"),

        // ── Thyroid ──────────────────────────────────────────────────
        d("TSH",  "ENDOCRINOLOGY", "ANY", 18, 200, "0.4", "4.5", "µIU/mL", "0.4 – 4.5 µIU/mL"),
        d("Free T3", "ENDOCRINOLOGY", "ANY", 18, 200, "2.0", "4.4", "pg/mL", "2.0 – 4.4 pg/mL"),
        d("Free T4", "ENDOCRINOLOGY", "ANY", 18, 200, "0.8", "1.8", "ng/dL", "0.8 – 1.8 ng/dL"),

        // ── Electrolytes ─────────────────────────────────────────────
        d("Sodium",    "BIOCHEMISTRY", "ANY", 0, 200, "135", "145", "mEq/L", "135 – 145 mEq/L"),
        d("Potassium", "BIOCHEMISTRY", "ANY", 0, 200, "3.5", "5.1", "mEq/L", "3.5 – 5.1 mEq/L"),
        d("Chloride",  "BIOCHEMISTRY", "ANY", 0, 200, "98",  "107", "mEq/L", "98 – 107 mEq/L"),
        d("Calcium",   "BIOCHEMISTRY", "ANY", 0, 200, "8.6", "10.2", "mg/dL", "8.6 – 10.2 mg/dL"),

        // ── Vitamins ─────────────────────────────────────────────────
        d("Vitamin D (25-OH)",  "BIOCHEMISTRY", "ANY", 0, 200, "30",  "100", "ng/mL", "30 – 100 ng/mL"),
        d("Vitamin B12",        "BIOCHEMISTRY", "ANY", 0, 200, "200", "900", "pg/mL", "200 – 900 pg/mL"),

        // ── Vitals (BP / pulse) — useful at sample collection ─────────
        d("Systolic BP",  "VITALS", "ANY", 18, 200, "90", "120", "mmHg", "90 – 120 mmHg"),
        d("Diastolic BP", "VITALS", "ANY", 18, 200, "60", "80",  "mmHg", "60 – 80 mmHg"),
        d("Heart Rate",   "VITALS", "ANY", 18, 200, "60", "100", "bpm",  "60 – 100 bpm"),
        d("Heart Rate",   "VITALS", "ANY",  6,  17, "75", "118", "bpm",  "75 – 118 bpm"),
        d("Heart Rate",   "VITALS", "ANY",  0,   5, "80", "140", "bpm",  "80 – 140 bpm"),
        d("Respiratory Rate", "VITALS", "ANY", 18, 200, "12", "20", "/min", "12 – 20 /min"),
        d("Temperature",  "VITALS", "ANY",  0, 200, "36.1", "37.2", "°C",  "36.1 – 37.2 °C"),
        d("SpO2",         "VITALS", "ANY",  0, 200, "95", "100", "%",    "95 – 100 %")
    );

    public List<Default> defaults() {
        return defaults;
    }
}
