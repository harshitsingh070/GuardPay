-- Persist the approval URL with the order so a verified replay can return the
-- same link instead of a null URL or a second PayPal order.
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS paypal_approval_url VARCHAR(2048);

COMMENT ON COLUMN transactions.paypal_approval_url IS
    'PayPal approve link returned at order creation; replay data, never a secret.';
