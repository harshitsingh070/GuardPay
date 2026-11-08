-- GuardPay V26: behavioral learning outcome ledger.
--
-- One row per (transaction, outcome). The unique constraint makes concurrent
-- and duplicate capture/webhook delivery idempotent. A non-learnable row
-- (failed, cancelled, stopped/suspicious, refunded, reversed, disputed)
-- excludes the transaction from all future baseline recomputation.

CREATE TABLE behavioral_outcome_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,
    outcome VARCHAR(24) NOT NULL,
    learnable BOOLEAN NOT NULL DEFAULT FALSE,
    source VARCHAR(48) NOT NULL,
    recommended_action VARCHAR(16),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_behavioral_outcome_events_transaction
        FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE,
    CONSTRAINT uq_behavioral_outcome_event UNIQUE (transaction_id, outcome),
    CONSTRAINT chk_behavioral_outcome_event_outcome
        CHECK (outcome IN ('COMPLETED','REFUNDED','REVERSED','DISPUTED','FAILED','CANCELLED')),
    CONSTRAINT chk_behavioral_outcome_event_action
        CHECK (recommended_action IS NULL OR recommended_action IN ('ALLOW','VERIFY','STOP'))
);

CREATE INDEX idx_behavioral_outcome_events_transaction
    ON behavioral_outcome_events (transaction_id);
CREATE INDEX idx_behavioral_outcome_events_learnable
    ON behavioral_outcome_events (learnable);
