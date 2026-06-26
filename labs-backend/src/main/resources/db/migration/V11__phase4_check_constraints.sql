-- V11 — Phase 4 hardening: DB-level CHECK constraints on closed-vocab columns.
--
-- Adds production-grade validation at the Postgres layer so any client (labs,
-- HMS proxy, ad-hoc SQL, a future microservice) gets a clean 23514 violation
-- instead of writing garbage values. Until now the only enforcement was the
-- application-side Java enum / Jackson serializer — which the DB has no way
-- to verify.
--
-- Approach: pure CHECK constraints on the existing VARCHAR columns. No
-- CREATE TYPE / ENUM types — that would require Hibernate's
-- @JdbcTypeCode(NAMED_ENUM) or a `stringtype=unspecified` JDBC param, both
-- of which add coordination risk for negligible perf gain at our scale.
-- CHECK gives identical DB-level validation with zero Java / frontend churn.
--
-- All constraints use named conventions (chk_<table>_<column>) so a future
-- DROP / re-ADD (when a vocab grows — e.g. discipline + 'MICROBIOLOGY') is a
-- one-liner.
--
-- Cross-app dependency audit (workflow run wf_f9ad3ca5-93b) confirmed:
--   * HMS-backend: read-only via REST proxy — no JPA entities for these
--     columns; cannot violate.
--   * HMS-frontend: hardcoded enum strings match our vocab exactly; the two
--     COORDINATE columns (discipline, investigation_type) only ever receive
--     'PATHOLOGY' / 'RADIOLOGY' from HMS — within our 4-value / 2-value sets.
--   * Pharmacy: zero references.
--   * Labs-frontend: every dropdown / filter constant already uses these
--     exact strings — no behaviour change.
--
-- Skipped (already constrained or HMS-owned domain):
--   lab_orders.status_id / priority_id        — INT FK to ref table
--   radiology_orders.status_id / priority_id  — INT FK to ref table
--   health_checkup_bookings.status_id         — INT FK to ref table
--   invoices.status_id                        — HMS-owned domain enum
--   admissions.status_id                      — HMS-owned domain enum

-- ── lab_test_catalog ─────────────────────────────────────────────────────
ALTER TABLE lab_test_catalog
    ADD CONSTRAINT chk_lab_test_catalog_discipline
    CHECK (discipline IS NULL OR discipline IN ('PATHOLOGY','RADIOLOGY','CYTOLOGY','HISTOPATHOLOGY'));

ALTER TABLE lab_test_catalog
    ADD CONSTRAINT chk_lab_test_catalog_value_type
    CHECK (value_type IN ('NUMERIC','TEXT','CODED','RATIO'));

ALTER TABLE lab_test_catalog
    ADD CONSTRAINT chk_lab_test_catalog_specimen_kind
    CHECK (specimen_kind IS NULL OR specimen_kind IN ('BLOOD','URINE','STOOL','SWAB','CSF','TISSUE','OTHER'));

ALTER TABLE lab_test_catalog
    ADD CONSTRAINT chk_lab_test_catalog_container_type
    CHECK (default_container_type IS NULL OR default_container_type IN
        ('EDTA','CITRATE','HEPARIN','PLAIN','FLUORIDE','URINE_CUP','STOOL_CUP','SWAB','OTHER'));

-- ── lab_specimen ─────────────────────────────────────────────────────────
ALTER TABLE lab_specimen
    ADD CONSTRAINT chk_lab_specimen_container_type
    CHECK (container_type IS NULL OR container_type IN
        ('EDTA','CITRATE','HEPARIN','PLAIN','FLUORIDE','URINE_CUP','STOOL_CUP','SWAB','OTHER'));

-- ── lab_reference_ranges ─────────────────────────────────────────────────
ALTER TABLE lab_reference_ranges
    ADD CONSTRAINT chk_lab_ref_sex
    CHECK (sex IN ('ANY','MALE','FEMALE'));

ALTER TABLE lab_reference_ranges
    ADD CONSTRAINT chk_lab_ref_special_state
    CHECK (special_state IS NULL OR special_state IN ('PREGNANT','NEONATE','FASTING','POSTPRANDIAL'));

-- ── lab_test_result ──────────────────────────────────────────────────────
ALTER TABLE lab_test_result
    ADD CONSTRAINT chk_lab_result_status
    CHECK (result_status IN ('PENDING','PRELIMINARY','FINAL','CORRECTED','CANCELLED'));

ALTER TABLE lab_test_result
    ADD CONSTRAINT chk_lab_result_abnormal_flag
    CHECK (abnormal_flag IS NULL OR abnormal_flag IN ('LL','L','N','H','HH','A','AA'));

-- ── lab_package_items ────────────────────────────────────────────────────
ALTER TABLE lab_package_items
    ADD CONSTRAINT chk_lab_pkg_investigation_type
    CHECK (investigation_type IN ('PATHOLOGY','RADIOLOGY'));

-- ── audit_log ────────────────────────────────────────────────────────────
ALTER TABLE audit_log
    ADD CONSTRAINT chk_audit_log_operation
    CHECK (operation IN (
        'CREATE',
        'UPDATE',
        'DELETE',
        'STATUS_CHANGE',
        'REPORT_GENERATED',
        'VERIFY',
        'AUTHORISE',
        'AMEND',
        'RECEIVE',
        'ACCESSION',
        'REJECT',
        'CANCEL',
        'PANIC_CALL',
        'TOGGLE',
        'AUTO_CREATE_ON_COLLECT'
    ));
