-- GuardPay V20: real-time alerts plus a transactional notification outbox.
-- Alerts are written in the same transaction as the risk decision, so a crash
-- cannot lose a CRITICAL alert. Delivery state is tracked per channel.
CREATE TABLE alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    transaction_id UUID,
    type VARCHAR(32) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    score INT,
    recommended_action VARCHAR(16),
    amount NUMERIC(19, 4),
    currency VARCHAR(3),
    recipient_name VARCHAR(255),
    signals TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_alerts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_alerts_transaction
        FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE SET NULL,
    CONSTRAINT uq_alerts_transaction_type UNIQUE (transaction_id, type)
);
CREATE INDEX idx_alerts_user_created ON alerts (user_id, created_at DESC);
CREATE INDEX idx_alerts_severity ON alerts (severity);

CREATE TABLE notification_outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id UUID NOT NULL,
    channel VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(512),
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_notification_outbox_alert
        FOREIGN KEY (alert_id) REFERENCES alerts (id) ON DELETE CASCADE,
    CONSTRAINT uq_notification_outbox_alert_channel UNIQUE (alert_id, channel),
    CONSTRAINT chk_notification_outbox_channel CHECK (channel IN ('IN_APP', 'EMAIL', 'SMS')),
    CONSTRAINT chk_notification_outbox_status CHECK (status IN
        ('PENDING', 'SENT', 'FAILED', 'SKIPPED'))
);
CREATE INDEX idx_notification_outbox_status ON notification_outbox (status, created_at);
