-- GuardPay V6: risk assessments (score is a 0-100 heuristic, NOT a probability).
CREATE TABLE risk_assessments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,
    score INTEGER NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    recommended_action VARCHAR(16) NOT NULL,
    explanation TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_risk_assessments_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id),
    CONSTRAINT chk_risk_assessments_score CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT chk_risk_assessments_level CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_risk_assessments_action CHECK (recommended_action IN ('ALLOW', 'VERIFY', 'STOP'))
);
CREATE INDEX idx_risk_assessments_transaction_id ON risk_assessments (transaction_id);
CREATE INDEX idx_risk_assessments_risk_level ON risk_assessments (risk_level);
CREATE INDEX idx_risk_assessments_created_at ON risk_assessments (created_at);
