-- V1 and V2 are already applied. Changing them invalidates Flyway checksums.
-- Move the support address here so existing databases can start and pick up the new email.

ALTER TABLE platform_settings
    ALTER COLUMN support_email SET DEFAULT 'supportlunaveta@gmail.com';

UPDATE platform_settings
SET support_email = 'supportlunaveta@gmail.com'
WHERE support_email = 'soporte@animalin.app';
