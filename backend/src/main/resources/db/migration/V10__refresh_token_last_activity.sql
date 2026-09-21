ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS last_activity_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_revoked ON refresh_tokens (user_id, revoked);
