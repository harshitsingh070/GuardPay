-- GuardPay V18: counterparty blocklist (mule/reported recipient intelligence).
-- One entry per recipient per user; rows are removed when a recipient is cleared.
CREATE TABLE recipient_blocklist (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    recipient_id UUID NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_recipient_blocklist_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_recipient_blocklist_recipient
        FOREIGN KEY (recipient_id) REFERENCES recipients (id) ON DELETE CASCADE,
    CONSTRAINT uq_recipient_blocklist_recipient UNIQUE (recipient_id)
);
CREATE INDEX idx_recipient_blocklist_user ON recipient_blocklist (user_id);
