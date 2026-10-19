-- GuardPay V8: PayPal webhook deliveries (idempotency + audit, payloads never stored).
CREATE TABLE paypal_webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    paypal_event_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_paypal_webhook_events_event_id UNIQUE (paypal_event_id),
    CONSTRAINT chk_paypal_webhook_events_status CHECK (status IN ('RECEIVED', 'PROCESSED', 'IGNORED', 'FAILED'))
);
CREATE INDEX idx_paypal_webhook_events_type ON paypal_webhook_events (event_type);
