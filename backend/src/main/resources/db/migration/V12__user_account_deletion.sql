ALTER TABLE users
    ADD COLUMN deletion_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN anonymized_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_users_deletion_status ON users (deletion_status);
