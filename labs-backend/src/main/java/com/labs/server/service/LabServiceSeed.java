package com.labs.server.service;

import com.labs.server.entity.LabService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Seed of common Indian-lab analytes with LOINC codes + workflow defaults.
 *
 * Used by {@link LabCatalogService#seedFor(UUID)} the first time a
 * hospital lists its catalogue. Mirrors {@link LabReferenceRangeSeed} so
 * the pattern is consistent across labs catalogue seeds.
 *
 * Not exhaustive — picks the 30-40 highest-volume tests across haematology,
 * biochemistry, endocrinology, glycaemic, and lipid. Hospitals add the long
 * tail (microbiology, special chem, immunology) through the admin UI.
 */
@Component
public class LabServiceSeed {

    public List<LabService> defaults(UUID hospitalId) {
        return List.of(
            // ───────── Panels (parent rows) ─────────
            panel(hospitalId, "CBC",    "Complete Blood Count",      "HAEMATOLOGY",   "BLOOD", "EDTA",     350, 10),
            panel(hospitalId, "LFT",    "Liver Function Test",       "BIOCHEMISTRY",  "BLOOD", "PLAIN",    450, 20),
            panel(hospitalId, "RFT",    "Renal Function Test",       "BIOCHEMISTRY",  "BLOOD", "PLAIN",    450, 30),
            panel(hospitalId, "LIPID",  "Lipid Profile",             "BIOCHEMISTRY",  "BLOOD", "PLAIN",    600, 40),
            panel(hospitalId, "THYROID","Thyroid Function Test",     "ENDOCRINOLOGY", "BLOOD", "PLAIN",    700, 50),
            panel(hospitalId, "DIAB",   "Diabetes Profile",          "BIOCHEMISTRY",  "BLOOD", "FLUORIDE", 500, 60),
            panel(hospitalId, "URINE",  "Urine Routine",             "BIOCHEMISTRY",  "URINE", "URINE_CUP",200, 70),

            // ───────── CBC analytes ─────────
            analyte(hospitalId, "WBC",         "Total WBC Count",        "718-7" , "CBC", "HAEMATOLOGY", "10^3/µL", "Impedance",          10),
            analyte(hospitalId, "RBC",         "RBC Count",              "789-8" , "CBC", "HAEMATOLOGY", "10^6/µL", "Impedance",          20),
            analyte(hospitalId, "HB",          "Hemoglobin",             "718-7" , "CBC", "HAEMATOLOGY", "g/dL",    "Cyanide-free SLS",   30),
            analyte(hospitalId, "HCT",         "Hematocrit",             "4544-3", "CBC", "HAEMATOLOGY", "%",       "Calculated",         40),
            analyte(hospitalId, "MCV",         "MCV",                    "787-2" , "CBC", "HAEMATOLOGY", "fL",      "Calculated",         50),
            analyte(hospitalId, "MCH",         "MCH",                    "785-6" , "CBC", "HAEMATOLOGY", "pg",      "Calculated",         60),
            analyte(hospitalId, "MCHC",        "MCHC",                   "786-4" , "CBC", "HAEMATOLOGY", "g/dL",    "Calculated",         70),
            analyte(hospitalId, "PLT",         "Platelet Count",         "777-3" , "CBC", "HAEMATOLOGY", "10^3/µL", "Impedance",          80),
            analyte(hospitalId, "NEUT_PCT",    "Neutrophils %",          "770-8" , "CBC", "HAEMATOLOGY", "%",       "Flow cytometry",     90),
            analyte(hospitalId, "LYMPH_PCT",   "Lymphocytes %",          "736-9" , "CBC", "HAEMATOLOGY", "%",       "Flow cytometry",    100),
            analyte(hospitalId, "MONO_PCT",    "Monocytes %",            "5905-5", "CBC", "HAEMATOLOGY", "%",       "Flow cytometry",    110),
            analyte(hospitalId, "EOS_PCT",     "Eosinophils %",          "713-8" , "CBC", "HAEMATOLOGY", "%",       "Flow cytometry",    120),
            analyte(hospitalId, "BASO_PCT",    "Basophils %",            "706-2" , "CBC", "HAEMATOLOGY", "%",       "Flow cytometry",    130),

            // ───────── LFT analytes ─────────
            analyte(hospitalId, "SGOT",        "SGOT (AST)",             "1920-8", "LFT", "BIOCHEMISTRY","U/L",     "IFCC",               10),
            analyte(hospitalId, "SGPT",        "SGPT (ALT)",             "1742-6", "LFT", "BIOCHEMISTRY","U/L",     "IFCC",               20),
            analyte(hospitalId, "ALP",         "Alkaline Phosphatase",   "6768-6", "LFT", "BIOCHEMISTRY","U/L",     "IFCC",               30),
            analyte(hospitalId, "GGT",         "Gamma-GT",               "2324-2", "LFT", "BIOCHEMISTRY","U/L",     "IFCC",               40),
            analyte(hospitalId, "TBIL",        "Total Bilirubin",        "1975-2", "LFT", "BIOCHEMISTRY","mg/dL",   "Diazo",              50),
            analyte(hospitalId, "DBIL",        "Direct Bilirubin",       "1968-7", "LFT", "BIOCHEMISTRY","mg/dL",   "Diazo",              60),
            analyte(hospitalId, "TP",          "Total Protein",          "2885-2", "LFT", "BIOCHEMISTRY","g/dL",    "Biuret",             70),
            analyte(hospitalId, "ALB",         "Albumin",                "1751-7", "LFT", "BIOCHEMISTRY","g/dL",    "BCG",                80),

            // ───────── RFT analytes ─────────
            analyte(hospitalId, "UREA",        "Urea",                   "3094-0", "RFT", "BIOCHEMISTRY","mg/dL",   "Urease",             10),
            analyte(hospitalId, "CREAT",       "Creatinine",             "2160-0", "RFT", "BIOCHEMISTRY","mg/dL",   "Jaffe (kinetic)",    20),
            analyte(hospitalId, "URICA",       "Uric Acid",              "3084-1", "RFT", "BIOCHEMISTRY","mg/dL",   "Uricase",            30),
            analyte(hospitalId, "NA",          "Sodium",                 "2951-2", "RFT", "BIOCHEMISTRY","mmol/L",  "ISE",                40),
            analyte(hospitalId, "K",           "Potassium",              "2823-3", "RFT", "BIOCHEMISTRY","mmol/L",  "ISE",                50),
            analyte(hospitalId, "CL",          "Chloride",               "2075-0", "RFT", "BIOCHEMISTRY","mmol/L",  "ISE",                60),

            // ───────── Lipid analytes ─────────
            analyte(hospitalId, "TC",          "Total Cholesterol",      "2093-3", "LIPID","BIOCHEMISTRY","mg/dL",  "CHOD-PAP",           10),
            analyte(hospitalId, "HDL",         "HDL Cholesterol",        "2085-9", "LIPID","BIOCHEMISTRY","mg/dL",  "Direct",             20),
            analyte(hospitalId, "LDL",         "LDL Cholesterol",        "2089-1", "LIPID","BIOCHEMISTRY","mg/dL",  "Direct / Friedewald",30),
            analyte(hospitalId, "TG",          "Triglycerides",          "2571-8", "LIPID","BIOCHEMISTRY","mg/dL",  "GPO-PAP",            40),
            analyte(hospitalId, "VLDL",        "VLDL Cholesterol",       "13458-5","LIPID","BIOCHEMISTRY","mg/dL",  "Calculated",         50),

            // ───────── Thyroid analytes ─────────
            analyte(hospitalId, "TSH",         "TSH",                    "3016-3", "THYROID","ENDOCRINOLOGY","µIU/mL", "CLIA",            10),
            analyte(hospitalId, "T3",          "Total T3",               "3053-6", "THYROID","ENDOCRINOLOGY","ng/dL",  "CLIA",            20),
            analyte(hospitalId, "T4",          "Total T4",               "3026-2", "THYROID","ENDOCRINOLOGY","µg/dL",  "CLIA",            30),
            analyte(hospitalId, "FT3",         "Free T3",                "3051-0", "THYROID","ENDOCRINOLOGY","pg/mL",  "CLIA",            40),
            analyte(hospitalId, "FT4",         "Free T4",                "3024-7", "THYROID","ENDOCRINOLOGY","ng/dL",  "CLIA",            50),

            // ───────── Diabetes analytes ─────────
            analyte(hospitalId, "FBS",         "Fasting Blood Sugar",    "1558-6", "DIAB", "BIOCHEMISTRY","mg/dL",  "Hexokinase",         10),
            analyte(hospitalId, "PPBS",        "Post-Prandial Glucose",  "1521-4", "DIAB", "BIOCHEMISTRY","mg/dL",  "Hexokinase",         20),
            analyte(hospitalId, "HBA1C",       "HbA1c",                  "4548-4", "DIAB", "BIOCHEMISTRY","%",      "HPLC",               30)
        );
    }

    private LabService panel(UUID hospitalId, String code, String name, String category,
                                  String specimenKind, String container, int price, int displayOrder) {
        return LabService.builder()
                .hospitalId(hospitalId)
                .testCode(code)
                .name(name)
                .category(category)
                .discipline("PATHOLOGY")
                .specimenKind(specimenKind)
                .defaultContainerType(container)
                .defaultVolumeMl(specimenKind.equals("BLOOD") ? new BigDecimal("3.0") : null)
                .fastingRequired(code.equals("LIPID") || code.equals("DIAB"))
                .isPanel(true)
                .valueType("NUMERIC")
                .requiresAuthorisation(false)
                .price(new BigDecimal(price))
                .displayOrder(displayOrder)
                .active(true)
                .build();
    }

    private LabService analyte(UUID hospitalId, String code, String name, String loinc, String parentPanel,
                                    String category, String unit, String method, int displayOrder) {
        return LabService.builder()
                .hospitalId(hospitalId)
                .testCode(code)
                .loincCode(loinc)
                .name(name)
                .category(category)
                .discipline("PATHOLOGY")
                .specimenKind("BLOOD")
                .defaultUnit(unit)
                .defaultMethod(method)
                .parentPanelCode(parentPanel)
                .isPanel(false)
                .valueType("NUMERIC")
                .requiresAuthorisation(false)
                .displayOrder(displayOrder)
                .active(true)
                .build();
    }
}
