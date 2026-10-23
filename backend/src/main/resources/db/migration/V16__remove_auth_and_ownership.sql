-- GuardPay V16: revert the authenticated multi-user model to one demo account.
DROP TABLE IF EXISTS auth_sessions;

ALTER TABLE users DROP COLUMN IF EXISTS password_hash;

-- Idempotency keys are global again. Keep the newest row per key before the
-- owner column and its composite uniqueness disappear.
ALTER TABLE guarded_payment_idempotency
    DROP CONSTRAINT IF EXISTS fk_guarded_payment_idempotency_user;
ALTER TABLE guarded_payment_idempotency
    DROP CONSTRAINT IF EXISTS uq_guarded_payment_idempotency_user_key;

DELETE FROM guarded_payment_idempotency older
USING guarded_payment_idempotency newer
WHERE older.idempotency_key = newer.idempotency_key
  AND (older.created_at, older.id) < (newer.created_at, newer.id);

ALTER TABLE guarded_payment_idempotency
    DROP COLUMN IF EXISTS user_id;
ALTER TABLE guarded_payment_idempotency
    ADD CONSTRAINT uq_guarded_payment_idempotency_key UNIQUE (idempotency_key);

-- One fixed demo account replaces all registered accounts. Reuse an existing
-- row that already owns the demo email instead of colliding with uq_users_email.
UPDATE users
SET external_user_id = 'guardpay-demo',
    display_name = 'GuardPay Demo',
    currency = 'USD'
WHERE email = 'demo@guardpay.local'
  AND external_user_id <> 'guardpay-demo'
  AND NOT EXISTS (SELECT 1 FROM users WHERE external_user_id = 'guardpay-demo');

INSERT INTO users (external_user_id, email, display_name, currency)
SELECT 'guardpay-demo', 'demo@guardpay.local', 'GuardPay Demo', 'USD'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE external_user_id = 'guardpay-demo');

UPDATE transactions
SET user_id = (SELECT id FROM users WHERE external_user_id = 'guardpay-demo')
WHERE user_id <> (SELECT id FROM users WHERE external_user_id = 'guardpay-demo');

UPDATE recipients
SET user_id = (SELECT id FROM users WHERE external_user_id = 'guardpay-demo')
WHERE user_id <> (SELECT id FROM users WHERE external_user_id = 'guardpay-demo');

DELETE FROM users WHERE external_user_id <> 'guardpay-demo';

-- Now that the email is free, normalise the demo account's email.
UPDATE users SET email = 'demo@guardpay.local'
WHERE external_user_id = 'guardpay-demo';

-- The capture uniqueness guard is not auth-related and remains in place.
