-- V4 — Phase 1: controlled vocab of sample rejection reasons.
--
-- Sourced from the pre-analytical sample-rejection criteria common to NABL /
-- CLIA labs. We seed the catalogue but let staff add hospital-specific codes
-- later (UI in Phase 1b will expose CRUD).

CREATE TABLE IF NOT EXISTS lab_rejection_reason (
    code           VARCHAR(50)  PRIMARY KEY,
    label          VARCHAR(150) NOT NULL,
    description    TEXT,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order  INT
);

INSERT INTO lab_rejection_reason (code, label, description, display_order) VALUES
    ('HEMOLYZED',         'Hemolyzed sample',
        'Free haemoglobin from RBC lysis interferes with potassium, LDH, AST, bilirubin assays.', 10),
    ('CLOTTED',           'Clotted sample',
        'Clot blocks the sampling needle and skews CBC / coagulation results.',                     20),
    ('CLOTTED_EDTA',      'Clotted EDTA tube',
        'EDTA tube clotted — invalid for CBC / ESR.',                                               30),
    ('QNS',               'Quantity not sufficient (QNS)',
        'Volume drawn is below the minimum required for the requested test panel.',                 40),
    ('WRONG_TUBE',        'Wrong tube / container',
        'Specimen in the wrong additive (e.g. glucose in a non-fluoride tube).',                    50),
    ('LABEL_MISMATCH',    'Label / patient mismatch',
        'Container label does not match the requisition or patient ID. Hard reject — NABL.',        60),
    ('LIPEMIC',           'Lipemic sample',
        'Visible lipaemia interferes with several biochemistry assays; recollect after fasting.',   70),
    ('ICTERIC',           'Icteric sample',
        'Severe hyperbilirubinaemia interferes with photometric assays.',                           80),
    ('EXPIRED_TUBE',      'Expired collection tube',
        'Container expired — additive concentration drifts.',                                       90),
    ('LEAKED_IN_TRANSIT', 'Leaked / damaged in transit',
        'Container compromised before reaching the lab.',                                          100),
    ('TIME_EXCEEDED',     'Stability time exceeded',
        'Sample outside the analyte-specific stability window.',                                   110),
    ('TEMP_EXCURSION',    'Temperature excursion',
        'Cold-chain broken (e.g. immunology samples held at room temperature).',                   120),
    ('INSUFFICIENT_INFO', 'Insufficient requisition information',
        'Missing clinical history / patient demographics required for interpretation.',            130)
ON CONFLICT (code) DO NOTHING;

COMMENT ON TABLE lab_rejection_reason IS
    'Controlled vocab of sample-rejection codes. Seeded with NABL-aligned defaults; hospitals can add custom rows.';
