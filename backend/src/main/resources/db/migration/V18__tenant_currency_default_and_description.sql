ALTER TABLE tenants
    ALTER COLUMN currency SET DEFAULT 'DOP';

ALTER TABLE tenants
    ALTER COLUMN timezone SET DEFAULT 'America/Santo_Domingo';

ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS description VARCHAR(800);
