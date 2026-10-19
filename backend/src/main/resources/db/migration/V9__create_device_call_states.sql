-- GuardPay V9: latest device call state (binary signal only — no audio, contacts, or content).
CREATE TABLE device_call_states (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL,
    active_call BOOLEAN NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_device_call_states_device_id UNIQUE (device_id)
);
