-- GuardPay V3: recipients (identifiers only, never credentials).
CREATE TABLE recipients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    name VARCHAR(255),
    email VARCHAR(255),
    paypal_account_id VARCHAR(128),
    is_trusted BOOLEAN NOT NULL DEFAULT FALSE,
    first_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_recipients_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_recipients_user_id ON recipients (user_id);
