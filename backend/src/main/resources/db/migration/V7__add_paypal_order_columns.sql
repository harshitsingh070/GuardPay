-- GuardPay V7: PayPal order linkage (identifiers only, never credentials).
ALTER TABLE transactions ADD COLUMN paypal_order_id VARCHAR(128);
ALTER TABLE transactions ADD COLUMN paypal_capture_id VARCHAR(128);
ALTER TABLE transactions ADD CONSTRAINT uq_transactions_paypal_order_id UNIQUE (paypal_order_id);
