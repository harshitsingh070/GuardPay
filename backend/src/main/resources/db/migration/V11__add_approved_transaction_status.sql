ALTER TABLE transactions DROP CONSTRAINT chk_transactions_status;

ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_status CHECK (status IN
        ('PENDING', 'APPROVED', 'COMPLETED', 'FAILED', 'CANCELLED'));
