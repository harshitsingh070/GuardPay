-- GuardPay V33: versioned feedback datasets and rule-calibration candidates.
--
-- Datasets are built only from admin-verified feedback. Feature values come
-- from the persisted decision-time assessment, never from the label or from
-- post-decision outcomes. Models are versioned candidates; only an explicit
-- admin approval/activation changes the champion calibration.

CREATE TABLE learning_datasets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dataset_version VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'READY',
    feature_version VARCHAR(32) NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    record_count INT NOT NULL,
    fraud_count INT NOT NULL,
    legitimate_count INT NOT NULL,
    train_count INT NOT NULL,
    validation_count INT NOT NULL,
    test_count INT NOT NULL,
    created_by UUID NOT NULL,
    built_at TIMESTAMPTZ NOT NULL,
    invalidated_at TIMESTAMPTZ,
    invalidation_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_learning_datasets_creator
        FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_learning_dataset_version UNIQUE (dataset_version),
    CONSTRAINT uq_learning_dataset_checksum UNIQUE (checksum)
);

CREATE INDEX idx_learning_datasets_status
    ON learning_datasets (status, built_at DESC);

CREATE TABLE learning_dataset_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dataset_id UUID NOT NULL,
    feedback_id UUID NOT NULL,
    transaction_id UUID NOT NULL,
    label VARCHAR(16) NOT NULL,
    split_name VARCHAR(16) NOT NULL,
    decision_action VARCHAR(16),
    risk_score INT,
    correlation_contribution INT NOT NULL DEFAULT 0,
    recipient_reputation_points INT NOT NULL DEFAULT 0,
    finding_codes TEXT,
    feature_json TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_learning_items_dataset
        FOREIGN KEY (dataset_id) REFERENCES learning_datasets (id) ON DELETE CASCADE,
    CONSTRAINT fk_learning_items_feedback
        FOREIGN KEY (feedback_id) REFERENCES fraud_feedback (id) ON DELETE RESTRICT,
    CONSTRAINT uq_learning_item_dataset_feedback UNIQUE (dataset_id, feedback_id)
);

CREATE INDEX idx_learning_items_dataset
    ON learning_dataset_items (dataset_id, split_name);
CREATE INDEX idx_learning_items_feedback
    ON learning_dataset_items (feedback_id);

CREATE TABLE learning_models (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_version VARCHAR(64) NOT NULL,
    dataset_id UUID NOT NULL,
    model_type VARCHAR(40) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    threshold_score INT,
    precision_value DOUBLE PRECISION,
    recall_value DOUBLE PRECISION,
    f1_value DOUBLE PRECISION,
    accuracy_value DOUBLE PRECISION,
    validation_count INT NOT NULL DEFAULT 0,
    test_count INT NOT NULL DEFAULT 0,
    artifact_json TEXT NOT NULL,
    metrics_json TEXT,
    created_by UUID NOT NULL,
    approved_by UUID,
    activated_by UUID,
    approved_at TIMESTAMPTZ,
    activated_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_learning_models_dataset
        FOREIGN KEY (dataset_id) REFERENCES learning_datasets (id) ON DELETE RESTRICT,
    CONSTRAINT fk_learning_models_creator
        FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_learning_models_approver
        FOREIGN KEY (approved_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_learning_models_activator
        FOREIGN KEY (activated_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT uq_learning_model_version UNIQUE (model_version)
);

CREATE INDEX idx_learning_models_status
    ON learning_models (status, created_at DESC);
CREATE INDEX idx_learning_models_dataset
    ON learning_models (dataset_id);

CREATE TABLE learning_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dataset_id UUID,
    model_id UUID,
    actor_reference UUID NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    detail VARCHAR(1000),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_learning_events_dataset
        FOREIGN KEY (dataset_id) REFERENCES learning_datasets (id) ON DELETE SET NULL,
    CONSTRAINT fk_learning_events_model
        FOREIGN KEY (model_id) REFERENCES learning_models (id) ON DELETE SET NULL,
    CONSTRAINT fk_learning_events_actor
        FOREIGN KEY (actor_reference) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_learning_events_occurred
    ON learning_events (occurred_at DESC);

COMMENT ON TABLE learning_datasets IS
    'Immutable snapshots of verified feedback used for offline evaluation.';
COMMENT ON TABLE learning_models IS
    'Versioned rule-calibration candidates; activation is explicit and auditable.';
