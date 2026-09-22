-- Specialty catalog is stored as a stable code on veterinarians.specialty.
-- Custom text for OTHER, and the specialty chosen on a pending invitation, live here.

ALTER TABLE veterinarians
    ADD COLUMN IF NOT EXISTS specialty_other VARCHAR(160);

ALTER TABLE staff_invitations
    ADD COLUMN IF NOT EXISTS specialty_code VARCHAR(40);

ALTER TABLE staff_invitations
    ADD COLUMN IF NOT EXISTS specialty_other VARCHAR(160);

ALTER TABLE staff_invitations
    ADD COLUMN IF NOT EXISTS branch_id BIGINT;

UPDATE veterinarians
SET specialty = 'GENERAL_MEDICINE',
    specialty_other = NULL
WHERE lower(specialty) IN ('medicina general', 'general medicine', 'general');

UPDATE veterinarians
SET specialty = 'INTERNAL_MEDICINE',
    specialty_other = NULL
WHERE lower(specialty) IN ('medicina interna', 'internal medicine');

UPDATE veterinarians
SET specialty = 'SURGERY',
    specialty_other = NULL
WHERE lower(specialty) IN ('cirugía', 'cirugia', 'surgery');

UPDATE veterinarians
SET specialty = 'DERMATOLOGY',
    specialty_other = NULL
WHERE lower(specialty) IN ('dermatología', 'dermatologia', 'dermatology');

UPDATE veterinarians
SET specialty = 'CARDIOLOGY',
    specialty_other = NULL
WHERE lower(specialty) IN ('cardiología', 'cardiologia', 'cardiology');

-- TENANT_OWNER: primary owner, subscription control.
-- TENANT_ADMIN: delegated clinic administrator.
UPDATE roles
SET name_es = 'Propietario de la veterinaria',
    name_en = 'Clinic owner',
    description_es = 'Propietario principal del tenant, con permisos máximos y control de la suscripción.',
    description_en = 'Primary tenant owner, with full permissions and subscription control.'
WHERE code = 'TENANT_OWNER';

UPDATE roles
SET name_es = 'Administrador de veterinaria',
    name_en = 'Clinic administrator',
    description_es = 'Administrador delegado de la veterinaria. No es el propietario de la suscripción.',
    description_en = 'Delegated clinic administrator. Does not own the subscription.'
WHERE code = 'TENANT_ADMIN';
