-- GuardPay V22: authenticated, per-device credentials and owner-scoped call state.
--
-- Existing device identities are migrated from transaction history. A device
-- with no historical owner cannot be assigned safely, so its untrusted call
-- state is removed rather than exposed to an arbitrary account.
CREATE TABLE devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    credential_hash VARCHAR(64) NOT NULL,
    label VARCHAR(120),
    revoked_at TIMESTAMPTZ,
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_devices_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_devices_credential_hash UNIQUE (credential_hash)
);
CREATE INDEX idx_devices_user_id ON devices (user_id);

-- One owner per historical device id: the most recent transaction wins.
INSERT INTO devices (id, user_id, credential_hash, label, created_at, updated_at)
SELECT d.device_id,
       d.user_id,
       md5(d.device_id::text) || md5(d.device_id::text || ':migrated'),
       'Migrated device',
       now(),
       now()
FROM (
    SELECT DISTINCT ON (device_id) device_id, user_id
    FROM transactions
    WHERE device_id IS NOT NULL
    ORDER BY device_id, transaction_timestamp DESC
) d
ON CONFLICT (id) DO NOTHING;

ALTER TABLE device_call_states ADD COLUMN user_id UUID;
UPDATE device_call_states s
SET user_id = d.user_id
FROM devices d
WHERE s.device_id = d.id;
DELETE FROM device_call_states WHERE user_id IS NULL;
ALTER TABLE device_call_states ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE device_call_states
    ADD CONSTRAINT fk_device_call_states_user
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE device_call_states
    ADD CONSTRAINT fk_device_call_states_device
    FOREIGN KEY (device_id) REFERENCES devices (id) ON DELETE CASCADE;
CREATE INDEX idx_device_call_states_user_device
    ON device_call_states (user_id, device_id);
