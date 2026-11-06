-- GuardPay V23: idle + absolute session expiry and cleanup indexes.
ALTER TABLE auth_sessions ADD COLUMN last_used_at TIMESTAMPTZ;
ALTER TABLE auth_sessions ADD COLUMN absolute_expires_at TIMESTAMPTZ;

UPDATE auth_sessions
SET last_used_at = COALESCE(created_at, now()),
    absolute_expires_at = expires_at
WHERE last_used_at IS NULL
   OR absolute_expires_at IS NULL;

ALTER TABLE auth_sessions ALTER COLUMN last_used_at SET NOT NULL;
ALTER TABLE auth_sessions ALTER COLUMN absolute_expires_at SET NOT NULL;

CREATE INDEX idx_auth_sessions_last_used_at ON auth_sessions (last_used_at);
CREATE INDEX idx_auth_sessions_absolute_expires_at ON auth_sessions (absolute_expires_at);
