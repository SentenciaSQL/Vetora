DROP INDEX IF EXISTS idx_plans_paddle_product;
DROP INDEX IF EXISTS idx_plans_paddle_monthly_price;
DROP INDEX IF EXISTS idx_plans_paddle_annual_price;
DROP INDEX IF EXISTS idx_plans_sync_status;

ALTER TABLE plans
    DROP COLUMN IF EXISTS paddle_product_id,
    DROP COLUMN IF EXISTS paddle_monthly_price_id,
    DROP COLUMN IF EXISTS paddle_annual_price_id,
    DROP COLUMN IF EXISTS paddle_monthly_price_status,
    DROP COLUMN IF EXISTS paddle_annual_price_status,
    DROP COLUMN IF EXISTS paddle_last_synced_at,
    DROP COLUMN IF EXISTS paddle_sync_status;

DROP INDEX IF EXISTS uq_subscriptions_paddle_subscription_id;
DROP INDEX IF EXISTS idx_subscriptions_paddle_customer;
DROP INDEX IF EXISTS idx_subscriptions_paddle_price;

ALTER TABLE subscriptions
    DROP COLUMN IF EXISTS paddle_customer_id,
    DROP COLUMN IF EXISTS paddle_subscription_id,
    DROP COLUMN IF EXISTS paddle_transaction_id,
    DROP COLUMN IF EXISTS paddle_product_id,
    DROP COLUMN IF EXISTS paddle_price_id,
    DROP COLUMN IF EXISTS pending_change_paddle_updated_at;

ALTER TABLE billing_events RENAME COLUMN paddle_event_id TO event_key;
ALTER INDEX uq_billing_events_paddle_event_id RENAME TO uq_billing_events_event_key;

UPDATE plans SET monthly_price = 19.00 WHERE code = 'BASIC';
UPDATE plans SET monthly_price = 39.00 WHERE code = 'PROFESSIONAL';
UPDATE plans SET monthly_price = 69.00 WHERE code = 'PREMIUM';
