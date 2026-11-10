-- GuardPay V29: recipient reputation and the global fraud intelligence network.
--
-- Identifiers are stored only as keyed HMAC-SHA-256 digests. Raw email
-- addresses, PayPal account ids, and report text never appear here.
--
-- The intelligence tables are evidence stores: the deterministic risk engine
-- remains the only decision authority.

ALTER TABLE users
    ADD COLUMN admin BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE risk_assessments
    ADD COLUMN recipient_reputation TEXT,
    ADD COLUMN recipient_reputation_points INT NOT NULL DEFAULT 0;

CREATE TABLE global_recipient_entities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    reputation_status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    reputation_category VARCHAR(32) NOT NULL DEFAULT 'NO_DATA',
    reputation_score INT NOT NULL DEFAULT 0,
    identity_confidence VARCHAR(16) NOT NULL DEFAULT 'LOW',
    evidence_count INT NOT NULL DEFAULT 0,
    report_count INT NOT NULL DEFAULT 0,
    distinct_reporter_count INT NOT NULL DEFAULT 0,
    last_evidence_at TIMESTAMPTZ,
    last_calculated_at TIMESTAMPTZ,
    calculation_version VARCHAR(16),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_global_recipient_entities_status
    ON global_recipient_entities (reputation_status);
CREATE INDEX idx_global_recipient_entities_category
    ON global_recipient_entities (reputation_category);

CREATE TABLE recipient_identifiers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_entity_id UUID NOT NULL,
    identifier_type VARCHAR(32) NOT NULL,
    identifier_hmac VARCHAR(64) NOT NULL,
    hmac_key_version INT NOT NULL,
    normalization_version VARCHAR(32) NOT NULL,
    verification_status VARCHAR(16) NOT NULL DEFAULT 'UNVERIFIED',
    identity_confidence VARCHAR(16) NOT NULL DEFAULT 'LOW',
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_recipient_identifiers_entity
        FOREIGN KEY (recipient_entity_id) REFERENCES global_recipient_entities (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_recipient_identifier_hmac UNIQUE (identifier_type, identifier_hmac)
);

CREATE INDEX idx_recipient_identifiers_entity
    ON recipient_identifiers (recipient_entity_id);
CREATE INDEX idx_recipient_identifiers_hmac
    ON recipient_identifiers (identifier_hmac);

CREATE TABLE fraud_intelligence_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_entity_id UUID NOT NULL,
    reporter_reference UUID NOT NULL,
    source VARCHAR(16) NOT NULL DEFAULT 'USER',
    report_status VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',
    description TEXT,
    evidence_fingerprint VARCHAR(64) NOT NULL,
    related_transaction_id UUID,
    incident_at TIMESTAMPTZ,
    review_note TEXT,
    reviewed_at TIMESTAMPTZ,
    reviewer_reference UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_fraud_reports_entity
        FOREIGN KEY (recipient_entity_id) REFERENCES global_recipient_entities (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_fraud_report_dedup
        UNIQUE (recipient_entity_id, reporter_reference, evidence_fingerprint)
);

CREATE INDEX idx_fraud_reports_entity
    ON fraud_intelligence_reports (recipient_entity_id);
CREATE INDEX idx_fraud_reports_reporter
    ON fraud_intelligence_reports (reporter_reference, created_at);
CREATE INDEX idx_fraud_reports_status
    ON fraud_intelligence_reports (report_status);

CREATE TABLE recipient_fraud_evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_entity_id UUID NOT NULL,
    evidence_type VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    verification_status VARCHAR(16) NOT NULL DEFAULT 'UNVERIFIED',
    confidence NUMERIC(5, 4) NOT NULL DEFAULT 0.5,
    evidence_fingerprint VARCHAR(64) NOT NULL,
    reporter_group_key VARCHAR(64),
    related_transaction_id UUID,
    details TEXT,
    observed_at TIMESTAMPTZ NOT NULL,
    last_observed_at TIMESTAMPTZ NOT NULL,
    report_count INT NOT NULL DEFAULT 1,
    revoked_at TIMESTAMPTZ,
    revoked_reason VARCHAR(255),
    source_report_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_recipient_evidence_entity
        FOREIGN KEY (recipient_entity_id) REFERENCES global_recipient_entities (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_recipient_evidence_report
        FOREIGN KEY (source_report_id) REFERENCES fraud_intelligence_reports (id)
        ON DELETE SET NULL,
    CONSTRAINT uq_recipient_evidence_fingerprint
        UNIQUE (recipient_entity_id, evidence_fingerprint)
);

CREATE INDEX idx_recipient_evidence_entity
    ON recipient_fraud_evidence (recipient_entity_id);
CREATE INDEX idx_recipient_evidence_observed
    ON recipient_fraud_evidence (observed_at);

CREATE TABLE recipient_reputation_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_entity_id UUID NOT NULL,
    score INT NOT NULL,
    category VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    evidence_count INT NOT NULL,
    report_count INT NOT NULL,
    distinct_reporter_count INT NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL,
    calculation_version VARCHAR(16) NOT NULL,
    contributing_evidence TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_reputation_snapshots_entity
        FOREIGN KEY (recipient_entity_id) REFERENCES global_recipient_entities (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_reputation_snapshots_entity
    ON recipient_reputation_snapshots (recipient_entity_id, calculated_at DESC);

CREATE TABLE recipient_review_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_entity_id UUID NOT NULL,
    actor_reference UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_review_events_entity
        FOREIGN KEY (recipient_entity_id) REFERENCES global_recipient_entities (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_review_events_entity
    ON recipient_review_events (recipient_entity_id, created_at DESC);

CREATE TABLE recipient_relationships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_reference UUID NOT NULL,
    recipient_entity_id UUID NOT NULL,
    relationship_type VARCHAR(32) NOT NULL,
    confidence VARCHAR(16) NOT NULL,
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    interaction_count INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_relationships_entity
        FOREIGN KEY (recipient_entity_id) REFERENCES global_recipient_entities (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_recipient_relationship UNIQUE (owner_reference, recipient_entity_id)
);

CREATE INDEX idx_relationships_owner
    ON recipient_relationships (owner_reference);

CREATE TABLE recipient_intelligence_access_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_entity_id UUID,
    actor_reference UUID NOT NULL,
    access_type VARCHAR(32) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    accessed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_access_logs_entity
        FOREIGN KEY (recipient_entity_id) REFERENCES global_recipient_entities (id)
        ON DELETE SET NULL
);

CREATE INDEX idx_access_logs_actor
    ON recipient_intelligence_access_logs (actor_reference, accessed_at DESC);
