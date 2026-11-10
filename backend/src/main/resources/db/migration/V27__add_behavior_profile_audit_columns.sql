-- GuardPay V27: schema validation fixes for the behavioral tables.
--
-- 1. All five tables created in V24 omitted the audit columns required by
--    BaseEntity (created_at/updated_at). Every other GuardPay table has them,
--    so ddl-auto=validate rejected these tables at startup.
--
-- 2. V24 defined profile_metadata as JSONB while the entity maps it as TEXT.
--    The field is never written (always NULL), so converting the type is
--    non-destructive and makes the schema match the mapping.

ALTER TABLE user_behavior_profiles
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE user_recipient_behavior
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE user_hourly_behavior
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE user_geo_behavior
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE user_device_behavior
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE user_behavior_profiles
    ALTER COLUMN profile_metadata TYPE TEXT USING profile_metadata::text;

CREATE INDEX idx_user_behavior_profiles_created_at
    ON user_behavior_profiles (created_at);
