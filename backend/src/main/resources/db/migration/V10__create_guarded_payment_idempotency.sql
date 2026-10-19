-- GuardPay V10: guarded-payment idempotency (key -> one processed payment).
CREATE TABLE guarded_payment_idempotency (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    transaction_id UUID NOT NULL,
    response_body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_guarded_payment_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_guarded_payment_idempotency_transaction
        FOREIGN KEY (transaction_id) REFERENCES transactions (id)
);
