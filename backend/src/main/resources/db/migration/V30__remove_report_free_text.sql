-- Remove user-supplied free text from fraud reports and evidence.
-- Structured metadata (type, source, fingerprint, timestamps) is sufficient
-- for reputation scoring; raw report text is never needed and may contain
-- identifiers or other sensitive data.
ALTER TABLE fraud_intelligence_reports DROP COLUMN IF EXISTS description;
ALTER TABLE recipient_fraud_evidence DROP COLUMN IF EXISTS details;

COMMENT ON TABLE fraud_intelligence_reports IS
  'Structured fraud reports only; raw free-text descriptions are not stored.';
COMMENT ON TABLE recipient_fraud_evidence IS
  'Structured, deduplicated evidence only; free-text details are not stored.';
