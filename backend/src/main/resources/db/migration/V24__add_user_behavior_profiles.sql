-- GuardPay V24: per-user adaptive behavioral profiling.
--
-- Stores a learned behavioral baseline per user so the risk engine can
-- detect personalized deviations (amount, time, recipient, device, geo,
-- frequency) instead of relying only on universal thresholds.
--
-- Only COMPLETED transactions contribute to legitimate spending baselines.
-- Cancelled, failed, and reversed payments are excluded unless a separate
-- analysis specifically requires them.

CREATE TABLE user_behavior_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    profile_version INTEGER NOT NULL DEFAULT 1,
    profile_status VARCHAR(32) NOT NULL DEFAULT 'INSUFFICIENT_DATA',
    transaction_count INTEGER NOT NULL DEFAULT 0,
    first_observed_at TIMESTAMPTZ,
    last_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    data_quality_score NUMERIC(3, 2) NOT NULL DEFAULT 0.00,
    amount_min NUMERIC(19, 4),
    amount_max NUMERIC(19, 4),
    amount_mean NUMERIC(19, 4),
    amount_median NUMERIC(19, 4),
    amount_stddev NUMERIC(19, 4),
    amount_mad NUMERIC(19, 4),
    amount_p90 NUMERIC(19, 4),
    amount_p95 NUMERIC(19, 4),
    frequency_per_hour NUMERIC(9, 4),
    frequency_per_day NUMERIC(9, 4),
    frequency_per_week NUMERIC(9, 4),
    avg_interval_minutes NUMERIC(12, 4),
    peak_hour INTEGER,
    weekend_payment_ratio NUMERIC(5, 4),
    night_payment_ratio NUMERIC(5, 4),
    unique_recipient_count INTEGER NOT NULL DEFAULT 0,
    established_recipient_count INTEGER NOT NULL DEFAULT 0,
    new_recipient_ratio NUMERIC(5, 4),
    currency VARCHAR(3),
    timezone VARCHAR(64),
    device_count INTEGER NOT NULL DEFAULT 0,
    geo_country_count INTEGER NOT NULL DEFAULT 0,
    profile_metadata JSONB,
    CONSTRAINT fk_user_behavior_profiles_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_user_behavior_profiles_user UNIQUE (user_id),
    CONSTRAINT chk_user_behavior_profiles_status
        CHECK (profile_status IN ('INSUFFICIENT_DATA','INITIALIZING','ESTABLISHED','DEGRADED')),
    CONSTRAINT chk_data_quality_score
        CHECK (data_quality_score >= 0 AND data_quality_score <= 1)
);
CREATE INDEX idx_user_behavior_profiles_user_id ON user_behavior_profiles (user_id);
CREATE INDEX idx_user_behavior_profiles_status ON user_behavior_profiles (profile_status);
CREATE INDEX idx_user_behavior_profiles_last_updated ON user_behavior_profiles (last_updated_at);

-- Per-recipient behavioral stats for the owning user.
CREATE TABLE user_recipient_behavior (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    recipient_id UUID NOT NULL,
    transaction_count INTEGER NOT NULL DEFAULT 0,
    total_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    avg_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    median_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    min_amount NUMERIC(19, 4),
    max_amount NUMERIC(19, 4),
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_user_recipient_behavior_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_recipient_behavior_recipient
        FOREIGN KEY (recipient_id) REFERENCES recipients (id) ON DELETE CASCADE,
    CONSTRAINT uq_user_recipient_behavior UNIQUE (user_id, recipient_id)
);
CREATE INDEX idx_user_recipient_behavior_user ON user_recipient_behavior (user_id);
CREATE INDEX idx_user_recipient_behavior_recipient ON user_recipient_behavior (recipient_id);

-- Per-hour payment distribution (0-23) for time-of-day anomaly detection.
CREATE TABLE user_hourly_behavior (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    hour_of_day INTEGER NOT NULL,
    transaction_count INTEGER NOT NULL DEFAULT 0,
    total_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    last_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_user_hourly_behavior_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_user_hourly_behavior UNIQUE (user_id, hour_of_day),
    CONSTRAINT chk_hour_of_day CHECK (hour_of_day >= 0 AND hour_of_day <= 23)
);
CREATE INDEX idx_user_hourly_behavior_user ON user_hourly_behavior (user_id);

-- Per-geo-country behavior for location anomaly detection.
CREATE TABLE user_geo_behavior (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    geo_country VARCHAR(2) NOT NULL,
    transaction_count INTEGER NOT NULL DEFAULT 0,
    total_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_user_geo_behavior_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_user_geo_behavior UNIQUE (user_id, geo_country)
);
CREATE INDEX idx_user_geo_behavior_user ON user_geo_behavior (user_id);

-- Per-device behavior for device anomaly detection.
CREATE TABLE user_device_behavior (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    device_id UUID,
    device_fingerprint VARCHAR(128),
    transaction_count INTEGER NOT NULL DEFAULT 0,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_user_device_behavior_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_user_device_behavior UNIQUE (user_id, device_id, device_fingerprint)
);
CREATE INDEX idx_user_device_behavior_user ON user_device_behavior (user_id);
