-- ============================================================================
-- V2__seed.sql
-- Seed adatok: permissions, roles, role_permissions, locations, app_users, configs
--
-- Fontos:
--   - A demo admin user jelszava: 'ChangeMe123!'
--   - Az Argon2id hash-t implementációkor kell generálni (memory=65536, t=3, p=1)
--   - Most placeholder hash, amit az Argon2PasswordEncoder az első login során
--     upgrade-el (Spring Security Argon2 PasswordEncoder.matches() kezeli)
--
-- A 'ChangeMe123!' jelszóhoz tartozó Argon2 hash generálása:
--   1. Spring Security 6.0+ beépített Argon2PasswordEncoder használata
--   2. Java kód: passwordEncoder.encode("ChangeMe123!")
--   3. A kapott PHC string-et be kell másolni ide
--
-- A V1__init_schema.sql init után fut le, minden tábla létezik.
-- ============================================================================

-- ============================================================================
-- 1. permissions seed — 14 permission
-- ============================================================================
INSERT INTO permissions (name, created_at, updated_at) VALUES
    ('DEVICE_CREATE',     NOW(), NOW()),
    ('DEVICE_READ',       NOW(), NOW()),
    ('DEVICE_UPDATE',     NOW(), NOW()),
    ('DEVICE_DELETE',     NOW(), NOW()),
    ('DEVICE_ASSIGN',     NOW(), NOW()),
    ('DEVICE_UNASSIGN',   NOW(), NOW()),
    ('USER_MANAGE',       NOW(), NOW()),
    ('USER_READ',         NOW(), NOW()),
    ('LOCATION_MANAGE',   NOW(), NOW()),
    ('LOCATION_READ',     NOW(), NOW()),
    ('AUDIT_READ',        NOW(), NOW()),
    ('AUDIT_ROLLBACK',    NOW(), NOW()),
    ('SOFTWARE_MANAGE',   NOW(), NOW()),
    ('SOFTWARE_LICENSE_VIEW', NOW(), NOW());

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
-- ROLE_ADMIN: minden permission
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.name = 'ROLE_ADMIN';

-- ROLE_TEACHER: DEVICE_READ + DEVICE_ASSIGN + DEVICE_UNASSIGN + USER_READ + LOCATION_READ + AUDIT_READ
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_TEACHER'
  AND p.name IN ('DEVICE_READ', 'DEVICE_ASSIGN', 'DEVICE_UNASSIGN', 'USER_READ', 'LOCATION_READ', 'AUDIT_READ');

-- ROLE_STUDENT: DEVICE_READ + USER_READ + LOCATION_READ
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_STUDENT'
  AND p.name IN ('DEVICE_READ', 'USER_READ', 'LOCATION_READ');

-- ============================================================================
-- 4. locations seed — hierarchikus demo location-ok
-- ============================================================================
-- A root location id-jét a DO $$ blokkban tároljuk, mert parent_id-ként kell
-- a child location-ok INSERT-jénél.
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

    -- Group a root alatt (NOTE: GROUP location-ra NEM lehet eszközt assignolni)
    INSERT INTO locations (name, parent_id, type, created_at, updated_at)
    VALUES ('Hallgatói Csoport', root_id, 'GROUP', NOW(), NOW())
    RETURNING id INTO group_id;
END $$;

-- ============================================================================
-- 5. app_users seed — demo admin user
-- ============================================================================
-- Email: admin@tanszek.local
-- Jelszó: ChangeMe123! (first-login kötelező csere a must_change_password flag miatt)
-- Role: ROLE_ADMIN
--
-- AZ ARGON2 HASH IMPLEMENTÁCIÓKOR GENERÁLANDÓ:
--   A Spring Security 6+ Argon2PasswordEncoder.encode("ChangeMe123!")
--   hívással kell generálni, és a kapott PHC string-et kell beilleszteni.
--   Paraméterek: memory=65536, t=3, p=1, salt-length=16, hash-length=32.
--
-- A lentebbi placeholder hash-t az Argon2PasswordEncoder.matches() kezeli,
-- mert a Spring Security PHC string formátumot használ.
-- Implementációkor: java -cp ... Argon2HashGen.ChangeMe123!
--   v. futtatás a backend repository-ban:
--     public class Argon2HashGen { public static void main(String[] args) {
--         System.out.println(new Argon2PasswordEncoder().encode("ChangeMe123!"));
--     }}
--
-- Jelenlegi placeholder hash:
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
)
SELECT
    -- email_encrypted: 'admin@tanszek.local' AES-GCM titkosítása (CryptoService)
    'PLACEHOLDER_ENCRYPTED_admin@tanszek.local' AS email_encrypted,
    -- email_hash: 'admin@tanszek.local' SHA-256 hash-e (CryptoService)
    encode(digest('admin@tanszek.local', 'sha256'), 'hex') AS email_hash,
    -- office_location_id: Tanszéki Iroda (root OFFICE location)
    (SELECT id FROM locations WHERE name = 'Tanszéki Iroda' AND parent_id IS NULL LIMIT 1) AS office_location_id,
    -- password_hash: Argon2id hash 'ChangeMe123!'-hoz (implementációkor generálandó)
    -- A Spring Security 6 Argon2PasswordEncoder PHC string formátuma:
    --   $argon2id$v=19$m=65536,t=3,p=1$<base64-salt>$<base64-hash>
    -- PLACEHOLDER — implementációkor cserélendő:
    '$argon2id$v=19$m=65536,t=3,p=1$PLACEHOLDER_SALT$PLACEHOLDER_HASH' AS password_hash,
    true AS active,
    true AS must_change_password,
    -- role_id: ROLE_ADMIN
    (SELECT id FROM roles WHERE name = 'ROLE_ADMIN' LIMIT 1) AS role_id,
    0 AS failed_login_count,
    NULL AS locked_until,
    NOW() AS password_changed_at,
    NOW() AS created_at,
    NOW() AS updated_at;

-- ============================================================================
-- 6. configs seed — 12 rendszer-konfigurációs kulcs-érték
-- ============================================================================
INSERT INTO configs (key, value, created_at, updated_at) VALUES
    ('AUTH_PROVIDER',              'LOCAL',                    NOW(), NOW()),
    ('BACKUP_RETENTION_DAYS',      '30',                       NOW(), NOW()),
    ('AUDIT_RETENTION_YEARS',      '5',                        NOW(), NOW()),
    ('AUDIT_ARCHIVE_PATH',         '/var/backups/archive/audit', NOW(), NOW()),
    ('MAX_LOGIN_ATTEMPTS',         '5',                        NOW(), NOW()),
    ('LOCKOUT_DURATION_MIN',       '15',                       NOW(), NOW()),
    ('JWT_ACCESS_TTL_MIN',         '15',                       NOW(), NOW()),
    ('JWT_REFRESH_TTL_DAYS',       '30',                       NOW(), NOW()),
    ('JWT_KID_GRACE_PERIOD_SEC',   '3600',                     NOW(), NOW()),
    ('PAGINATION_DEFAULT_SIZE',    '20',                       NOW(), NOW()),
    ('PAGINATION_MAX_SIZE',        '50',                       NOW(), NOW()),
    ('ALERT_EMAIL_RECIPIENT',      'admin@tanszek.local',      NOW(), NOW());

-- ============================================================================
-- Összesítés (sanity check):
--   14 permissions
--    3 roles
--   ~22 role_permissions (14 + 6 + 3)
--    4 locations (1 root + 3 children)
--    1 app_user (admin)
--    0 user_permissions (minden user a role permissionjeit örökli)
--   12 configs
-- ============================================================================