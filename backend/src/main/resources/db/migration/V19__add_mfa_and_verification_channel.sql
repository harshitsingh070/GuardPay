-- GuardPay V19: real second-factor support.
-- TOTP secrets are per user. The secret is only returned once at enrollment
-- through the demo MFA endpoint; payment responses never contain it.
ALTER TABLE users
    ADD COLUMN totp_secret VARCHAR(64);

-- The delivery channel for a VERIFY challenge (TOTP, EMAIL, SIMULATED).
-- Existing rows are simulated challenges from earlier milestones.
ALTER TABLE payment_verifications
    ADD COLUMN channel VARCHAR(16) NOT NULL DEFAULT 'SIMULATED';
