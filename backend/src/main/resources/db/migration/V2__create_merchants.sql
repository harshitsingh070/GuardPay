-- GuardPay V2: merchants (reputation/familiarity context).
CREATE TABLE merchants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    merchant_identifier VARCHAR(128),
    domain VARCHAR(255),
    category VARCHAR(128),
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_merchants_identifier ON merchants (merchant_identifier);
CREATE INDEX idx_merchants_domain ON merchants (domain);
