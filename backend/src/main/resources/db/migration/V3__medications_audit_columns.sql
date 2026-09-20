-- Medication extends TenantEntity/BaseEntity and needs the same audit columns
-- as the rest of the tenant-scoped tables.
ALTER TABLE medications
    ADD COLUMN created_by VARCHAR(120),
    ADD COLUMN updated_by VARCHAR(120);
