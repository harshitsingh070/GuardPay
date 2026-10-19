-- GuardPay V5: payment context (call-state metadata only, never call audio).
CREATE TABLE payment_context (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,
    active_call BOOLEAN NOT NULL DEFAULT FALSE,
    unexpected_invoice BOOLEAN NOT NULL DEFAULT FALSE,
    urgent_language BOOLEAN NOT NULL DEFAULT FALSE,
    suspicious_url BOOLEAN NOT NULL DEFAULT FALSE,
    unknown_merchant BOOLEAN NOT NULL DEFAULT FALSE,
    new_recipient BOOLEAN NOT NULL DEFAULT FALSE,
    message_content TEXT,
    invoice_content TEXT,
    url VARCHAR(2048),
    phone_number VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_payment_context_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id),
    CONSTRAINT uq_payment_context_transaction_id UNIQUE (transaction_id)
);
