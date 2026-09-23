-- One-time cleanup. Flyway records this script in flyway_schema_history and does not run it again.
-- Tenant identity is tenants.slug, matched exactly. User identity is users.email, matched with LOWER(email).
-- No foreign keys are altered. The only existing ON DELETE CASCADE (branch_hour_exception_intervals) is left as-is.
-- Global rows are kept: plans, roles, permissions, platform_settings, vaccine_catalog with tenant_id IS NULL,
-- and billing_events (webhook ledger with no tenant_id or user_id foreign key).
-- Users who belong to these clinics but whose email is not listed below are not deleted.
-- Nullable foreign keys from other clinics that point at rows being removed are set to NULL.
-- Non-nullable foreign keys that reference an explicit user are removed only for that user.

CREATE TEMP TABLE cleanup_tenant_ids AS
SELECT id
FROM tenants
WHERE slug IN (
    'vet-vir',
    'vet',
    'patitas-callejeras',
    'rambo-vet',
    'vet2',
    'test',
    'luinni'
);

CREATE TEMP TABLE cleanup_user_ids AS
SELECT id
FROM users
WHERE LOWER(email) IN (
    'vet22@gmail.com',
    'j.ulloa@mail.com',
    'lavirgen@gmail.com',
    'andresfrias00014@gmail.com',
    'andresfrias0014@gmail.com',
    'vet1@gmail.com',
    'testuser123@gmail.com'
);

CREATE TEMP TABLE cleanup_veterinarian_ids AS
SELECT id
FROM veterinarians
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids)
   OR user_id IN (SELECT id FROM cleanup_user_ids);

CREATE TEMP TABLE cleanup_branch_ids AS
SELECT id
FROM branches
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

CREATE TEMP TABLE cleanup_owner_ids AS
SELECT id
FROM owners
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

CREATE TEMP TABLE cleanup_pet_ids AS
SELECT id
FROM pets
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

CREATE TEMP TABLE cleanup_service_ids AS
SELECT id
FROM clinic_services
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

CREATE TEMP TABLE cleanup_appointment_ids AS
SELECT id
FROM appointments
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

CREATE TEMP TABLE cleanup_consultation_ids AS
SELECT id
FROM consultations
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

CREATE TEMP TABLE cleanup_medication_ids AS
SELECT id
FROM medications
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

CREATE TEMP TABLE cleanup_treatment_ids AS
SELECT id
FROM treatments
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

CREATE TEMP TABLE cleanup_prescription_ids AS
SELECT id
FROM prescriptions
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

CREATE TEMP TABLE cleanup_vaccine_ids AS
SELECT id
FROM vaccine_catalog
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

CREATE TEMP TABLE cleanup_conversation_ids AS
SELECT id
FROM conversations
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

CREATE TEMP TABLE cleanup_message_ids AS
SELECT id
FROM messages
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids)
   OR sender_id IN (SELECT id FROM cleanup_user_ids);

CREATE TEMP TABLE cleanup_file_ids AS
SELECT id
FROM stored_files
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

-- Detach nullable references held by clinics that stay, so their clinical rows are preserved.

UPDATE appointments
SET veterinarian_id = NULL
WHERE veterinarian_id IN (SELECT id FROM cleanup_veterinarian_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids);

UPDATE appointments
SET service_id = NULL
WHERE service_id IN (SELECT id FROM cleanup_service_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids);

UPDATE appointments
SET branch_id = NULL
WHERE branch_id IN (SELECT id FROM cleanup_branch_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids);

UPDATE consultations
SET veterinarian_id = NULL
WHERE veterinarian_id IN (SELECT id FROM cleanup_veterinarian_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids);

UPDATE consultations
SET branch_id = NULL
WHERE branch_id IN (SELECT id FROM cleanup_branch_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids);

UPDATE consultations
SET appointment_id = NULL
WHERE appointment_id IN (SELECT id FROM cleanup_appointment_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids);

UPDATE pets
SET primary_veterinarian_id = NULL
WHERE primary_veterinarian_id IN (SELECT id FROM cleanup_veterinarian_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids);

UPDATE pets
SET branch_id = NULL
WHERE branch_id IN (SELECT id FROM cleanup_branch_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids);

UPDATE treatments
SET veterinarian_id = NULL
WHERE veterinarian_id IN (SELECT id FROM cleanup_veterinarian_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids);

UPDATE treatments
SET consultation_id = NULL
WHERE consultation_id IN (SELECT id FROM cleanup_consultation_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids);

UPDATE prescriptions
SET veterinarian_id = NULL
WHERE veterinarian_id IN (SELECT id FROM cleanup_veterinarian_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids);

UPDATE prescriptions
SET consultation_id = NULL
WHERE consultation_id IN (SELECT id FROM cleanup_consultation_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids);

