-- Locally scheduled plan downgrades. Paddle applies item changes immediately, so LunaVeta
-- stores the pending plan and applies it at the next billing cycle.

ALTER TABLE subscriptions
    ADD COLUMN pending_plan_id BIGINT REFERENCES plans (id),
    ADD COLUMN pending_price_id VARCHAR(64),
    ADD COLUMN pending_billing_interval VARCHAR(20),
    ADD COLUMN pending_change_effective_at TIMESTAMP,
    ADD COLUMN pending_change_created_at TIMESTAMP,
    ADD COLUMN pending_change_status VARCHAR(20),
    ADD COLUMN pending_change_paddle_updated_at TIMESTAMP;

CREATE INDEX idx_subscriptions_pending_change
    ON subscriptions (pending_change_effective_at)
    WHERE pending_plan_id IS NOT NULL;
