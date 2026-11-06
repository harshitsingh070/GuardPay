-- GuardPay V25: optimistic locking for behavioral profiles.
--
-- Adds a version column to user_behavior_profiles for optimistic concurrency
-- control, preventing lost updates when multiple payment evaluations for the
-- same user run concurrently. Also adds a last_included_transaction_id column
-- for idempotent profile updates.

ALTER TABLE user_behavior_profiles
    ADD COLUMN version INTEGER NOT NULL DEFAULT 1;

ALTER TABLE user_behavior_profiles
    ADD COLUMN last_included_transaction_id UUID;

-- Index for efficient lookup by last included transaction (idempotency checks).
CREATE INDEX idx_user_behavior_profiles_last_tx ON user_behavior_profiles
    (last_included_transaction_id)
    WHERE last_included_transaction_id IS NOT NULL;
