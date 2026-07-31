-- ============================================================================
-- V3__seed_demo_devices.sql
-- Demo adatok: eszközök, szoftverek, teszt oktató és hallgató userek
-- ============================================================================

-- 1. Demo Eszközök (devices)
INSERT INTO devices (type, inventory_number, status, created_at, updated_at) VALUES
    ('laptop', 'INV-2026-0001', 'IN_STORAGE', NOW(), NOW()),
    ('laptop', 'INV-2026-0002', 'ASSIGNED', NOW(), NOW()),
    ('desktop', 'INV-2026-0003', 'IN_STORAGE', NOW(), NOW()),
    ('desktop', 'INV-2026-0004', 'MAINTENANCE', NOW(), NOW()),
    ('monitor', 'INV-2026-0005', 'IN_STORAGE', NOW(), NOW()),
    ('projector', 'INV-2026-0006', 'IN_STORAGE', NOW(), NOW()),
    ('tablet', 'INV-2026-0007', 'DISPOSED', NOW(), NOW());

-- 2. Demo Szoftverek (softwares)
INSERT INTO softwares (name, license_key_encrypted, created_at, updated_at) VALUES
    ('MATLAB R2024a', 'PLACEHOLDER_ENCRYPTED_MATLAB_KEY', NOW(), NOW()),
    ('IntelliJ IDEA Ultimate', 'PLACEHOLDER_ENCRYPTED_INTELLIJ_KEY', NOW(), NOW()),
    ('Microsoft Office 2024 Pro', 'PLACEHOLDER_ENCRYPTED_OFFICE_KEY', NOW(), NOW()),
    ('Adobe Creative Cloud 2024', 'PLACEHOLDER_ENCRYPTED_ADOBE_KEY', NOW(), NOW());

-- 3. Demo Oktató (Teacher) és Hallgató (Student) felhasználók
INSERT INTO app_users (
    email_encrypted, email_hash, office_location_id, password_hash, active, must_change_password, role_id, created_at, updated_at
) VALUES (
    'PLACEHOLDER_ENCRYPTED_teacher@tanszek.local',
    encode(digest('teacher@tanszek.local', 'sha256'), 'hex'),
    (SELECT id FROM locations WHERE name = 'Tanszéki Iroda' LIMIT 1),
    '$argon2id$v=19$m=65536,t=3,p=1$PLACEHOLDER_SALT$PLACEHOLDER_HASH',
    true, false, (SELECT id FROM roles WHERE name = 'ROLE_TEACHER' LIMIT 1), NOW(), NOW()
), (
    'PLACEHOLDER_ENCRYPTED_student@tanszek.local',
    encode(digest('student@tanszek.local', 'sha256'), 'hex'),
    NULL,
    '$argon2id$v=19$m=65536,t=3,p=1$PLACEHOLDER_SALT$PLACEHOLDER_HASH',
    true, false, (SELECT id FROM roles WHERE name = 'ROLE_STUDENT' LIMIT 1), NOW(), NOW()
);
