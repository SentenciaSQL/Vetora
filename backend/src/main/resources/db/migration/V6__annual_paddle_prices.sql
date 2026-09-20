-- Annual catalog amounts for existing plans and contracted Paddle identifiers on subscriptions.
-- paddle_annual_price_id already exists and remains nullable so deploys succeed before Sandbox IDs are pasted.

ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS paddle_product_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS paddle_price_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_subscriptions_paddle_price ON subscriptions (paddle_price_id);

UPDATE plans
SET monthly_price = 19.00,
    annual_price = 190.00,
    currency = 'USD',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'BASIC';

UPDATE plans
SET monthly_price = 39.00,
    annual_price = 390.00,
    currency = 'USD',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'PROFESSIONAL';

UPDATE plans
SET monthly_price = 69.00,
    annual_price = 690.00,
    currency = 'USD',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'PREMIUM';
