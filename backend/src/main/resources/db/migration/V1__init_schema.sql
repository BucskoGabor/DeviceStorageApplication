-- ============================================================================
-- V1__init_schema.sql
-- Egyetemi Informatikai Tanszéki Nyilvántartó Rendszer (Clean Initial Schema)
-- 14 tábla + BaseEntity mezők (created_at, updated_at) minden táblán
-- ============================================================================
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================================
-- 1. configs tábla — rendszer konfigurációs kulcs-érték párok
-- ============================================================================
CREATE TABLE configs (
    id BIGSERIAL PRIMARY KEY,
    key VARCHAR(255) NOT NULL UNIQUE,
    value TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE configs IS 'Rendszer konfigurációs kulcs-érték párok (pl. AUTH_PROVIDER, BACKUP_RETENTION_DAYS)';

-- ============================================================================
-- 2. permissions tábla — granularitású jogosultságok
-- ============================================================================
CREATE TABLE permissions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE permissions IS 'Granuláris jogosultságok (DEVICE_CREATE, DEVICE_READ, USER_MANAGE, stb.)';

-- ============================================================================
-- 3. roles tábla — felhasználói szerepkörök (jogosultság-gyűjtők)
-- ============================================================================
CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE roles IS 'Felhasználói szerepkörök (ROLE_ADMIN, ROLE_TEACHER, ROLE_STUDENT)';

-- ============================================================================
-- 4. locations tábla — hierarchikus helyszínek (parent_id self-reference)
-- ============================================================================
CREATE TABLE locations (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,  -- @Version optimistic lock
    name VARCHAR(255) NOT NULL,
    parent_id BIGINT,
    type VARCHAR(20) NOT NULL CHECK (type IN ('CLASSROOM', 'OFFICE', 'STORAGE', 'GROUP')),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_locations_parent FOREIGN KEY (parent_id) REFERENCES locations(id) ON DELETE SET NULL
);

CREATE INDEX idx_locations_parent_id ON locations(parent_id);
CREATE INDEX idx_locations_type ON locations(type);

COMMENT ON TABLE locations IS 'Hierarchikus helyszínek (épület -> terem -> csoport)';
COMMENT ON COLUMN locations.type IS 'CLASSROOM/terem, OFFICE/iroda, STORAGE/raktár, GROUP/csoport';
COMMENT ON COLUMN locations.version IS 'Optimistic lock — párhuzamos módosítás ellen';

-- ============================================================================
-- 5. app_users tábla — felhasználók (email encrypted + hash, Argon2id password)
-- ============================================================================
CREATE TABLE app_users (
    id BIGSERIAL PRIMARY KEY,
    email_encrypted TEXT NOT NULL,
    email_hash VARCHAR(64) NOT NULL UNIQUE,  -- SHA-256 hex
    office_location_id BIGINT,
    password_hash TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    must_change_password BOOLEAN NOT NULL DEFAULT false,
    role_id BIGINT NOT NULL,
    failed_login_count INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP,
    password_changed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_app_users_office_location FOREIGN KEY (office_location_id) REFERENCES locations(id) ON DELETE SET NULL,
    CONSTRAINT fk_app_users_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT
);

CREATE INDEX idx_app_users_active ON app_users(active);
CREATE INDEX idx_app_users_role_id ON app_users(role_id);
CREATE INDEX idx_app_users_office_location_id ON app_users(office_location_id);
CREATE INDEX idx_app_users_locked_until ON app_users(locked_until) WHERE locked_until IS NOT NULL;

COMMENT ON TABLE app_users IS 'Felhasználók (email encrypted + hash, Argon2id jelszó, role, lockout)';
COMMENT ON COLUMN app_users.email_encrypted IS 'AES-GCM titkosított email (admin felületen visszafejthető)';
COMMENT ON COLUMN app_users.email_hash IS 'SHA-256 hash az egyediséghez és gyors kereséshez';
COMMENT ON COLUMN app_users.password_hash IS 'Argon2id hash (OWASP ajánlás)';
COMMENT ON COLUMN app_users.must_change_password IS 'First-login flag — belépéskor /password-change-re kényszerít';
COMMENT ON COLUMN app_users.failed_login_count IS 'Brute-force védelem — 5 próba -> 15 min lockout';

-- ============================================================================
-- 6. role_permissions join table — role-ok és permission-ök many-to-many
-- ============================================================================
CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);

