-- GuardPay V17: account-takeover context captured with each payment attempt.
-- All columns are nullable so existing rows and older clients keep working.
ALTER TABLE transactions
    ADD COLUMN device_id UUID,
    ADD COLUMN device_fingerprint VARCHAR(128),
    ADD COLUMN client_ip VARCHAR(64),
    ADD COLUMN geo_country VARCHAR(2),
    ADD COLUMN geo_city VARCHAR(128),
    ADD COLUMN asn INTEGER,
    ADD COLUMN vpn_or_proxy BOOLEAN;

CREATE INDEX idx_transactions_user_device ON transactions (user_id, device_id);
CREATE INDEX idx_transactions_user_geo ON transactions (user_id, geo_country);