UPDATE vaccinations
SET veterinarian_id = NULL
WHERE veterinarian_id IN (SELECT id FROM cleanup_veterinarian_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids);

UPDATE vaccinations
SET vaccine_id = NULL
WHERE vaccine_id IN (SELECT id FROM cleanup_vaccine_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids);

UPDATE procedures
SET veterinarian_id = NULL
WHERE veterinarian_id IN (SELECT id FROM cleanup_veterinarian_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids);

UPDATE procedures
SET consultation_id = NULL
WHERE consultation_id IN (SELECT id FROM cleanup_consultation_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids);

UPDATE surgeries
SET veterinarian_id = NULL
WHERE veterinarian_id IN (SELECT id FROM cleanup_veterinarian_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids);

UPDATE laboratory_results
SET veterinarian_id = NULL
WHERE veterinarian_id IN (SELECT id FROM cleanup_veterinarian_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids);

UPDATE laboratory_results
SET consultation_id = NULL
WHERE consultation_id IN (SELECT id FROM cleanup_consultation_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids);

UPDATE vital_signs
SET consultation_id = NULL
WHERE consultation_id IN (SELECT id FROM cleanup_consultation_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids);

UPDATE treatment_items
SET medication_id = NULL
WHERE medication_id IN (SELECT id FROM cleanup_medication_ids)
  AND treatment_id NOT IN (SELECT id FROM cleanup_treatment_ids);

UPDATE prescription_items
SET medication_id = NULL
WHERE medication_id IN (SELECT id FROM cleanup_medication_ids)
  AND prescription_id NOT IN (SELECT id FROM cleanup_prescription_ids);

UPDATE schedule_blocks
SET veterinarian_id = NULL
WHERE veterinarian_id IN (SELECT id FROM cleanup_veterinarian_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids);

UPDATE schedule_blocks
SET branch_id = NULL
WHERE branch_id IN (SELECT id FROM cleanup_branch_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids);

UPDATE veterinarians
SET branch_id = NULL
WHERE branch_id IN (SELECT id FROM cleanup_branch_ids)
  AND id NOT IN (SELECT id FROM cleanup_veterinarian_ids);

UPDATE employees
SET branch_id = NULL
WHERE branch_id IN (SELECT id FROM cleanup_branch_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids)
  AND user_id NOT IN (SELECT id FROM cleanup_user_ids);

UPDATE owners
SET user_id = NULL
WHERE user_id IN (SELECT id FROM cleanup_user_ids)
  AND id NOT IN (SELECT id FROM cleanup_owner_ids);

UPDATE conversations
SET owner_id = NULL
WHERE owner_id IN (SELECT id FROM cleanup_owner_ids)
  AND id NOT IN (SELECT id FROM cleanup_conversation_ids);

UPDATE conversations
SET pet_id = NULL
WHERE pet_id IN (SELECT id FROM cleanup_pet_ids)
  AND id NOT IN (SELECT id FROM cleanup_conversation_ids);

UPDATE messages
SET pet_id = NULL
WHERE pet_id IN (SELECT id FROM cleanup_pet_ids)
  AND id NOT IN (SELECT id FROM cleanup_message_ids);

UPDATE messages
SET consultation_id = NULL
WHERE consultation_id IN (SELECT id FROM cleanup_consultation_ids)
  AND id NOT IN (SELECT id FROM cleanup_message_ids);

UPDATE documents
SET pet_id = NULL
WHERE pet_id IN (SELECT id FROM cleanup_pet_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids);

UPDATE documents
SET owner_id = NULL
WHERE owner_id IN (SELECT id FROM cleanup_owner_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids);

UPDATE documents
SET consultation_id = NULL
WHERE consultation_id IN (SELECT id FROM cleanup_consultation_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids);

UPDATE documents
SET file_id = NULL
WHERE file_id IN (SELECT id FROM cleanup_file_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids);

UPDATE documents
SET uploaded_by = NULL
WHERE uploaded_by IN (SELECT id FROM cleanup_user_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids);

UPDATE message_attachments
SET file_id = NULL
WHERE file_id IN (SELECT id FROM cleanup_file_ids)
  AND message_id NOT IN (SELECT id FROM cleanup_message_ids);

UPDATE conversation_read_states
SET last_read_message_id = NULL
WHERE last_read_message_id IN (SELECT id FROM cleanup_message_ids);

UPDATE staff_invitations
SET invited_by = NULL
WHERE invited_by IN (SELECT id FROM cleanup_user_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids);

UPDATE staff_invitations
SET accepted_by = NULL
WHERE accepted_by IN (SELECT id FROM cleanup_user_ids)
  AND tenant_id NOT IN (SELECT id FROM cleanup_tenant_ids);

UPDATE clinic_signups
SET tenant_id = NULL
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids)
  AND user_id NOT IN (SELECT id FROM cleanup_user_ids);

