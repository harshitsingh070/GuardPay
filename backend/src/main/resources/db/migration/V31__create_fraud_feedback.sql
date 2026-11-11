CREATE TABLE fraud_feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL UNIQUE
        REFERENCES transactions(id) ON DELETE CASCADE,
    user_reference UUID NOT NULL
        REFERENCES users(id) ON DELETE CASCADE,
    label VARCHAR(16) NOT NULL,
    source VARCHAR(16) NOT NULL DEFAULT 'USER',
    decision_action VARCHAR(16),
    risk_score INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_fraud_feedback_label_created
    ON fraud_feedback (label, created_at DESC);

CREATE INDEX idx_fraud_feedback_user
    ON fraud_feedback (user_reference);

COMMENT ON TABLE fraud_feedback IS
    'Ground-truth labels for offline evaluation and retraining; one per transaction.';
