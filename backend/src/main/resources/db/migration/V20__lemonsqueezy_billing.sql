-- Lemon Squeezy subscription identifiers on the existing tenant subscription,
-- plus a wider idempotency key so Lemon event keys fit in billing_events.

ALTER TABLE subscriptions
    ADD COLUMN ls_subscription_id VARCHAR(64),
    ADD COLUMN ls_customer_id VARCHAR(64),
    ADD COLUMN ls_order_id VARCHAR(64),
    ADD COLUMN ls_order_item_id VARCHAR(64),
    ADD COLUMN ls_product_id VARCHAR(64),
    ADD COLUMN ls_variant_id VARCHAR(64),
    ADD COLUMN ls_variant_name VARCHAR(180),
    ADD COLUMN ls_product_name VARCHAR(180),
    ADD COLUMN ls_product_sku VARCHAR(120),
    ADD COLUMN ls_status VARCHAR(40),
    ADD COLUMN trial_ends_at TIMESTAMP,
    ADD COLUMN renews_at TIMESTAMP,
    ADD COLUMN ends_at TIMESTAMP,
    ADD COLUMN ls_cancelled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN ls_paused BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN ls_test_mode BOOLEAN,
    ADD COLUMN ls_refunded_at TIMESTAMP,
    ADD COLUMN ls_created_at TIMESTAMP,
    ADD COLUMN ls_updated_at TIMESTAMP,
    ADD COLUMN user_id BIGINT REFERENCES users (id);

CREATE UNIQUE INDEX uq_subscriptions_ls_subscription
    ON subscriptions (ls_subscription_id, ls_test_mode)
    WHERE ls_subscription_id IS NOT NULL;

CREATE INDEX idx_subscriptions_ls_customer ON subscriptions (ls_customer_id, ls_test_mode);
CREATE INDEX idx_subscriptions_ls_order ON subscriptions (ls_order_id, ls_test_mode);
CREATE INDEX idx_subscriptions_ls_user ON subscriptions (user_id);

ALTER TABLE billing_events
    ALTER COLUMN paddle_event_id TYPE VARCHAR(180);
