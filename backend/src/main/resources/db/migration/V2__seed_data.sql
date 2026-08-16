-- ============================================================================
-- V2__seed_data.sql
-- Seed adatok: permissions, roles, role_permissions, locations, app_users, configs, softwares, devices, assignments
-- ============================================================================

-- ============================================================================
-- 1. permissions seed — 30 granuláris permission
-- ============================================================================
INSERT INTO permissions (name, created_at, updated_at) VALUES
    ('DEVICE_CREATE',              NOW(), NOW()),
    ('DEVICE_READ',                NOW(), NOW()),
    ('DEVICE_UPDATE',              NOW(), NOW()),
    ('DEVICE_DELETE',              NOW(), NOW()),
    ('DEVICE_MANAGE',              NOW(), NOW()),
    ('DEVICE_ASSIGN',              NOW(), NOW()),
    ('DEVICE_UNASSIGN',            NOW(), NOW()),
    ('ASSIGNMENT_APPROVE',         NOW(), NOW()),
    ('DEVICE_MAINTENANCE_REQUEST', NOW(), NOW()),
    ('DEVICE_MAINTENANCE_APPROVE', NOW(), NOW()),
    ('DEVICE_DISPOSE_REQUEST',     NOW(), NOW()),
    ('DEVICE_DISPOSE_APPROVE',     NOW(), NOW()),
    ('ATTACHMENT_MANAGE',          NOW(), NOW()),
    ('USER_READ',                  NOW(), NOW()),
    ('USER_CREATE',                NOW(), NOW()),
    ('USER_UPDATE',                NOW(), NOW()),
    ('USER_DELETE',                NOW(), NOW()),
    ('ROLE_READ',                  NOW(), NOW()),
    ('ROLE_MANAGE',                NOW(), NOW()),
    ('LOCATION_READ',              NOW(), NOW()),
    ('LOCATION_CREATE',            NOW(), NOW()),
    ('LOCATION_UPDATE',            NOW(), NOW()),
    ('LOCATION_DELETE',            NOW(), NOW()),
    ('SOFTWARE_LICENSE_VIEW',      NOW(), NOW()),
    ('SOFTWARE_CREATE',            NOW(), NOW()),
    ('SOFTWARE_UPDATE',            NOW(), NOW()),
    ('SOFTWARE_DELETE',            NOW(), NOW()),
    ('AUDIT_READ',                 NOW(), NOW()),
    ('AUDIT_ROLLBACK',             NOW(), NOW()),
    ('IMPORT_EXECUTE',             NOW(), NOW());

-- ============================================================================
-- 2. roles seed — 3 role
-- ============================================================================
INSERT INTO roles (name, created_at, updated_at) VALUES
    ('ROLE_ADMIN',   NOW(), NOW()),
    ('ROLE_TEACHER', NOW(), NOW()),
    ('ROLE_STUDENT', NOW(), NOW());

-- ============================================================================
-- 3. role_permissions seed — role-ok és permission-ök összerendelése
-- ============================================================================
-- ROLE_ADMIN: minden permission (30/30)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.name = 'ROLE_ADMIN';

-- ROLE_TEACHER: oktatói jogok
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_TEACHER'
  AND p.name IN (
      'DEVICE_READ', 'DEVICE_ASSIGN', 'DEVICE_UNASSIGN',
      'DEVICE_MAINTENANCE_REQUEST', 'DEVICE_DISPOSE_REQUEST',
      'USER_READ', 'LOCATION_READ', 'AUDIT_READ'
  );

-- ROLE_STUDENT: hallgatói olvasási jogok
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_STUDENT'
  AND p.name IN ('DEVICE_READ', 'USER_READ', 'LOCATION_READ');

-- ============================================================================
-- 4. locations seed — hierarchikus helyszínek
-- ============================================================================
DO $$
DECLARE
    root_id BIGINT;
    classroom_id BIGINT;
    storage_id BIGINT;
    group_id BIGINT;
