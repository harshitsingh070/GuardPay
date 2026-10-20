-- GuardPay V13: persist the engine findings behind each decision so the
-- transaction history can explain WHY a payment was allowed/verified/blocked.
-- Stored as a JSON array of findings; display/audit data only, never re-scored.
ALTER TABLE risk_assessments ADD COLUMN findings TEXT;
