-- GuardPay V32: feedback lifecycle, review trail, structured evidence, and
-- payment outcomes.
--
-- Feedback is explicit human input, not an inference from payment status. A
-- successful capture is a financial outcome and never becomes a fraud label.
-- Only an admin review can verify a feedback label; verified labels are the
-- only input to the learning dataset builder.

ALTER TABLE fraud_feedback
    DROP CONSTRAINT IF EXISTS uq_fraud_feedback_transaction;

ALTER TABLE fraud_feedback
    ADD COLUMN feedback_type VARCHAR(40),
    ADD COLUMN feedback_status VARCHAR(24) NOT NULL DEFAULT 'SUBMITTED',
    ADD COLUMN risk_assessment_id UUID,
    ADD COLUMN recipient_entity_id UUID,
    ADD COLUMN related_finding_code VARCHAR(64),
    ADD COLUMN reviewer_reference UUID,
    ADD COLUMN reviewer_confidence NUMERIC(4, 3),
    ADD COLUMN reason_code VARCHAR(48),
    ADD COLUMN evidence_fingerprint VARCHAR(64),
    ADD COLUMN idempotency_key VARCHAR(128),
    ADD COLUMN reviewed_at TIMESTAMPTZ,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE fraud_feedback
SET feedback_type = CASE
        WHEN label = 'FRAUD' THEN 'SCAM_OR_SOCIAL_ENGINEERING'
        ELSE 'LEGITIMATE_PAYMENT'
    END,
    reason_code = CASE
        WHEN label = 'FRAUD' THEN 'OTHER'
        ELSE 'NONE'
    END;

ALTER TABLE fraud_feedback
    ALTER COLUMN feedback_type SET NOT NULL,
    ALTER COLUMN reason_code SET NOT NULL;

ALTER TABLE fraud_feedback
    ADD CONSTRAINT fk_fraud_feedback_risk_assessment
        FOREIGN KEY (risk_assessment_id) REFERENCES risk_assessments (id)
        ON DELETE SET NULL,
    ADD CONSTRAINT fk_fraud_feedback_recipient_entity
        FOREIGN KEY (recipient_entity_id) REFERENCES global_recipient_entities (id)
        ON DELETE SET NULL,
    ADD CONSTRAINT fk_fraud_feedback_reviewer
        FOREIGN KEY (reviewer_reference) REFERENCES users (id)
        ON DELETE SET NULL,
    ADD CONSTRAINT uq_fraud_feedback_transaction_user
        UNIQUE (transaction_id, user_reference);

CREATE INDEX idx_fraud_feedback_status_created
    ON fraud_feedback (feedback_status, created_at DESC);
CREATE INDEX idx_fraud_feedback_recipient
    ON fraud_feedback (recipient_entity_id);
CREATE INDEX idx_fraud_feedback_idempotency
    ON fraud_feedback (user_reference, idempotency_key);

CREATE TABLE fraud_feedback_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    feedback_id UUID NOT NULL,
    actor_reference UUID,
    event_type VARCHAR(32) NOT NULL,
    from_status VARCHAR(24),
    to_status VARCHAR(24),
    detail VARCHAR(1000),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_feedback_events_feedback
        FOREIGN KEY (feedback_id) REFERENCES fraud_feedback (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_feedback_events_feedback
    ON fraud_feedback_events (feedback_id, occurred_at DESC);

CREATE TABLE fraud_feedback_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    feedback_id UUID NOT NULL,
    reviewer_reference UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    from_status VARCHAR(24) NOT NULL,
    to_status VARCHAR(24) NOT NULL,
    confidence NUMERIC(4, 3),
    note VARCHAR(1000),
    reviewed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_feedback_reviews_feedback
        FOREIGN KEY (feedback_id) REFERENCES fraud_feedback (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_feedback_reviews_reviewer
        FOREIGN KEY (reviewer_reference) REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_feedback_reviews_feedback
    ON fraud_feedback_reviews (feedback_id, reviewed_at DESC);

CREATE TABLE fraud_feedback_evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    feedback_id UUID NOT NULL,
    evidence_type VARCHAR(32) NOT NULL,
    evidence_fingerprint VARCHAR(64) NOT NULL,
    related_finding_code VARCHAR(64),
    related_transaction_id UUID,
    observed_at TIMESTAMPTZ NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_feedback_evidence_feedback
        FOREIGN KEY (feedback_id) REFERENCES fraud_feedback (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_feedback_evidence_fingerprint
        UNIQUE (feedback_id, evidence_fingerprint)
);

CREATE INDEX idx_feedback_evidence_feedback
    ON fraud_feedback_evidence (feedback_id, observed_at DESC);

CREATE TABLE payment_outcome_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,
    outcome_type VARCHAR(24) NOT NULL,
    source VARCHAR(64) NOT NULL,
    event_fingerprint VARCHAR(64) NOT NULL,
    financial_only BOOLEAN NOT NULL DEFAULT TRUE,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_payment_outcome_transaction
        FOREIGN KEY (transaction_id) REFERENCES transactions (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_payment_outcome_fingerprint UNIQUE (event_fingerprint)
);

CREATE INDEX idx_payment_outcome_transaction
    ON payment_outcome_events (transaction_id, occurred_at DESC);

COMMENT ON TABLE fraud_feedback IS
    'Explicit human feedback with a decision snapshot. One current label per user/transaction; history is append-only.';
COMMENT ON TABLE payment_outcome_events IS
    'Financial lifecycle events (capture/refund/reversal/dispute). Never a fraud label.';
