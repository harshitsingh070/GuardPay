-- GuardPay V35: record which deployed calibration produced a persisted decision.
--
-- Null/absent means the deterministic defaults were used. This is audit
-- provenance only; it never participates in scoring.

ALTER TABLE risk_assessments
    ADD COLUMN calibration_version VARCHAR(64);

CREATE INDEX idx_risk_assessments_calibration
    ON risk_assessments (calibration_version);

COMMENT ON COLUMN risk_assessments.calibration_version IS
    'Deployed rule-calibration model version, or deterministic-default when absent.';
