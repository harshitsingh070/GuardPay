-- GuardPay V21: authenticated accounts and owner-scoped idempotency keys.
ALTER TABLE users ADD COLUMN password_hash VARCHAR(100);

CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_auth_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_auth_sessions_token_hash UNIQUE (token_hash)
);
CREATE INDEX idx_auth_sessions_user_id ON auth_sessions (user_id);
CREATE INDEX idx_auth_sessions_expires_at ON auth_sessions (expires_at);

-- A client-supplied idempotency key is only unique within one owner.
ALTER TABLE guarded_payment_idempotency ADD COLUMN user_id UUID;
UPDATE guarded_payment_idempotency g
SET user_id = t.user_id
FROM transactions t
WHERE g.transaction_id = t.id;
ALTER TABLE guarded_payment_idempotency ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE guarded_payment_idempotency
    ADD CONSTRAINT fk_guarded_payment_idempotency_user
    FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE guarded_payment_idempotency
    DROP CONSTRAINT uq_guarded_payment_idempotency_key;
ALTER TABLE guarded_payment_idempotency
    ADD CONSTRAINT uq_guarded_payment_idempotency_user_key
    UNIQUE (user_id, idempotency_key);