CREATE INDEX idx_role_permissions_permission_id ON role_permissions(permission_id);

COMMENT ON TABLE role_permissions IS 'Join table: melyik role-hoz melyik permission-ök tartoznak';

-- ============================================================================
-- 7. user_permissions join table — user-specifikus közvetlen jogosultságok
-- ============================================================================
CREATE TABLE user_permissions (
    user_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, permission_id),
    CONSTRAINT fk_user_permissions_user FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_permissions_permission_id ON user_permissions(permission_id);

COMMENT ON TABLE user_permissions IS 'Join table: user-specifikus extra jogosultságok (role-on felül)';

-- ============================================================================
-- 8. softwares tábla — szoftver licence-ek
-- ============================================================================
CREATE TABLE softwares (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    license_key_encrypted TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE softwares IS 'Szoftverek és licence kulcsok (AES-GCM titkosítás)';
COMMENT ON COLUMN softwares.license_key_encrypted IS 'AES-GCM titkosított licence kulcs';

-- ============================================================================
-- 9. devices tábla — eszközök
-- ============================================================================
CREATE TABLE devices (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    inventory_number VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING', 'IN_STORAGE', 'ASSIGNED', 'PENDING_MAINTENANCE', 'MAINTENANCE', 'PENDING_DISPOSAL', 'DISPOSED')),
    status_reason TEXT,
    previous_status VARCHAR(32),
    current_location_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_devices_current_location FOREIGN KEY (current_location_id) REFERENCES locations(id) ON DELETE SET NULL
);

CREATE INDEX idx_devices_status ON devices(status);
CREATE INDEX idx_devices_type ON devices(type);
CREATE INDEX idx_devices_current_location ON devices(current_location_id);

COMMENT ON TABLE devices IS 'Eszközök (laptop, monitor, projektor, stb.)';
COMMENT ON COLUMN devices.status IS 'IN_STORAGE/ASSIGNED/PENDING_MAINTENANCE/MAINTENANCE/PENDING_DISPOSAL/DISPOSED';
COMMENT ON COLUMN devices.status_reason IS 'Karbantartás, selejtezés stb. indoklása';
COMMENT ON COLUMN devices.previous_status IS 'Kérelem előtti állapot elutasítás esetén';
COMMENT ON COLUMN devices.inventory_number IS 'Egyedi leltári szám (max 50 karakter)';
COMMENT ON COLUMN devices.current_location_id IS 'Jelenlegi raktár vagy terem helyszín';

-- ============================================================================
-- 10. device_softwares join table — devices és softwares many-to-many
-- ============================================================================
CREATE TABLE device_softwares (
    device_id BIGINT NOT NULL,
    software_id BIGINT NOT NULL,
    PRIMARY KEY (device_id, software_id),
    CONSTRAINT fk_device_softwares_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
    CONSTRAINT fk_device_softwares_software FOREIGN KEY (software_id) REFERENCES softwares(id) ON DELETE CASCADE
);

CREATE INDEX idx_device_softwares_software_id ON device_softwares(software_id);

COMMENT ON TABLE device_softwares IS 'Join table: melyik eszközre melyik szoftver van telepítve';

-- ============================================================================
-- 11. device_attachments tábla — eszközhöz csatolt fájlok
-- ============================================================================
CREATE TABLE device_attachments (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT NOW(),
    uploaded_by_id BIGINT NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_device_attachments_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
    CONSTRAINT fk_device_attachments_uploaded_by FOREIGN KEY (uploaded_by_id) REFERENCES app_users(id) ON DELETE RESTRICT
);

CREATE INDEX idx_device_attachments_device_id ON device_attachments(device_id);
CREATE INDEX idx_device_attachments_uploaded_by_id ON device_attachments(uploaded_by_id);

COMMENT ON TABLE device_attachments IS 'Eszközökhöz csatolt fájlok (max 5MB/fájl, max 5/device)';

-- ============================================================================
-- 12. device_assignments tábla — eszköz hozzárendelés (history-szerű audit napló)
-- ============================================================================
CREATE TABLE device_assignments (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL,
    from_location_id BIGINT,
    to_location_id BIGINT,
    from_user_id BIGINT,
    to_user_id BIGINT,
    by_user_id BIGINT NOT NULL,
    approved_by_id BIGINT,
    unassigned_by_id BIGINT,
    unassign_approved_by_id BIGINT,
    date_of_assignment TIMESTAMP,
    created_date TIMESTAMP NOT NULL DEFAULT NOW(),
    unassign_date TIMESTAMP,
    unassign_created_date TIMESTAMP,
    status VARCHAR(30) NOT NULL CHECK (status IN ('IN_STORAGE', 'ASSIGNED', 'PENDING_ASSIGNMENT', 'PENDING_UNASSIGNMENT', 'REJECTED')),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_device_assignments_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE RESTRICT,
    CONSTRAINT fk_device_assignments_from_location FOREIGN KEY (from_location_id) REFERENCES locations(id) ON DELETE SET NULL,
    CONSTRAINT fk_device_assignments_to_location FOREIGN KEY (to_location_id) REFERENCES locations(id) ON DELETE SET NULL,
    CONSTRAINT fk_device_assignments_from_user FOREIGN KEY (from_user_id) REFERENCES app_users(id) ON DELETE SET NULL,
    CONSTRAINT fk_device_assignments_to_user FOREIGN KEY (to_user_id) REFERENCES app_users(id) ON DELETE SET NULL,
    CONSTRAINT fk_device_assignments_by_user FOREIGN KEY (by_user_id) REFERENCES app_users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_device_assignments_approved_by FOREIGN KEY (approved_by_id) REFERENCES app_users(id) ON DELETE SET NULL,
    CONSTRAINT fk_device_assignments_unassigned_by FOREIGN KEY (unassigned_by_id) REFERENCES app_users(id) ON DELETE SET NULL,
    CONSTRAINT fk_device_assignments_unassign_approved_by FOREIGN KEY (unassign_approved_by_id) REFERENCES app_users(id) ON DELETE SET NULL
);

CREATE INDEX idx_device_assignments_device_id ON device_assignments(device_id);
CREATE INDEX idx_device_assignments_to_user_id ON device_assignments(to_user_id) WHERE to_user_id IS NOT NULL;
CREATE INDEX idx_device_assignments_to_location_id ON device_assignments(to_location_id) WHERE to_location_id IS NOT NULL;
CREATE INDEX idx_device_assignments_status ON device_assignments(status);

COMMENT ON TABLE device_assignments IS 'Eszköz hozzárendelés történet és állapotgép';
COMMENT ON COLUMN device_assignments.status IS 'IN_STORAGE/ASSIGNED/PENDING_ASSIGNMENT/PENDING_UNASSIGNMENT/REJECTED';

-- ============================================================================
-- 13. audit_logs tábla — audit log + rollback támogatás
-- ============================================================================
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    user_email VARCHAR(255) NOT NULL,
    endpoint VARCHAR(500) NOT NULL,
    method VARCHAR(10) NOT NULL,
    request_payload TEXT,
    changes_json TEXT,
    http_status INTEGER NOT NULL,
    entity_type VARCHAR(100),
    entity_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_timestamp ON audit_logs(timestamp DESC);
CREATE INDEX idx_audit_logs_user_email ON audit_logs(user_email);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_endpoint ON audit_logs(endpoint);
CREATE INDEX idx_audit_logs_http_status ON audit_logs(http_status);

COMMENT ON TABLE audit_logs IS 'Audit log minden írási művelethez, rollback támogatással';
COMMENT ON COLUMN audit_logs.changes_json IS 'JSON diff {before: {...}, after: {...}} a rollback-hez';
COMMENT ON COLUMN audit_logs.entity_type IS 'Device, AppUser, Location, DeviceAssignment, Software, DeviceAttachment';

-- ============================================================================
-- 14. refresh_tokens tábla — JWT refresh token rotation (RFC 6819)
-- ============================================================================
CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,  -- SHA-256 hex
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    replaced_by_id BIGINT,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_refresh_tokens_replaced_by FOREIGN KEY (replaced_by_id) REFERENCES refresh_tokens(id) ON DELETE SET NULL
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
CREATE INDEX idx_refresh_tokens_revoked ON refresh_tokens(revoked) WHERE revoked = true;
CREATE INDEX idx_refresh_tokens_replaced_by_id ON refresh_tokens(replaced_by_id);

COMMENT ON TABLE refresh_tokens IS 'JWT refresh token rotation (RFC 6819 kompatibilis)';
