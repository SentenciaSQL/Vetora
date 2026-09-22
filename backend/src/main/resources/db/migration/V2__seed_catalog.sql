INSERT INTO plans (code, name_es, name_en, description_es, description_en, max_users, max_veterinarians, max_branches, max_storage_mb, max_messages_month, reports_enabled, messaging_enabled, laboratory_enabled, monthly_price, active)
VALUES
    ('BASIC', 'Básico', 'Basic', 'Ideal para clínicas pequeñas que empiezan a digitalizarse.', 'Ideal for small clinics starting to go digital.', 5, 2, 1, 1024, 200, TRUE, TRUE, FALSE, 29.00, TRUE),
    ('PROFESSIONAL', 'Profesional', 'Professional', 'Para clínicas en crecimiento con varias sucursales y equipo clínico.', 'For growing clinics with multiple branches and clinical staff.', 20, 8, 3, 10240, 2000, TRUE, TRUE, TRUE, 79.00, TRUE),
    ('PREMIUM', 'Premium', 'Premium', 'Capacidad completa, reportes avanzados y almacenamiento amplio.', 'Full capacity, advanced reports and generous storage.', 100, 40, 15, 102400, 20000, TRUE, TRUE, TRUE, 149.00, TRUE);

INSERT INTO roles (code, name_es, name_en, description_es, description_en, system_role)
VALUES
    ('SUPER_ADMIN', 'Super administrador', 'Super administrator', 'Administra la plataforma SaaS.', 'Manages the SaaS platform.', TRUE),
    ('TENANT_ADMIN', 'Administrador de clínica', 'Clinic administrator', 'Administra una veterinaria.', 'Manages a veterinary clinic.', TRUE),
    ('VETERINARIAN', 'Veterinario', 'Veterinarian', 'Atiende pacientes y gestiona expedientes.', 'Treats patients and manages records.', TRUE),
    ('RECEPTIONIST', 'Recepcionista', 'Receptionist', 'Gestiona agenda, propietarios y mascotas.', 'Manages schedule, owners and pets.', TRUE),
    ('PET_OWNER', 'Propietario', 'Pet owner', 'Consulta información de sus mascotas.', 'Views information about their pets.', TRUE);