-- Children of the target clinics, then profiles that still block deletion of the listed users.

DELETE FROM conversation_read_states
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids)
   OR user_id IN (SELECT id FROM cleanup_user_ids)
   OR conversation_id IN (SELECT id FROM cleanup_conversation_ids);

DELETE FROM message_attachments
WHERE message_id IN (SELECT id FROM cleanup_message_ids);

DELETE FROM messages
WHERE id IN (SELECT id FROM cleanup_message_ids);

DELETE FROM conversation_participants
WHERE conversation_id IN (SELECT id FROM cleanup_conversation_ids)
   OR user_id IN (SELECT id FROM cleanup_user_ids);

DELETE FROM conversations
WHERE id IN (SELECT id FROM cleanup_conversation_ids);

DELETE FROM documents
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

DELETE FROM treatment_items
WHERE treatment_id IN (SELECT id FROM cleanup_treatment_ids);

DELETE FROM prescription_items
WHERE prescription_id IN (SELECT id FROM cleanup_prescription_ids);

DELETE FROM diagnoses
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

DELETE FROM vital_signs
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

DELETE FROM treatments
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

DELETE FROM prescriptions
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

DELETE FROM vaccinations
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

DELETE FROM procedures
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

DELETE FROM surgeries
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

DELETE FROM laboratory_results
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

DELETE FROM consultations
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

DELETE FROM appointments
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

DELETE FROM pet_weight_logs
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

DELETE FROM pets
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

DELETE FROM medications
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

DELETE FROM vaccine_catalog
WHERE id IN (SELECT id FROM cleanup_vaccine_ids);

DELETE FROM clinic_services
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

DELETE FROM veterinarian_schedules
WHERE veterinarian_id IN (SELECT id FROM cleanup_veterinarian_ids);

DELETE FROM veterinarian_time_off
WHERE veterinarian_id IN (SELECT id FROM cleanup_veterinarian_ids);

DELETE FROM schedule_blocks
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

DELETE FROM veterinarians
WHERE id IN (SELECT id FROM cleanup_veterinarian_ids);

DELETE FROM employees
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids)
   OR user_id IN (SELECT id FROM cleanup_user_ids);

DELETE FROM owners
WHERE id IN (SELECT id FROM cleanup_owner_ids);

DELETE FROM branch_hour_exception_intervals
WHERE exception_id IN (
    SELECT id
    FROM branch_hour_exceptions
    WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids)
);

DELETE FROM branch_hour_exceptions
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

DELETE FROM branch_hours
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

DELETE FROM branches
WHERE id IN (SELECT id FROM cleanup_branch_ids);

DELETE FROM stored_files
WHERE id IN (SELECT id FROM cleanup_file_ids);

DELETE FROM stored_files AS stored_file
WHERE stored_file.uploaded_by IN (SELECT id FROM cleanup_user_ids)
  AND stored_file.tenant_id IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM documents AS document WHERE document.file_id = stored_file.id
  )
  AND NOT EXISTS (
      SELECT 1 FROM message_attachments AS attachment WHERE attachment.file_id = stored_file.id
  );

UPDATE stored_files
SET uploaded_by = NULL
WHERE uploaded_by IN (SELECT id FROM cleanup_user_ids);

DELETE FROM notifications
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids)
   OR user_id IN (SELECT id FROM cleanup_user_ids);

DELETE FROM push_tokens
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids)
   OR user_id IN (SELECT id FROM cleanup_user_ids);

DELETE FROM refresh_tokens
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids)
   OR user_id IN (SELECT id FROM cleanup_user_ids);

DELETE FROM password_reset_tokens
WHERE user_id IN (SELECT id FROM cleanup_user_ids);

DELETE FROM email_verification_tokens
WHERE user_id IN (SELECT id FROM cleanup_user_ids);

DELETE FROM account_deletion_requests
WHERE user_id IN (SELECT id FROM cleanup_user_ids);

DELETE FROM staff_invitations
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

DELETE FROM clinic_signups
WHERE user_id IN (SELECT id FROM cleanup_user_ids);

DELETE FROM tenant_memberships
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids)
   OR user_id IN (SELECT id FROM cleanup_user_ids);

DELETE FROM user_roles
WHERE user_id IN (SELECT id FROM cleanup_user_ids);

DELETE FROM subscriptions
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

DELETE FROM tenant_settings
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

DELETE FROM audit_logs
WHERE tenant_id IN (SELECT id FROM cleanup_tenant_ids);

DELETE FROM audit_logs
WHERE user_id IN (SELECT id FROM cleanup_user_ids)
  AND tenant_id IS NULL;

DELETE FROM tenants
WHERE id IN (SELECT id FROM cleanup_tenant_ids);

DELETE FROM users
WHERE id IN (SELECT id FROM cleanup_user_ids);
