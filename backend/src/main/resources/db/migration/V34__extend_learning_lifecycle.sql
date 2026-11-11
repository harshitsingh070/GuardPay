-- GuardPay V34: learning traceability, exclusions, deployment, and rollback.
--
-- Labels are only eligible after an admin verification. Dataset generation is
-- immutable and checksum-addressed. Candidate models are rule calibrations;
-- promotion and rollback stay explicit admin actions.

ALTER TABLE learning_datasets
    ADD COLUMN label_policy_version VARCHAR(32) NOT NULL DEFAULT 'labels-v1',
    ADD COLUMN excluded_count INT NOT NULL DEFAULT 0;

CREATE TABLE learning_dataset_exclusions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dataset_id UUID NOT NULL,
    feedback_id UUID,
    reason_code VARCHAR(48) NOT NULL,
    detail VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_learning_exclusions_dataset
        FOREIGN KEY (dataset_id) REFERENCES learning_datasets (id) ON DELETE CASCADE,
    CONSTRAINT fk_learning_exclusions_feedback
        FOREIGN KEY (feedback_id) REFERENCES fraud_feedback (id) ON DELETE SET NULL
);

CREATE INDEX idx_learning_exclusions_dataset
    ON learning_dataset_exclusions (dataset_id, reason_code);

ALTER TABLE learning_models
    ADD COLUMN rollback_reference UUID,
    ADD COLUMN dataset_checksum VARCHAR(64),
    ADD COLUMN label_policy_version VARCHAR(32),
    ADD COLUMN feature_version VARCHAR(32),
    ADD COLUMN false_positive_rate DOUBLE PRECISION,
    ADD COLUMN false_negative_rate DOUBLE PRECISION,
    ADD COLUMN champion_comparison_json TEXT,
    ADD COLUMN training_failure_reason VARCHAR(500);

CREATE INDEX idx_learning_models_active
    ON learning_models (status, model_type, activated_at DESC);

COMMENT ON TABLE learning_dataset_exclusions IS
    'Explicit record of why a feedback label was not eligible for a dataset version.';
COMMENT ON TABLE learning_models IS
    'Versioned rule-calibration candidates. Only an approved model can become ACTIVE.';