BEGIN
    -- Root location: Tanszéki Iroda (OFFICE)
    INSERT INTO locations (name, parent_id, type, created_at, updated_at)
    VALUES ('Tanszéki Iroda', NULL, 'OFFICE', NOW(), NOW())
    RETURNING id INTO root_id;

    -- Classroom a root alatt
    INSERT INTO locations (name, parent_id, type, created_at, updated_at)
    VALUES ('Tanterem 101', root_id, 'CLASSROOM', NOW(), NOW())
    RETURNING id INTO classroom_id;

    -- Storage a root alatt
    INSERT INTO locations (name, parent_id, type, created_at, updated_at)
    VALUES ('Eszköz Raktár', root_id, 'STORAGE', NOW(), NOW())
    RETURNING id INTO storage_id;

    -- Group a root alatt (GROUP típusúra nem rendelhető ki eszköz)
    INSERT INTO locations (name, parent_id, type, created_at, updated_at)
    VALUES ('Hallgatói Csoport', root_id, 'GROUP', NOW(), NOW())
    RETURNING id INTO group_id;
END $$;

-- ============================================================================
-- 5. app_users seed — demo felhasználók (admin, teacher, student)
-- ============================================================================
-- Jelszavak: 'ChangeMe123!' (A SeedPasswordInitializer induláskor valódi Argon2id hash-re frissíti)
INSERT INTO app_users (
    email_encrypted,
    email_hash,
    office_location_id,
    password_hash,
    active,
    must_change_password,
    role_id,
    failed_login_count,
    locked_until,
    password_changed_at,
    created_at,
    updated_at
) VALUES (
    'PLACEHOLDER_ENCRYPTED_admin@tanszek.local',
    encode(digest('admin@tanszek.local', 'sha256'), 'hex'),
    (SELECT id FROM locations WHERE name = 'Tanszéki Iroda' AND parent_id IS NULL LIMIT 1),
    '$argon2id$v=19$m=65536,t=3,p=1$PLACEHOLDER_SALT$PLACEHOLDER_HASH',
    true,
    false,
    (SELECT id FROM roles WHERE name = 'ROLE_ADMIN' LIMIT 1),
    0,
    NULL,
    NOW(),
    NOW(),
    NOW()
), (
    'PLACEHOLDER_ENCRYPTED_teacher@tanszek.local',
    encode(digest('teacher@tanszek.local', 'sha256'), 'hex'),
    (SELECT id FROM locations WHERE name = 'Tanszéki Iroda' AND parent_id IS NULL LIMIT 1),
    '$argon2id$v=19$m=65536,t=3,p=1$PLACEHOLDER_SALT$PLACEHOLDER_HASH',
    true,
    false,
    (SELECT id FROM roles WHERE name = 'ROLE_TEACHER' LIMIT 1),
    0,
    NULL,
    NOW(),
    NOW(),
    NOW()
), (
    'PLACEHOLDER_ENCRYPTED_student@tanszek.local',
    encode(digest('student@tanszek.local', 'sha256'), 'hex'),
    NULL,
    '$argon2id$v=19$m=65536,t=3,p=1$PLACEHOLDER_SALT$PLACEHOLDER_HASH',
    true,
    false,
    (SELECT id FROM roles WHERE name = 'ROLE_STUDENT' LIMIT 1),
    0,
    NULL,
    NOW(),
    NOW(),
    NOW()
);

