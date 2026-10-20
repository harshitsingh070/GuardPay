-- GuardPay V12: controlled VERIFY state. One verification per failed-and-
-- challenged payment attempt; the backend alone sets the outcome.
CREATE TABLE payment_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    challenge_code_hash VARCHAR(128) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_payment_verifications_transaction
        FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE,
    CONSTRAINT uq_payment_verifications_transaction UNIQUE (transaction_id),
    CONSTRAINT chk_payment_verifications_status CHECK (status IN
        ('PENDING', 'VERIFIED', 'FAILED', 'EXPIRED'))
);
CREATE INDEX idx_payment_verifications_status ON payment_verifications (status);
