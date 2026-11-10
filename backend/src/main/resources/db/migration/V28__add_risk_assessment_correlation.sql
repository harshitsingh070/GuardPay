-- GuardPay V28: persist correlation evidence with the stored risk decision.
--
-- Correlation findings are audit/display data, like the existing findings
-- column. Storing them on risk_assessments keeps a single assessment row as
-- the complete explanation of a decision and avoids a new table.
--
-- correlation_contribution is the number of points the correlation layer added
-- to the score (0 when no combination fired or correlation was unavailable).

ALTER TABLE risk_assessments
    ADD COLUMN correlation_findings TEXT,
    ADD COLUMN correlation_status VARCHAR(16),
    ADD COLUMN correlation_contribution INT NOT NULL DEFAULT 0;