-- ============================================================================
-- 6. configs seed — rendszer-konfigurációk
-- ============================================================================
INSERT INTO configs (key, value, created_at, updated_at) VALUES
    ('AUTH_PROVIDER',              'LOCAL',                      NOW(), NOW()),
    ('BACKUP_RETENTION_DAYS',      '30',                         NOW(), NOW()),
    ('AUDIT_RETENTION_YEARS',      '5',                          NOW(), NOW()),
    ('AUDIT_ARCHIVE_PATH',         '/var/backups/archive/audit', NOW(), NOW()),
    ('MAX_LOGIN_ATTEMPTS',         '5',                          NOW(), NOW()),
    ('LOCKOUT_DURATION_MIN',       '15',                         NOW(), NOW()),
    ('JWT_ACCESS_TTL_MIN',         '15',                         NOW(), NOW()),
    ('JWT_REFRESH_TTL_DAYS',       '30',                         NOW(), NOW()),
    ('JWT_KID_GRACE_PERIOD_SEC',   '3600',                       NOW(), NOW()),
    ('PAGINATION_DEFAULT_SIZE',    '20',                         NOW(), NOW()),
    ('PAGINATION_MAX_SIZE',        '50',                         NOW(), NOW()),
    ('ALERT_EMAIL_RECIPIENT',      'admin@tanszek.local',        NOW(), NOW());

-- ============================================================================
-- 7. softwares seed — demo szoftverek és licencek
-- ============================================================================
INSERT INTO softwares (name, license_key_encrypted, created_at, updated_at) VALUES
    ('MATLAB R2024a',             'PLACEHOLDER_ENCRYPTED_MATLAB_KEY',    NOW(), NOW()),
    ('IntelliJ IDEA Ultimate',    'PLACEHOLDER_ENCRYPTED_INTELLIJ_KEY',  NOW(), NOW()),
    ('Microsoft Office 2024 Pro', 'PLACEHOLDER_ENCRYPTED_OFFICE_KEY',    NOW(), NOW()),
    ('Adobe Creative Cloud 2024', 'PLACEHOLDER_ENCRYPTED_ADOBE_KEY',     NOW(), NOW());

-- ============================================================================
-- 8. devices seed — demo eszközök
-- ============================================================================
DO $$
DECLARE
    storage_loc_id BIGINT;
    classroom_loc_id BIGINT;
    assigned_device_id BIGINT;
    admin_user_id BIGINT;
BEGIN
    SELECT id INTO storage_loc_id FROM locations WHERE name = 'Eszköz Raktár' LIMIT 1;
    SELECT id INTO classroom_loc_id FROM locations WHERE name = 'Tanterem 101' LIMIT 1;
    SELECT id INTO admin_user_id FROM app_users WHERE email_hash = encode(digest('admin@tanszek.local', 'sha256'), 'hex') LIMIT 1;

    -- IN_STORAGE eszközök
    INSERT INTO devices (type, inventory_number, status, current_location_id, created_at, updated_at) VALUES
        ('laptop',    'INV-2026-0001', 'IN_STORAGE',  storage_loc_id, NOW(), NOW()),
        ('desktop',   'INV-2026-0003', 'IN_STORAGE',  storage_loc_id, NOW(), NOW()),
        ('desktop',   'INV-2026-0004', 'MAINTENANCE', storage_loc_id, NOW(), NOW()),
        ('monitor',   'INV-2026-0005', 'IN_STORAGE',  storage_loc_id, NOW(), NOW()),
        ('projector', 'INV-2026-0006', 'IN_STORAGE',  storage_loc_id, NOW(), NOW()),
        ('tablet',    'INV-2026-0007', 'DISPOSED',    NULL,           NOW(), NOW());

    -- ASSIGNED eszköz
    INSERT INTO devices (type, inventory_number, status, current_location_id, created_at, updated_at)
    VALUES ('laptop', 'INV-2026-0002', 'ASSIGNED', classroom_loc_id, NOW(), NOW())
    RETURNING id INTO assigned_device_id;

    -- Kezdeti hozzárendelési rekord a Tanterem 101-hez
    INSERT INTO device_assignments (
        device_id,
        from_location_id,
        to_location_id,
        from_user_id,
        to_user_id,
        by_user_id,
        approved_by_id,
        status,
        date_of_assignment,
        created_date,
        created_at,
        updated_at
    ) VALUES (
        assigned_device_id,
        storage_loc_id,
        classroom_loc_id,
        NULL,
        NULL,
        admin_user_id,
        admin_user_id,
        'ASSIGNED',
        NOW(),
        NOW(),
        NOW(),
        NOW()
    );
END $$;
