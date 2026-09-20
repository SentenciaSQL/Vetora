-- Plan Paddle sync metadata. Limits already live on plans; this stores last validated catalog state.

ALTER TABLE plans
    ADD COLUMN IF NOT EXISTS paddle_monthly_price_status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS paddle_annual_price_status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS paddle_last_synced_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS paddle_sync_status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN';

CREATE INDEX IF NOT EXISTS idx_plans_sync_status ON plans (paddle_sync_status);