INSERT INTO permissions (code, name_es, name_en, module) VALUES
    ('PET_READ', 'Ver mascotas', 'View pets', 'pets'),
    ('PET_CREATE', 'Crear mascotas', 'Create pets', 'pets'),
    ('PET_UPDATE', 'Editar mascotas', 'Update pets', 'pets'),
    ('OWNER_READ', 'Ver propietarios', 'View owners', 'owners'),
    ('OWNER_CREATE', 'Crear propietarios', 'Create owners', 'owners'),
    ('OWNER_UPDATE', 'Editar propietarios', 'Update owners', 'owners'),
    ('APPOINTMENT_READ', 'Ver citas', 'View appointments', 'appointments'),
    ('APPOINTMENT_CREATE', 'Crear citas', 'Create appointments', 'appointments'),
    ('APPOINTMENT_UPDATE', 'Editar citas', 'Update appointments', 'appointments'),
    ('APPOINTMENT_CANCEL', 'Cancelar citas', 'Cancel appointments', 'appointments'),
    ('MEDICAL_RECORD_READ', 'Ver expediente', 'View medical records', 'medical'),
    ('MEDICAL_RECORD_WRITE', 'Editar expediente', 'Write medical records', 'medical'),
    ('PRESCRIPTION_READ', 'Ver recetas', 'View prescriptions', 'prescriptions'),
    ('PRESCRIPTION_CREATE', 'Crear recetas', 'Create prescriptions', 'prescriptions'),
    ('REPORT_VIEW', 'Ver reportes', 'View reports', 'reports'),
    ('SETTINGS_READ', 'Ver configuración', 'View settings', 'settings'),
    ('SETTINGS_UPDATE', 'Editar configuración', 'Update settings', 'settings'),
    ('BRANDING_UPDATE', 'Editar marca', 'Update branding', 'settings'),
    ('STAFF_MANAGE', 'Gestionar personal', 'Manage staff', 'staff'),
    ('BRANCH_MANAGE', 'Gestionar sucursales', 'Manage branches', 'branches'),
    ('SERVICE_MANAGE', 'Gestionar servicios', 'Manage services', 'services'),
    ('MESSAGE_READ', 'Ver mensajes', 'View messages', 'messaging'),
    ('MESSAGE_WRITE', 'Enviar mensajes', 'Send messages', 'messaging'),
    ('DOCUMENT_READ', 'Ver documentos', 'View documents', 'documents'),
    ('DOCUMENT_WRITE', 'Subir documentos', 'Upload documents', 'documents'),
    ('NOTIFICATION_MANAGE', 'Gestionar notificaciones', 'Manage notifications', 'notifications'),
    ('PLATFORM_ADMIN', 'Administrar plataforma', 'Administer platform', 'admin');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON r.code = 'SUPER_ADMIN' AND p.code = 'PLATFORM_ADMIN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
    ON r.code = 'TENANT_ADMIN'
   AND p.code IN (
        'PET_READ','PET_CREATE','PET_UPDATE',
        'OWNER_READ','OWNER_CREATE','OWNER_UPDATE',
        'APPOINTMENT_READ','APPOINTMENT_CREATE','APPOINTMENT_UPDATE','APPOINTMENT_CANCEL',
        'MEDICAL_RECORD_READ','MEDICAL_RECORD_WRITE',
        'PRESCRIPTION_READ','PRESCRIPTION_CREATE',
        'REPORT_VIEW','SETTINGS_READ','SETTINGS_UPDATE','BRANDING_UPDATE',
        'STAFF_MANAGE','BRANCH_MANAGE','SERVICE_MANAGE',
        'MESSAGE_READ','MESSAGE_WRITE','DOCUMENT_READ','DOCUMENT_WRITE','NOTIFICATION_MANAGE'
   );

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
    ON r.code = 'VETERINARIAN'
   AND p.code IN (
        'PET_READ','PET_UPDATE',
        'OWNER_READ',
        'APPOINTMENT_READ','APPOINTMENT_UPDATE',
        'MEDICAL_RECORD_READ','MEDICAL_RECORD_WRITE',
        'PRESCRIPTION_READ','PRESCRIPTION_CREATE',
        'MESSAGE_READ','MESSAGE_WRITE','DOCUMENT_READ','DOCUMENT_WRITE'
   );

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
    ON r.code = 'RECEPTIONIST'
   AND p.code IN (
        'PET_READ','PET_CREATE','PET_UPDATE',
        'OWNER_READ','OWNER_CREATE','OWNER_UPDATE',
        'APPOINTMENT_READ','APPOINTMENT_CREATE','APPOINTMENT_UPDATE','APPOINTMENT_CANCEL',
        'MESSAGE_READ','MESSAGE_WRITE','DOCUMENT_READ'
   );

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
    ON r.code = 'PET_OWNER'
   AND p.code IN (
        'PET_READ','APPOINTMENT_READ','APPOINTMENT_CREATE','APPOINTMENT_CANCEL',
        'PRESCRIPTION_READ','MESSAGE_READ','MESSAGE_WRITE','DOCUMENT_READ'
   );

INSERT INTO vaccine_catalog (tenant_id, name_es, name_en, species, default_interval_days, active) VALUES
    (NULL, 'Rabia', 'Rabies', 'DOG', 365, TRUE),
    (NULL, 'Moquillo', 'Distemper', 'DOG', 365, TRUE),
    (NULL, 'Parvovirus', 'Parvovirus', 'DOG', 365, TRUE),
    (NULL, 'Tos de las perreras', 'Kennel cough', 'DOG', 365, TRUE),
    (NULL, 'Triple felina', 'Feline viral rhinotracheitis', 'CAT', 365, TRUE),
    (NULL, 'Leucemia felina', 'Feline leukemia', 'CAT', 365, TRUE),
    (NULL, 'Rabia felina', 'Feline rabies', 'CAT', 365, TRUE);

INSERT INTO platform_settings (support_email, default_locale, default_trial_days, maintenance_mode)
VALUES ('soporte@animalin.app', 'es', 14, FALSE);
