-- GuardPay V4: transactions (money is NUMERIC, never float/double).
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    recipient_id UUID,
    merchant_id UUID,
    paypal_transaction_id VARCHAR(128),
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    transaction_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    description TEXT,
    transaction_timestamp TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_transactions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_transactions_recipient FOREIGN KEY (recipient_id) REFERENCES recipients (id),
    CONSTRAINT fk_transactions_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id),
    CONSTRAINT chk_transactions_amount_non_negative CHECK (amount >= 0),
    CONSTRAINT chk_transactions_type CHECK (transaction_type IN ('PAYMENT', 'REFUND', 'TRANSFER')),
    CONSTRAINT chk_transactions_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);
CREATE INDEX idx_transactions_user_id ON transactions (user_id);
CREATE INDEX idx_transactions_recipient_id ON transactions (recipient_id);
CREATE INDEX idx_transactions_merchant_id ON transactions (merchant_id);
CREATE INDEX idx_transactions_paypal_id ON transactions (paypal_transaction_id);
CREATE INDEX idx_transactions_timestamp ON transactions (transaction_timestamp);
CREATE INDEX idx_transactions_created_at ON transactions (created_at);
