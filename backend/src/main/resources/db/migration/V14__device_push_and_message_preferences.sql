ALTER TABLE push_tokens
    ADD COLUMN tenant_id BIGINT REFERENCES tenants (id),
    ADD COLUMN installation_id VARCHAR(128),
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN updated_at TIMESTAMP,
    ADD COLUMN last_used_at TIMESTAMP,
    ADD COLUMN app_version VARCHAR(40),
    ADD COLUMN device_name VARCHAR(120);

UPDATE push_tokens
SET updated_at = created_at
WHERE updated_at IS NULL;

UPDATE push_tokens
SET last_used_at = created_at
WHERE last_used_at IS NULL;

ALTER TABLE push_tokens
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN last_used_at SET NOT NULL,
    ALTER COLUMN token TYPE VARCHAR(1024);

UPDATE push_tokens older
SET active = FALSE
WHERE active = TRUE
  AND EXISTS (
      SELECT 1
      FROM push_tokens newer
      WHERE newer.token = older.token
        AND newer.active = TRUE
        AND newer.id > older.id
  );

CREATE INDEX idx_push_tokens_user_active ON push_tokens (user_id, active);
CREATE INDEX idx_push_tokens_token ON push_tokens (token);
CREATE INDEX idx_push_tokens_installation ON push_tokens (installation_id);
CREATE UNIQUE INDEX uq_push_tokens_active_token ON push_tokens (token) WHERE active = TRUE;
CREATE UNIQUE INDEX uq_push_tokens_user_installation ON push_tokens (user_id, installation_id) WHERE installation_id IS NOT NULL;

ALTER TABLE users
    ADD COLUMN message_push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN message_preview_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN message_sound_enabled BOOLEAN NOT NULL DEFAULT TRUE;
