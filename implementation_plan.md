# Egyetemi Informatikai Tanszéki Nyilvántartó Rendszer - Részletes Implementációs Terv

## 0. Repo Struktúra (Monorepo)

```
/
├── backend/                # Spring Boot alkalmazás (com.tanszek.device.* feature package-ek, alcsomagokkal)
├── frontend/               # Vite + React alkalmazás
├── docs/                   # Markdown dokumentáció
│   ├── architecture.md
│   ├── api.md              # OpenAPI-ból auto-generált
│   ├── deployment.md
│   └── runbook.md          # Standard: deployment, rollback, hibák, monitoring, credential rotation, backup restore
├── scripts/                # Utility scriptek
│   ├── bootstrap.sh        # első indítás (.env.example → .env és backup.env.example → backup.env)
│   ├── smoke-test.sh       # Fázis 5
│   ├── backup-restore.sh   # dump visszaállítás
│   └── rotate-jwt-secret.sh
├── docker-compose.yml
├── .env.example            # backend és frontend konténer .env-je (SPRING_DATASOURCE_*, JWT_*, SMTP_*, stb.)
├── backup.env.example      # backup konténer saját .env-je (BACKUP_RETENTION_DAYS, POSTGRES_*, stb.)
├── .gitignore
├── .editorconfig
├── LICENSE (MIT)
├── README.md
├── CONTRIBUTING.md
├── implementation_plan.md
└── agent_progress.md
```

### Backend Package Struktúra (Feature-based, alcsomagok)
```
com.tanszek.device
├── auth/                  # Login, JWT, refresh token, AuthProvider
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   ├── dto/
│   ├── exception/
│   └── config/
├── user/
├── device/
├── location/
├── software/
├── assignment/
├── attachment/
├── audit/
├── import/                # Excel import (Apache POI, Upload → Preview → Confirm)
├── crypto/                # CryptoService
├── config/                # SecurityConfig, JpaConfig, OpenApiConfig, MailConfig
├── common/                # GlobalExceptionHandler, BaseEntity, MessageKey constants
└── DeviceStorageApplication.java
```

---

## 1. Deployment & Hosting
*   Csak `docker-compose` alapú, lokális fejlesztés és on-premise egyetemi szerveren történő hosztolás.
*   Nincs cloud függőség.
*   PostgreSQL adatbázisról **napi pg_dump** dedikált `backup` konténerben, `backup_data:/var/backups/` named volume-ba.
*   Backup retention: 30 nap lokális dump.
*   **Production hardening:** non-root user mindenhol, read-only FS ahol lehet, resource limits, healthcheckek, log rotation.
*   **Healthcheck policy:** A docker-compose healthcheck-ek **minden környezetben aktívak** (dev, staging, prod). A postgres `pg_isready`, a backup konténer `pg_isready` a postgres felé, a backend Spring Actuator `/actuator/health` endpointja (testcontainers indításkor healthcheck-nek). A frontend Nginx maga nem ad healthcheck-et, de a `/api/` proxy location egy 502-t ad vissza ha a backend nem elérhető — ezt a docker healthcheck figyeli.

### `docker-compose.yml` konténer részletek:

*   **`postgres` (PostgreSQL 16 Alpine):**
    *   Környezeti változók: `POSTGRES_DB=tanszek_db`, `POSTGRES_USER=admin`, `POSTGRES_PASSWORD=secure_pass`.
    *   Volume: `postgres_data:/var/lib/postgresql/data` (DB perzisztencia).
    *   Healthcheck: `pg_isready -U admin -d tanszek_db` (15s interval).
    *   Non-root user (`postgres`), `mem_limit: 1G`, `cpus: 1.0`, log rotation (`json-file` driver, max-size 10m, max-file 3).
    *   *Megjegyzés:* Az `uploads_data` volume-ot a `backend` konténer mountolja (nem a postgres).
*   **`backend` (Spring Boot 3.3 / Java 21):**
    *   Build: Multi-stage Dockerfile (`maven:3.9-eclipse-temurin-21` -> `eclipse-temurin:21-jre-alpine`).
    *   Környezeti változók: `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/tanszek_db`, JWT kulcsok (lásd §3), titkosítási kulcsok, SMTP konfig.
    *   Függőség: `depends_on` a postgres healthcheck feltétellel.
    *   `mem_limit: 2G`, `cpus: 2.0`, `read_only: true` ahol lehet, **tmpfs mount-ok**: `/tmp` (Spring Boot temp), `/var/run` (PID file), volume mount-ok: `uploads_data:/var/uploads`, `audit_archive_data:/var/backups/archive/audit`. Spring Boot log stdout/stderr-re megy (12-factor app), így nincs külön /var/log mount.
*   **`frontend` (React + Vite + Nginx):**
    *   Build: Node.js multi-stage build, Alpine Nginx statikus fájlokkal.
    *   Port mapping: `80:80`.
    *   Nginx konfig: `client_max_body_size 10M;` a `/api/` location block-ban (request payload limit).
    *   `mem_limit: 512M`, `cpus: 0.5`.
*   **`backup` (pg_dump cron konténer):**
    *   Alpine + bash + postgresql-client + dcron, non-root user (`backup`).
    *   Healthcheck: `pg_isready -h postgres -U admin -d tanszek_db` (15s interval).
    *   `depends_on: postgres: condition: service_healthy`.
    *   **Külön env_file:** a backup konténer `env_file: ./backup.env` direktívával tölti be a **saját .env fájlját** (a repo gyökerében `backup.env`), amelyben csak a backup-specifikus env var-ok vannak (`BACKUP_RETENTION_DAYS`, `POSTGRES_HOST=postgres`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`). A backend és a backup konténer külön .env fájlt használ, így a backup konténer nem fér hozzá a backend titkaihoz (JWT key, SMTP password, stb.).
    *   Napi 02:00 pg_dump, `backup_data:/var/backups/YYYY-MM-DD.sql` (named volume mount, a `read_only: true` mellett).
    *   **Retention script:** a `backup_data:/var/backups/` mappában lévő dump-okat a shell script (`cleanup.sh`) a `BACKUP_RETENTION_DAYS` env var-ból olvassa (a backup.env-ből). Ha a retention-days nincs beállítva, default 30 nap.
    *   `mem_limit: 256M`, `cpus: 0.25`, `read_only: true`.

### Docker Compose Volume-ok (explicit lista)
A docker-compose.yml `volumes:` szekciójában definiálandó:
*   `postgres_data:` — postgres perzisztencia
*   `uploads_data:` — device attachments (backend mountolja `/var/uploads`-ra)
*   `backup_data:` — pg_dump fájlok (backup konténer mountolja `/var/backups`-ra)
*   `audit_archive_data:` — archivált audit log-ok (backend mountolja `/var/backups/archive/audit`-ra, az `AUDIT_ARCHIVE_PATH` configs érték erre mutat)
*   **`mailhog` (dev profile only):**
    *   `mailhog/mailhog` image, SMTP port `1025`, Web UI port `8025`.
    *   **Host port mapping:** `"1025:1025"` (SMTP) és `"8025:8025"` (Web UI) — a fejlesztő böngészőből a `localhost:8025`-ön látja a küldött emaileket.
    *   Csak dev profile-ban aktív (production build-ben kimarad).
    *   `mem_limit: 256M`.

### Email Alerting
*   **Dev profile:** MailHog konténer (SMTP localhost:1025, UI :8025).
    *   **Production:** SMTP konfig env var-okból (`SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `MAIL_FROM`). A backend konténer `env_file: .env` direktívával tölti be a `.env` fájlból (a repo gyökerében), így a docker-compose.yml tiszta marad.
*   **Retry logika:** Spring Retry, 3 attempt, exponential backoff.

---

## 2. Adatbázis Séma és JPA Entitások Részletes Mezői

### 1. `configs`
*   `id` (Long, PK, Identity)
*   `key` (String, Unique, Not Null)
*   `value` (String, Not Null)
*   *Seed értékek (V2__seed.sql):* `AUTH_PROVIDER=LOCAL`, `BACKUP_RETENTION_DAYS=30`, `AUDIT_RETENTION_YEARS=5`, `AUDIT_ARCHIVE_PATH=/var/backups/archive/audit`, `MAX_LOGIN_ATTEMPTS=5`, `LOCKOUT_DURATION_MIN=15`, `JWT_ACCESS_TTL_MIN=15`, `JWT_REFRESH_TTL_DAYS=30`, `JWT_KID_GRACE_PERIOD_SEC=3600`, `PAGINATION_DEFAULT_SIZE=20`, `PAGINATION_MAX_SIZE=50`, `ALERT_EMAIL_RECIPIENT=admin@tanszek.local`.

### 2. `permissions`
*   `id` (Long, PK, Identity)
*   `name` (String, Unique, Not Null)
*   *Teljes lista:* `DEVICE_CREATE`, `DEVICE_READ`, `DEVICE_UPDATE`, `DEVICE_DELETE`, `DEVICE_ASSIGN`, `DEVICE_UNASSIGN`, `USER_MANAGE`, `USER_READ`, `LOCATION_MANAGE`, `LOCATION_READ`, `AUDIT_READ`, `AUDIT_ROLLBACK`, `SOFTWARE_MANAGE`, `SOFTWARE_LICENSE_VIEW` (14 permission).

### 3. `roles`
*   `id` (Long, PK, Identity)
*   `name` (String, Unique, Not Null) - `ROLE_ADMIN`, `ROLE_TEACHER`, `ROLE_STUDENT`.
*   *Role-Permission mapping (V2__seed.sql):*
    *   `ROLE_ADMIN`: minden permission.
    *   `ROLE_TEACHER`: `DEVICE_READ` (saját + row-filter), `DEVICE_ASSIGN`, `DEVICE_UNASSIGN`, `USER_READ` (saját profil), `LOCATION_READ`, `AUDIT_READ`.
    *   `ROLE_STUDENT`: `DEVICE_READ` (saját), `USER_READ` (saját), `LOCATION_READ`.
*   *Factory pattern:* `AuthProviderFactory` a `configs.AUTH_PROVIDER` értéke alapján tölti be a provider implementációt (`LOCAL` → `LocalAuthProvider`, `AD` → `StubAdAuthProvider`).

### 4. `locations`
*   `id` (Long, PK, Identity)
*   `version` (Long, @Version - optimistic lock) - párhuzamos módosítás ellen.
*   `name` (String, Not Null)
*   `parent_id` (Long, FK -> `locations.id`, Nullable) - Korlátlan mélység, körkörös referencia tiltva service-szinten.
*   `type` (ENUM: `CLASSROOM`, `OFFICE`, `STORAGE`, `GROUP`) - *Üzleti szabály: `GROUP` típusú helyre NEM lehet eszközt assignolni (forrás ÉS cél is tilos).*
*   *Service logika:* `LocationService.move()` `@Transactional`, `LocationService.validateNoCycle(parentId, locationId)` rekurzívan ellenőrzi a parent láncot, throw-ol ha ciklust talál. Meghívódik minden create/update előtt. **Optimistic lock retry:** `OptimisticLockException` esetén a service automatikusan retry-olja 3x (külön tranzakcióban), és ha 3. próbálkozásra sem sikerül, `OptimisticLockException`-t dob a GlobalExceptionHandler felé.
*   *Soft delete:* NINCS, hard delete + audit log capture a törlés előtti állapottal.

### 5. `app_users`
*   `id` (Long, PK, Identity)
*   `email_encrypted` (String, Not Null) - AES-GCM titkosított formátum (visszafejthető, admin megjelenítéshez).
*   `email_hash` (String, Unique, Not Null) - SHA-256 hash az egyediséghez, kereséshez és idempotens műveletekhez.
*   `office_location_id` (Long, FK -> `locations.id`, Nullable)
*   `password_hash` (String, Not Null) - Argon2id hashelt jelszó (paraméterek tárolva a hash-ben a rehash detekcióhoz).
*   `active` (Boolean, Not Null) - Ha `false`, a user nem jelentkezhet be.
*   `must_change_password` (Boolean, Not Null, default false) - first-login flag.
*   `role_id` (Long, FK -> `roles.id`, Not Null)
*   `failed_login_count` (Integer, Not Null, default 0) - Brute-force védelemhez.
*   `locked_until` (Timestamp, Nullable) - Account lockout végéig.
*   `password_changed_at` (Timestamp, Not Null) - Jelszócsere tracking (most csak first-login change flag-re szolgál, password expiry policy NINCS).
*   *Password rehash:* Login service minden sikeres bejelentkezéskor ellenőrzi a hash paramétereit, és ha az aktuális policy-től elmaradnak, transparent módon újrahasheli.
*   *Password expiry:* NINCS, csak first-login change kényszerítve a `must_change_password` flaggel.
*   *Deactivation:* `UserService.deactivate()` automatikusan az összes aktív refresh_token.revoked = true-t állítja. Következő kérésnél a user kijelentkezik minden session-ből.
    *   **must_change_password flow:** a demo admin user `must_change_password = true` flaggel jön létre (V2__seed.sql). Belépéskor a frontend **dinamikus redirectet** csinál: ha `mustChangePassword = true` a SecurityContext-ből, a route loader automatikusan a `/password-change` page-re irányít (a routes/index.ts-ben NINCS explicit route, hanem a route loader guard-ja kezeli). A `/password-change` oldalon a jelszócsere törli a flaget és frissíti a `password_changed_at` timestampet.
*   *Kapcsolat:* `@ManyToMany` a `permissions` táblával egyedi extra jogokhoz (`user_permissions` join table: `user_id`, `permission_id`).

### 6. `softwares`
*   `id` (Long, PK, Identity)
*   `name` (String, Not Null)
*   `license_key_encrypted` (String, Not Null) - AES-GCM titkosítás (ugyanaz a `CryptoService`).
*   **License key maszkolás a frontend felé:**
    *   Ha usernek van `SOFTWARE_LICENSE_VIEW` permissionje: teljes visszafejtett érték megjelenítése.
    *   Ha nincs: csak az utolsó 4 karakter + `****-****-****-` prefix (encrypted formából, visszafejtés nélkül, pl. `****-****-****-A8F2`).

### 7. `devices`
*   `id` (Long, PK, Identity)
*   `type` (String, Not Null, max 50 char) - service-réteg validálja (regex `[a-zA-Z0-9\-_]+`, max 50 char). String marad az új típusok rugalmas támogatásához (pl. egyedi tanszéki eszköztípusok).
*   `inventory_number` (String, Unique, Not Null, max 50 char)
*   `status` (ENUM: `PENDING`, `ASSIGNED`, `IN_STORAGE`, `MAINTENANCE`, `DISPOSED`)
*   *Kapcsolat:* `@ManyToMany` a `softwares` táblával (`device_softwares` join table: `device_id`, `software_id`), **cascade nélkül** (egyik oldalon sem). A Device szoftvereit külön service kezeli (assign/unassign), és a Software törlésekor a join tábla bejegyzések manuálisan tisztítandók a service-ben.

### 8. `device_attachments`
*   `id` (Long, PK, Identity)
*   `device_id` (Long, FK -> `devices.id`, Not Null)
*   `file_name` (String, max 255, Not Null)
*   `mime_type` (String, max 100, Not Null)
*   `size_bytes` (Long, Not Null) - max 5MB feltöltéskor validálva.
*   `uploaded_at` (Timestamp, Not Null)
*   `uploaded_by_id` (Long, FK -> `app_users.id`, Not Null)
*   `storage_path` (String, Not Null) - formátum: `./uploads/devices/{device_id}/{uuid}.{ext}` (lokális volume mount, `uploads_data:/var/uploads`).
*   *Limits:* Max 5MB/fájl (validálás upload endpointon), max 5 fájl/device (service check).
*   **Cascade policy:** Device törlésekor a kapcsolódó attachments rekordok ÉS fizikai fájlok is törlődnek (DB cascade + filesystem cleanup). Audit log capture a törlés előtti állapotról.

### 9. `device_assignments` (egyetlen tábla, history-szerű)
*   `id` (Long, PK, Identity)
*   `device_id` (Long, FK -> `devices.id`, Not Null)
*   `from_location_id` (Long, FK -> `locations.id`, Nullable)
*   `to_location_id` (Long, FK -> `locations.id`, Nullable)
*   `from_user_id` (Long, FK -> `app_users.id`, Nullable)
*   `to_user_id` (Long, FK -> `app_users.id`, Nullable)
*   `by_user_id` (Long, FK -> `app_users.id`, Not Null) - Aki létrehozta az assignt.
*   `approved_by_id` (Long, FK -> `app_users.id`, Nullable) - Aki elfogadta.
*   `unassigned_by_id` (Long, FK -> `app_users.id`, Nullable) - Aki kezdeményezte az unassign-t.
*   `unassign_approved_by_id` (Long, FK -> `app_users.id`, Nullable) - Aki elfogadta az unassign-t.
*   `date_of_assignment` (Timestamp, Nullable) - Amikor végbement.
*   `created_date` (Timestamp, Not Null) - Amikor létrehozták.
*   `unassign_date` (Timestamp, Nullable) - Amikor vissza lett véve.
*   `unassign_created_date` (Timestamp, Nullable) - Amikor az unassign létrejött.
*   `status` (ENUM: `IN_STORAGE`, `ASSIGNED`, `PENDING_ASSIGNMENT`, `PENDING_UNASSIGNMENT`)
*   `active` (Boolean, Not Null)
*   *Miért egy tábla:* egyszerűbb JOIN-ok, egy device-hoz egy aktív assignment sor tartozik, history ugyanott. Az unassign mezők NULL-ázódnak aktív állapotban.
*   *Service assert:* Új aktív rekord mindig NULL az unassign_* mezőkön (DTO validáció + service assertion fut create-kor).

### 10. `audit_logs` (Rollback és Nyomkövetés)
*   `id` (Long, PK, Identity)
*   `timestamp` (Timestamp, Not Null)
*   `user_email` (String, Not Null) - A műveletet végző user maszkolt vagy eredeti emailje.
*   `endpoint` (String, Not Null) - Hívott URL path.
*   `method` (String, Not Null) - HTTP metódus (GET, POST, PUT, DELETE).
*   `request_payload` (TEXT, Nullable) - maszkolva.
*   `changes_json` (TEXT, Nullable) - előtte/utána diff JSON rollbackhez.
*   `http_status` (Integer, Not Null)
*   `entity_type` (String, Nullable) - `Device`, `User`, stb. (rollback target azonosítás).
*   `entity_id` (Long, Nullable) - cél entitás ID.
*   **Retention policy:** 1 év után archiválás (export + tömörítés a `/var/backups/archive/audit/YYYY/` mappába, ami az `AUDIT_ARCHIVE_PATH` configs-ból jön és az `audit_archive_data` volume mounton van), 5 év után végleges törlés. Implementálva `@Scheduled` job a backendben, heti 1x (vasárnap 03:00).
*   **Email alert on failure:** ha a retention job elbukik, emailt küld az `ALERT_EMAIL_RECIPIENT`-nek (mailhog dev, production SMTP).

### 11. `refresh_tokens` (Refresh token rotation, RFC 6819 kompatibilis)
*   `id` (Long, PK, Identity)
*   `user_id` (Long, FK -> `app_users.id`, Not Null)
*   `token_hash` (String, Unique, Not Null) - SHA-256 hash a refresh token értékből.
*   `expires_at` (Timestamp, Not Null) - 30 nap a létrehozástól.
*   `revoked` (Boolean, Not Null, default false)
*   `created_at` (Timestamp, Not Null)
*   `replaced_by_id` (Long, FK -> `refresh_tokens.id`, Nullable, **ON DELETE SET NULL**) - rotation chain. Ha egy token törölve van (cleanup), a chain mutató NULL lesz, nem törlődik cascade-szel az összes kapcsolódó token.
*   *Reuse detection:* Ha egy már revoked tokenrel próbálkoznak, az egész chain revokeolódik (`revoked = true` minden kapcsolódó tokenre), user kénytelen újra bejelentkezni.
*   *Cleanup:* `@Scheduled` napi 04:00, törli a **7+ napos** lejárt vagy revoked tokeneket (grace period debug célokra, config-ból olvassa: `refresh.cleanup.retention-days: 7`). Email alert on failure.

### Flyway migrációk
*   **Classpath location:** `backend/src/main/resources/db/migration/` — a Spring Boot classpath-on belül, a Maven build során a JAR-ba csomagolódnak. Standard Spring Boot convention.
*   `V1__init_schema.sql` - A fenti 11 tábla + minden táblánál explicit `created_at` + `updated_at` mezők (`TIMESTAMP NOT NULL DEFAULT NOW()`). A Flyway nem ismeri a JPA `@MappedSuperclass`-t, ezért a séma fájlban explicit definiálni kell őket. **A `DEFAULT NOW()` backup mechanizmus** — a JPA Auditing runtime felülírja az értéket save-kor (`@PrePersist`, `@PreUpdate` callback-eken keresztül), de a DB default backup ha a JPA valamiért nem állítaná be (defense in depth). A `refresh_tokens.created_at`, `audit_logs.timestamp`, `device_assignments.created_date` egyedi timestamp-ek, nem a BaseEntity-ből jönnek — azok NOT NULL, de nincs DEFAULT NOW(), az alkalmazás állítja be őket.
*   `V2__seed.sql` - 14 permission (beleértve `LOCATION_READ`), 3 role + mapping, 12 configs alapérték, demo admin user (`admin@tanszek.local` / `ChangeMe123!` **pre-hashed Argon2 string** — memory=65536, iterations=3, parallelism=1, `must_change_password = true`). Implementációkor az Argon2 hash-t egyszer generáljuk a `ChangeMe123!` jelszóhoz és a hash string beilleszthető a seed.sql INSERT INTO parancsába.
*   **Nincs Flyway undo** (`U1`, `U2`) — a rollback az `audit_logs.changes_json` alapján történik a `POST /api/audit/rollback/{id}` endpointon. A Flyway rollback csak a teljes DB restore (`scripts/backup-restore.sh`) esetén releváns.
*   **Baseline policy:** `flyway.baseline-on-migrate: false`. Ha a DB üres, minden migráció lefut. Ha létező DB van és nincs Flyway tábla, a Flyway hibát dob — manuális `flyway baseline` parancs szükséges. Tiszta indulás filozófia.
*   **Repair policy:** `spring.flyway.repair-on-migrate: false` (default, biztonságos). Ha checksum mismatch van, a Flyway hibát dob — manuális `flyway repair` parancs kell a CI/CD pipeline-ban, hogy ne csúszjanak át a drift-ek.
*   **Flyway history tábla:** `flyway_schema_history` a default `public` sémában. Az admin userek (USER_MANAGE permission) lekérdezhetik a migration history-t debug célokra.

### 12. `BaseEntity` (közös ősosztály, @MappedSuperclass)
*   `id` (Long, PK, Identity) — azonosító, minden entitás örökli.
*   `created_at` (Timestamp, Not Null) — `@CreatedDate` JPA Auditing tölti.
*   `updated_at` (Timestamp, Not Null) — `@LastModifiedDate` JPA Auditing tölti.
*   **Implementáció:** `@MappedSuperclass` abstract class a `com.tanszek.device.common` package-ben, minden konkrét entitás `@EntityListeners(AuditingEntityListener.class)` annotációval örökli. A backend alkalmazás osztályban `@EnableJpaAuditing` annotáció aktív. Standard Spring Data JPA pattern — a JPA automatikusan kezeli az audit mezőket, a Flyway V1__init_schema.sql-ben explicit definiálni kell őket minden táblánál.

---

## 3. Backend Architektúra & Biztonsági Komponensek

### 3.0. Tervezési Elvek (SOLID + DRY/KISS/YAGNI)

A rendszer tervezése és implementációja során az alábbi elveket követjük:

*   **SOLID:**
    *   **S (Single Responsibility):** Minden service osztály egy felelősséggel bír — `AuthService` csak auth, `DeviceService` csak device műveletek, `AuditService` csak audit log. A `CryptoService` csak titkosítás, semmi más.
    *   **O (Open/Closed):** Az `AuthProvider` interface lehetővé teszi új provider-ek (LDAP, OAuth2) hozzáadását anélkül, hogy a meglévő kódot módosítanánk. A `@RequirePermission` aspektus új permission típusokkal bővíthető a meglévő metódusok módosítása nélkül.
    *   **L (Liskov Substitution):** A `LocalAuthProvider` és `StubAdAuthProvider` (jövőbeli `LdapAuthProvider`) helyettesítheti egymást az `AuthProvider` interface-en keresztül, a hívó kód változatlan marad.
    *   **I (Interface Segregation):** A service-ek kicsi, fókuszált interfészeket implementálnak (pl. `UserRepository extends JpaRepository<AppUser, Long>`, `DeviceRepository extends JpaRepository<Device, Long>`). Nincs "fat interface" — minden repository csak a saját entitásához tartozó műveleteket tartalmazza.
    *   **D (Dependency Inversion):** A service-ek a repository-kat és más service-eket interface-en keresztül kapják (constructor injection). A concrete implementációk (JpaRepository) helyettesíthetők mock-okkal a tesztekben. A `CryptoService` is interface mögött van, így a teszt in-memory implementációt használhat. A Spring stereotype annotációk (`@Service`, `@Repository`, `@Component`) biztosítják az automatikus bean regisztrációt és a constructor injection-t.
*   **DRY (Don't Repeat Yourself):** Közös util-ok a `com.tanszek.device.common` package-ben (BaseEntity, GlobalExceptionHandler, MessageKey constants). A `RowLevelSecurityPredicate` helper újrahasznosítható minden repository-ban. A `validateNoCycle`, `upgradeEncoding` stb. service-szinten deduplikált.
*   **KISS (Keep It Simple, Stupid):** Egyszerű implementáció, nem over-engineered. Egy service egy felelősséggel. Nincs absztrakció feleslegesen. A custom `@RequirePermission` helyett a Spring natív `@PreAuthorize`-ot használnánk, ha nem lenne a row-level filter is ott — de a row-level check miatt kell a custom aspektus.
*   **YAGNI (You Aren't Gonna Need It):** A Future Considerations lista (17+ elem) explicit dokumentálja, hogy mi NEM része a jelenlegi scope-nak: 2FA, OIDC/SSO, PWA, HA deployment, WebSocket, stb. Ne építsünk bele semmit, ami nem kell most — csak ha a use case felmerül.

*   **`CryptoService`:** AES-GCM (256 bites kulccsal) implementáció — `email_encrypted` és `license_key_encrypted` mezőkhöz, valamint SHA-256 hashelés az `email_hash` és `refresh_tokens.token_hash` generálásához.

*   **AuthProvider Pattern:** `AuthProvider` interface, `LocalAuthProvider` (Argon2 + DB) az induló implementáció, `StubAdAuthProvider` placeholder az AD integrációhoz (Future Work). `AuthProviderFactory` a `configs.AUTH_PROVIDER` értéke alapján tölti be az aktív providert. **Runtime váltás** `@RefreshScope` annotációval (Spring Cloud Context dependency) — ha a configs.AUTH_PROVIDER értéke megváltozik és az Actuator `/actuator/refresh` endpoint hívódik, a factory újra betölti a providert újraindítás nélkül.

*   **Argon2PasswordEncoder + Rehash:** Spring Security 6 integráció. A LoginService.authenticate() metódus sikeres hitelesítés után ellenőrzi az `Argon2PasswordEncoder.upgradeEncoding(hash)` metódussal, hogy a hash paraméterei (memory, iterations, parallelism) megfelelnek-e a policy-nek. Ha igen, transparent `passwordEncoder.encode(rawPassword)` hívással újrahasheli és DB-be írja. Az egész egy tranzakcióban fut.
    *   **Miért Argon2id (és nem bcrypt):** memóriaigényes (memory-hard), GPU/ASIC-resistant, PHC string format a paraméterek tárolására. OWASP 2024+ ajánlás. Bcrypt CPU-only, GPU támadás ellen gyengébb.

*   **JWT Auth (Access + Refresh, kid alapú rotáció):**
    *   **Security Filter Chain rendje (SecurityConfig):** 1) **RequestIdFilter** (UUID `request_id` generálás vagy `X-Request-ID` header átvétel kliensről, MDC-be rakja, response `X-Request-ID` headerben visszaadja, RateLimitFilter előtt, a lánc legelején), 2) **RateLimitFilter** (Bucket4j per-IP/per-email limit a `/api/auth/login` endpointon, CsrfFilter ELŐTT fut, hogy a brute-force botok ne kapjanak CSRF tokent), 3) **CsrfFilter** (CSRF token check a `CookieCsrfTokenRepository`-val, state-changing endpointokon), 4) **JwtAuthenticationFilter** (Bearer token validáció, SecurityContext betöltés), 5) **UsernamePasswordAuthenticationFilter** (csak `/api/auth/login` endpointon), 6) **ExceptionTranslationFilter** (authentication/authorization hibák kezelése), 7) **AuthorizationFilter** (`@RequirePermission` annotáció és URL-alapú permission check). A RateLimitFilter ha a bucket üres, `throw new BusinessValidationException("rateLimitExceeded", ...)` kivételt dob, amit a GlobalExceptionHandler a szokásos JSON formátumban adja vissza (timestamp, status: 429, error, message, messageKey, path, details).
    *   **Access token:** 15 perc élettartam, HS256 aláírás, `kid` header.
    *   **Refresh token:** 30 nap, DB-ben tárolva a `refresh_tokens` táblában.
    *   **JwtAuthenticationFilter (extends OncePerRequestFilter):** minden request-en kiolvassa a `Authorization: Bearer <token>` headert, validálja a `JwtTokenProvider`-rel (signature + expiry + kid). Ha valid, betölti a `UserDetails`-t az adatbázisból a `CustomUserDetailsService.loadUserByUsername(email)` hívással (ami az `AppUserRepository.findByEmail(email_hash)`-szel keres, és visszaadja a role-okat `ROLE_` prefix-szel + a permission-öket mint `GrantedAuthority`-kat), és beállítja a `SecurityContextHolder`-ben az `UsernamePasswordAuthenticationToken`-t. **Ha a token invalid vagy lejárt, a filter nem dob kivételt** — csak `log.debug()` bejegyzést ír, és a lánc folytatódik. Spring Security később (a védett endpointokon) `401 Unauthorized`-t ad vissza a `BearerTokenAuthenticationEntryPoint` által, a frontend pedig silent refresh-t indít.
    *   **CustomUserDetailsService implements UserDetailsService:** az `AppUserRepository.findByEmail(email_hash)` hívással keres (NEM `email_encrypted`-et használja, mert az visszafejtés lassú lenne minden request-en — az `email_hash` SHA-256 gyors keresésre). Az `AppUser` entitáson van egy `@ManyToMany` `permissions` field, ami a `user_permissions` join táblát tölti lazy/eager módon (alapértelmezetten eager a Security betöltéshez). A `CustomUserDetails` tartalmazza a role-okat (`ROLE_` prefix-szel), a role-permission-öket (a `role` entitás `@ManyToMany` `permissions` field-jéből), ÉS a user-specifikus permission-öket (a `user_permissions` join táblából) — union merge-ölve egy Set-be. A `UserDetails.getAuthorities()` visszaadja a role-ok + az összes permission listáját, amit a `@RequirePermission` aspektus ellenőriz.
    *   **kid (Key ID) alapú rotáció:** A JWT header tartalmaz egy `kid` mezőt. A `JwtTokenProvider` `@RefreshScope` bean, egy `Map<String, SecretKey>`-ben tartja az aktív és az előző secretet. Új tokenek az aktuális `kid`-vel íródnak, validáció mindkettőt elfogadja. A `PreviousKeyConfig` bean `@ConditionalOnProperty(name = "jwt.kids.previous", matchIfMissing = false)` — ha nincs previous key env var-ban, a bean nem töltődik be. Config: `application.yml`-ben `jwt.kids.active` és `jwt.kids.previous` env var-okból. **Grace period: 1 óra** (config: `JWT_KID_GRACE_PERIOD_SEC=3600`). Rotáció: secret cseréje + restart, a régi 1 órán át még érvényes.
    *   **Refresh rotation:** Minden `/api/auth/refresh` híváskor új refresh token generálódik, a régi `revoked = true` lesz, és `replaced_by_id` mutat az újra. Reuse detection: ha revoked tokent használnak, az egész chain revokeolódik.
    *   **Endpoints:** `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout`, `POST /api/auth/password-change`.
    *   **Cookie-alapú refresh token tárolás:** A backend a refresh tokent **HttpOnly + Secure + SameSite=Strict** cookie-ban adja vissza (`Set-Cookie: refresh_token=...; HttpOnly; Secure; SameSite=Strict; Path=/api/auth; Max-Age=2592000`). A legszűkebb path scope, csak a `/api/auth/*` endpointokra megy (login, refresh, logout, password-change). XSS ellen védett (JavaScript nem fér hozzá). A frontend **soha nem storage-ban tárolja** a refresh tokent. Az access token memory-ban (Zustand/React Context) marad a session idejére.
    *   **CSRF védelem:** **Spring Security CSRF token bekapcsolva minden state-changing műveletre** (POST, PUT, DELETE, PATCH). Az auth endpointokon (`/api/auth/login`, `/api/auth/refresh`) a CSRF token kikapcsolva, mert ott nincs még session. **Implementáció:** `CookieCsrfTokenRepository.withHttpOnlyFalse()` — a CSRF token cookie-ban tárolódik `XSRF-TOKEN` névvel, HttpOnly=false hogy a frontend JavaScript hozzáférjen. Frontend Axios interceptor minden state-changing request-hez a cookie-ból olvassa és `X-XSRF-TOKEN` headerbe rakja. Spring Security automatikusan validálja a cookie és header egyezését. Defense in depth a SameSite cookie védelem mellett.
    *   **Silent refresh (frontend):** Axios interceptor 401 esetén automatikus `POST /api/auth/refresh` hívás (a cookie automatikusan csatolódik, `withCredentials: true`), retry az eredeti kéréssel. **Refresh-in-progress lock + queue:** ha több párhuzamos kérés egyszerre kap 401-et, csak az első indít refresh-t, a többi ugyanarra a promise-re várakozik és utána retry-ol. Csak a refresh failure esetén redirect a `/login` route-ra — nincs user-facing warning modal.

*   **Brute-force védelem (Bucket4j):**
    *   Per-IP limit: 5 próba/perc a `/api/auth/login` endpointon.
    *   Per-email limit: 10 próba/óra.
    *   5 egymás utáni hibás próba után account 15 percre lockolódik (`failed_login_count >= 5` → `locked_until = now + 15min`).
    *   **Admin unlock endpoint:** `POST /api/admin/users/{id}/unlock` — csak `USER_MANAGE` permissionnel rendelkező user hívhatja. Beállítja `failed_login_count = 0` és `locked_until = NULL`. Audit log bejegyzés generálódik.
    *   **RateLimitFilter (extends OncePerRequestFilter)** a Security Filter Chain legelején (a CsrfFilter előtt). Bucket-ek in-memory `ConcurrentHashMap<String, Bucket>` alapú, kulcs az IP-cím vagy az email SHA-256 hash-e. Ha a bucket üres, a filter `BusinessValidationException("rateLimitExceeded", ...)` kivételt dob, amit a GlobalExceptionHandler 429-es JSON válaszként ad vissza.
    *   Bucket state-ek in-memory (egyetlen backend instance esetén), HA deployment esetén Redis backend.

*   **Backend i18n (camelCase kulcsok):**
    *   Spring `MessageSource` ResourceBundle-ből (`messages_hu.properties`, `messages_en.properties` fájlok a `backend/src/main/resources/` mappában).
    *   **Kulcsformátum:** camelCase mindkét oldalon (`deviceNotFound`, `userEmailDuplicate`, `assignmentNotApproved`). Backend ResourceBundle property file-okban camelCase kulcsok, frontend i18next resource fájlokban (`hu.json`, `en.json`) ugyanazok a kulcsok.
    *   **Code generator (saját implementation):** Maven plugin (custom) build-time generálja:
        1. `frontend/src/lib/i18n/i18n-keys.ts` — TypeScript union type az összes kulccsal: `export type MessageKey = 'deviceNotFound' | 'userEmailDuplicate' | 'assignmentNotApproved' | ...`
        2. `frontend/src/lib/i18n/i18n-defaults.json` — dict (key → default hu szöveg, fallback a frontend resource fájlokhoz).
        A backend `mvn package` build során a plugin fut, és a generált fájl a frontend `src/lib/i18n/` mappájába kerül. A frontend `npm run build` a backend build után automatikusan felhasználja. A frontend resource fájlok (`hu.json`, `en.json`) kézzel szerkeszthetők maradnak, csak a kulcs-lista van szinkronizálva a code generator által. CI-ban type-check fut a drift ellen.
    *   **Locale feloldás:** User choice (localStorage) > `Accept-Language` header > `hu` fallback.
    *   **Validation üzenetek** is i18n ResourceBundle-ből jönnek.

*   **GlobalExceptionHandler & Custom Exceptions:**
    *   `@RestControllerAdvice` annotációval, és minden exception típushoz külön `@ExceptionHandler`: `@ExceptionHandler(BusinessValidationException.class)`, `@ExceptionHandler(ResourceNotFoundException.class)`, `@ExceptionHandler(UnauthorizedActionException.class)`, `@ExceptionHandler(OptimisticLockException.class)`, `@ExceptionHandler(MethodArgumentNotValidException.class)` (validation hibák), `@ExceptionHandler(Exception.class)` (fallback).
    *   **Validáció:** A controller method-ok `@Valid @RequestBody CreateUserDto user` formában fogadják a DTO-t. A Bean Validation annotációk (`@NotNull`, `@Size`, `@Email`, `@Pattern`) a DTO mezőin vannak. A `MethodArgumentNotValidException`-t a GlobalExceptionHandler elkapja, és a `details` tömbben visszaadja a mező-hibákat: `{field: "email", message: "not valid", rejectedValue: "abc"}`. A frontend ebből tudja, melyik mező hibás.
    *   Saját kivétel-hierarchia: `ResourceNotFoundException`, `BusinessValidationException`, `UnauthorizedActionException`, `OptimisticLockException` (location move retry).
    *   Egységes válasz body: `{timestamp, status, error, message, messageKey, path, details}`. A `messageKey` az i18n fordítás kulcsa.

*   **Request payload méretkorlát (háromszintű):**
    *   **Nginx** (`frontend` konténer): `client_max_body_size 10M;` a `/api/` location block-ban.
    *   **Spring Boot:** `spring.servlet.multipart.max-file-size=10MB`, `max-request-size=10MB`.
    *   **Bean Validation:** `@Size(max = ...)` minden nagy String mezőn a DTO-kban (pl. megjegyzések, nevek: max 255-500 char).

*   **Row-level szűrés (MINDEN műveletre, nem csak olvasásra):**
    *   **DEVICE_READ:** STUDENT → csak ahol `device_assignment.to_user_id = current_user`, TEACHER → saját + saját `office_location_id`-jában lévő eszközök, ADMIN → minden.
    *   **DEVICE_UPDATE/DELETE/ASSIGN/UNASSIGN:** ugyanaz a filter aktív — user csak a saját eszközein dolgozhat (row-level check service assertion-ben).
    *   `RowLevelSecurityPredicate` helper, JpaSpecificationExecutor + service assertion íráskor is.
    *   **Defense in depth:** permission check + row-level check együtt fut, mindkettő kell a sikeres művelethez.

*   **Dinamikus Szűrés:** Spring Data JPA `JpaSpecificationExecutor` a predikátumok építésére a frontend szűrési paramétereiből, `Pageable` lapozással (max 50/page, config: `PAGINATION_MAX_SIZE`).

*   **AOP Audit Interceptor:** Spring AOP `@AfterReturning` advice a service metódusokon (pl. `DeviceService`, `UserService`), ahol az entity paraméter vagy return value alapján azonosítja az entity_type-ot (`entity.getClass().getSimpleName()` → "Device", "User", stb.) és entity_id-t (`entity.getId()`). Automatikusan figyeli a tranzakciókat, entity_type + entity_id capture, maszkolja a kulcsszavakat (**case-insensitive**: `password`, `secret`, `token`, `license_key` — beleértve a `license_key_encrypted`, `password_hash`, `token_hash`, `refresh_token` stb. suffix-es formákat is, mivel a substring egyezés aktív) -> `***`, beírja az `audit_logs` táblába a `changes_json` diffet. **Formátum:** `{"before": {...entity state előtte vagy null create esetén...}, "after": {...entity state utána vagy null delete esetén...}}`. A rollback az `after`-t visszaállítja `before`-ra (update esetén), törli az entitást (ha az `after` null és `before` nem, az create rollback), vagy visszaállítja a törölt entitást (ha `before` nem null és `after` null, az delete rollback). A rollback során a `changes_json` deserialize-éhez egy típus-leképező helper (`EntityTypeRegistry`) van: `entity_type = "Device"` → `Device.class`, stb.

*   **Audit Retention Job:** `@Scheduled(cron = "0 0 3 ? * SUN")` heti futás vasárnap 03:00, archiválja a 1+ éves rekordokat, törli az 5+ éveseket. **Email alert on failure.**

*   **Audit Rollback Endpoint:** `POST /api/audit/rollback/{audit_log_id}` — csak `AUDIT_ROLLBACK` permission-nel (alapértelmezetten csak ROLE_ADMIN). Csak bizonyos entitás-típusokra engedélyezett: **Device, User, Location, Assignment, Software, Attachment**. A rollback a `changes_json`-ben tárolt diff inverzét alkalmazza, és egy új audit log bejegyzést generál. **Tranzakció:** `@Transactional` (REQUIRED propagation), az egész rollback egy tranzakcióban fut: az entitás visszaállítása + az új audit log bejegyzés együtt commit-olódik. Ha bármi hiba, rollback az egész (all-or-nothing).

*   **Scheduled Job Monitoring:**
    *   Minden `@Scheduled` metódus (backup pg_dump, audit retention, refresh token cleanup) try-catch wrapperben fut.
    *   Hiba esetén email alert az `ALERT_EMAIL_RECIPIENT`-nek (mailhog dev, production SMTP).
    *   Alert template: job neve, hibaüzenet, stack trace, timestamp.

*   **Logging (Structured JSON):**
    *   Logback + `logstash-logback-encoder` JSON layout.
    *   **MDC mezők:** `request_id` (RequestIdFilter generálja UUID-val vagy átveszi a kliens `X-Request-ID` headeréből, response-ban is visszaadja), `user_id`, `user_email`, `endpoint`, `method`, `timestamp`, `trace_id`, `span_id` (OpenTelemetry + Micrometer Tracing).
    *   OpenTelemetry auto-instrumentation Spring Boot starter, OTLP exporter: dev → console, production → env var (`OTLP_ENDPOINT`, pl. Jaeger/Tempo).

*   **Mail Konfig:** Spring Mail starter, Spring Retry 3 attempt exponential backoff. Dev → MailHog SMTP :1025, production → env var SMTP konfig.

---

## 4. Frontend Architektúra (React + Vite + Tailwind + shadcn/ui + i18next)

*   **Build tool:** Vite + React 18 + TypeScript. **Backend build tool: Maven** (korábban Gradle is szóba jött, de Maven maradt a Spring Boot projekthez jobban illeszkedő konvenciók miatt).
*   **UI Library:** shadcn/ui (copy-paste komponensek Radix UI primitíveken) + Tailwind CSS.
*   **Theme:** shadcn dark mode + next-themes (system preference alapértelmezetten, manuális toggle a header-ben).
*   **Vite Dev Proxy:** `vite.config.ts`-ben `server.proxy['/api'] = 'http://localhost:8080'` — dev módban same-origin élmény (Vite :5173 → Spring Boot :8080). Prod-ban Nginx reverse proxy az `/api`-ra.
*   **Nincs CORS config:** Mind dev (Vite proxy), mind prod (Nginx reverse proxy) same-origin, így a Spring Security CORS config felesleges. **CSRF aktív, CORS kikapcsolva** — same-origin esetén a CSRF token véd és a SameSite=Strict cookie, CORS nem kell. Ha a jövőben külön subdomainekre kerül a frontend/backend, újra kell gondolni mindkettőt.
*   **Nginx Config (frontend konténer, részletes):**
    ```nginx
    server {
        listen 80;
        server_name _;
        root /usr/share/nginx/html;
        index index.html;

        # Security headers
        add_header X-Frame-Options "SAMEORIGIN" always;
        add_header X-Content-Type-Options "nosniff" always;
        add_header Referrer-Policy "strict-origin-when-cross-origin" always;
        add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; connect-src 'self';" always;

        # Gzip
        gzip on;
        gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript;
        gzip_min_length 1000;

        # API reverse proxy
        location /api/ {
            proxy_pass http://backend:8080;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            client_max_body_size 10M;
        }

        # Static asset caching
        location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
            expires 1y;
            add_header Cache-Control "public, immutable";
        }

        # SPA fallback
        location / {
            try_files $uri $uri/ /index.html;
        }
    }
    ```
*   **i18n:** `react-i18next` két nyelvvel (`hu` alapértelmezett, `en`). Locale prioritás: user choice (localStorage) > `Accept-Language` header > `hu` fallback. Backend hibaüzenetek a `messageKey` alapján a frontend resource fájlokban is megvannak fordítva.

### Frontend Package Struktúra
```
frontend/src/
├── features/               # Feature-based, párhuzamosan a backend-del
│   ├── auth/
│   │   ├── components/     # LoginForm, PasswordChangeForm
│   │   ├── hooks/          # useAuth, useLogin
│   │   ├── pages/          # LoginPage, PasswordChangePage
│   │   └── api/            # authApi.ts (login, refresh, logout)
│   ├── user/
│   │   ├── components/
│   │   ├── hooks/
│   │   ├── pages/          # UserListPage, UserEditPage
│   │   └── api/
│   ├── device/
│   │   ├── components/
│   │   ├── hooks/
│   │   ├── pages/          # DeviceListPage, DeviceDetailPage
│   │   └── api/
│   ├── location/
│   │   ├── components/
│   │   ├── hooks/
│   │   ├── pages/
│   │   └── api/
│   ├── software/           # (ua. struktúra)
│   ├── assignment/
│   ├── attachment/
│   └── audit/
├── components/             # Újrahasználható UI
│   ├── ui/                 # shadcn komponensek (Button, Input, Dialog, stb.)
│   ├── DataTable/          # Lapozható, szűrhető táblázat wrapper
│   ├── DiffViewer/         # JSON diff pretty-print megjelenítő
│   └── SonnerWrapper/      # i18n-aware Sonner toast (messageKey → i18next)
├── lib/                    # Közös util-ok
│   ├── api/                # axios instance, interceptors
│   ├── i18n/               # i18next konfig, hu.json, en.json, i18n-keys.ts (generált)
│   ├── theme/              # next-themes setup
│   └── validation/         # Zod sémák a DTO-khoz
├── routes/                 # React Router route konfig (központi)
│   └── index.ts            # routes tömb: [{ path: '/login', component: LoginPage, protected: false }, { path: '/my-dashboard', component: DashboardPage, protected: true }, { path: '/admin', component: AdminPage, protected: true, roles: ['ROLE_ADMIN'] }, { path: '/admin/users', component: UsersPage, protected: true, roles: ['ROLE_ADMIN'], permissions: ['USER_MANAGE'] }, ...]
├── hooks/                  # Globális hook-ok (useDebounce, stb.)
├── types/                  # TypeScript típusdefiníciók
├── App.tsx
└── main.tsx
```
*   **HTTP Kliens (Axios):**
    *   Interceptor Bearer token csatolással.
    *   **Silent refresh:** 401 válasz esetén automatikus `POST /api/auth/refresh` hívás, retry az eredeti kéréssel. Csak refresh failure esetén redirect `/login`-re. **Nincs user-facing warning modal** — a user nem érzékeli a frissítést.
    *   **Globális hibakezelő interceptor:** minden 4xx/5xx-re shadcn Sonner toast notification. A Sonner wrapper komponens a `messageKey`-t próbálja lefordítani i18next-tel. Ha a frontend resource fájlban nincs hozzá fordítás (drift esetén), a backend response `message` mezője jelenik meg fallback-ként (a backend mindig kitölti).
    *   **React Error Boundary:** A route-ok köré wrap-elt Error Boundary komponens. Ha egy komponens runtime error-t dob (pl. undefined state, render hiba), a boundary elkapja, megjelenít egy fallback UI-t (hibaüzenet + "Vissza a főoldalra" gomb), és logolja az error-t a backend `POST /api/audit/error` endpointján (vagy lokálisan konzolra). A Sonner toast az API hibákra, az Error Boundary a render hibákra.
*   **Állapotkezelés:** TanStack Query (`useQuery`, `useMutation`) szerveroldali cache, lapozás, aszinkron hívások.
*   **Felhasználói Felületek:**
    *   Bejelentkezési oldal (HU/EN választó, theme toggle).
    *   `must_change_password = true` esetén: belépés után kötelező redirect `/password-change` route-ra.
    *   Saját Dashboard (`/my-dashboard`): bejelentkezett user saját eszközei, hozzárendelései.
    *   Admin felületek: CRUD táblázatok (users, devices, locations, softwares), audit log viewer, Excel import.
*   **Excel import UI:** `Upload → Preview → Confirm` flow:
    1. Fájlfeltöltés (drag-drop vagy click).
    2. Backend validáció (`/api/import/preview`) visszaadja az érvényes/érvénytelen sorokat.
    3. User átnézi, javítja a fájlt, újra feltölti.
    4. Confirm gombra idempotens import fut (`/api/import/execute`).
    5. Eredmény riport: inserted/updated/skipped/errors.
    *   **Service helye:** külön `com.tanszek.device.import` feature package — `ImportController`, `ImportService`, `ImportPreviewResponse`, `ImportExecuteResponse` DTO-k.
    *   **Biztonság:** `USER_MANAGE` permission kell (csak admin tölthet fel). Mime type check (`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` + `.xlsx` extension), max 10MB. CSRF token standard.
    *   **Concurrency:** `ImportService.execute()` `synchronized` metódus vagy `LockManager` singleton — egyszerre csak egy import futhat. Ha egy másik import kérés jön, 409 Conflict válasz. Megelőzi a unique constraint violation-öket és a deadlock-okat.
*   **Audit log viewer:** Szűrés (felhasználó, endpoint, dátum, HTTP státusz), lapozás, sor kattintásakor side panel a `changes_json` diff pretty-print megjelenítésével, rollback gomb (csak jogosult entitás-típusoknál).
*   **Device attachments UI:** Drag-drop upload, preview grid, delete, kliens-oldali mime/size validation (5MB, elfogadott mime típusok).
*   **Standard SPA**, nincs PWA / service worker / offline mód.
*   **Frontend env változók:** `frontend/.env` fájlban, `VITE_` prefix-szel a Vite beépíti a bundle-be. Példa: `VITE_API_BASE_URL=http://localhost:8080`, `VITE_APP_NAME=device-storage`. A backend URL nem kritikus, mert a Vite proxy (`server.proxy['/api']`) átirányítja a kéréseket. Production build esetén a frontend az Nginx reverse proxy-n át éri el a backendet (same-origin).

---

## 5. Tesztelési Stratégia & CI (GitHub Actions)

*   **Unit tesztek:** Service réteg JUnit 5 + Mockito, min. 70% lefedettség (Jacoco threshold gate).
*   **Integration tesztek:** `@SpringBootTest` + **Testcontainers** (valódi PostgreSQL konténer minden futtatásnál).
*   **Controller tesztek:** `@WebMvcTest` + MockMvc (auth flow, CRUD, jogosultság-ellenőrzés, rate limiting, rollback, refresh rotation reuse detection, row-level filter minden műveletre, attachment cascade).
*   **Smoke teszt:** `scripts/smoke-test.sh` docker-compose up + scripted curl a kritikus endpointokra.

*   **CI pipeline (`.github/workflows/ci.yml`):**
    1. **Spotless** (google-java-format) — Java formázás
    2. **Checkstyle** (saját ruleset) — linting
    3. **OWASP Dependency-Check** — security vulnerabilities
    4. **Unit tests** (mvn test, Jacoco coverage ≥70% gate)
    5. **Integration tests** (Testcontainers, mvn verify -Pintegration)
    6. **Docker build** (2 saját image: backend + frontend, multi-stage cache; postgres/backup/mailhog official image-ek pull-al)
    7. **Smoke test** (docker-compose up + scripted curl, exit code check)
    *   Spotless + Checkstyle + OWASP párhuzamosan futtathatók, majd unit + integration szekvenciálisan, végül docker build + smoke.
    *   Status check kötelező a merge-hez.

---

## 6. Nem-funkcionális Követelmények

*   **Teljesítmény:** Lapozás max 50 sor/page, válaszidő < 500ms a tipikus listázó endpointokon.
*   **Hibakezelés:** Minden backend hiba egységes JSON formátumban, frontend Sonner toast notification a `messageKey` i18n fordításával.
*   **Audit:** Minden írási művelet automatikusan naplózva, rollback elérhető.
*   **Hozzáférhetőség:** WCAG 2.1 AA (shadcn alapból támogatja).
*   **OpenAPI:** Springdoc OpenAPI starter, JWT bearer security scheme definiálva. `/v3/api-docs` és `/swagger-ui.html` elérhető dev profile-ban.

---

## 7. Jövőbeli Megfontolások (Future Work)

Lásd `agent_progress.md` "Future Considerations" szekció.

---

## 8. Konfiguráció (application.yml + Profile-ok)

Build tool: **Maven** (a Spring Boot projekthez jobban illeszkedő konvenciók, code generator plugin is Maven plugin lesz).

### Profile-ok
*   `application.yml` (közös, profile-független alapbeállítások)
*   `application-dev.yml` (dev profile, SPRING_PROFILES_ACTIVE=dev)
*   `application-prod.yml` (prod profile, SPRING_PROFILES_ACTIVE=prod)

### `application.yml` (kulcs lista, env var referenciákkal)

```yaml
spring:
  application:
    name: device-storage
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://postgres:5432/tanszek_db}
    username: ${SPRING_DATASOURCE_USERNAME:admin}
    password: ${SPRING_DATASOURCE_PASSWORD:secure_pass}
    hikari:
      maximum-pool-size: ${DB_POOL_MAX:10}
      minimum-idle: ${DB_POOL_MIN:5}
      connection-timeout: 30000      # 30s
      idle-timeout: 600000           # 10min
      max-lifetime: 1800000          # 30min
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        jdbc.time_zone: UTC
        format_sql: false
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false  # tiszta indulás, manuális baseline parancs szükséges ha nem üres a DB
    repair-on-migrate: false     # biztonságos: checksum mismatch esetén hiba, manuális repair parancs kell
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
  data:
    web:
      pageable:
        default-page-size: ${PAGINATION_DEFAULT_SIZE:20}
        max-page-size: ${PAGINATION_MAX_SIZE:50}

server:
  port: 8080
  error:
    include-message: always
    include-binding-errors: always

# Spring Cloud (RefreshScope)
spring.cloud.refresh.enabled: ${SPRING_CLOUD_REFRESH_ENABLED:true}

# Actuator endpoints
management:
  endpoints:
    web:
      exposure:
        include: health,info,refresh
  endpoint:
    health:
      show-details: when-authorized
    refresh:
      enabled: true

# JWT
jwt:
  access-token-ttl-min: ${JWT_ACCESS_TTL_MIN:15}
  refresh-token-ttl-days: ${JWT_REFRESH_TTL_DAYS:30}
  grace-period-sec: ${JWT_KID_GRACE_PERIOD_SEC:3600}
  kids:
    active: ${JWT_KID_ACTIVE}
    previous: ${JWT_KID_PREVIOUS:}

# Crypto
crypto:
  aes-key: ${CRYPTO_AES_KEY}     # 256-bit Base64
  argon2:
    salt-length: 16
    hash-length: 32
    parallelism: 1
    memory-kb: 65536
    iterations: 3

# Upload (device attachments)
upload:
  base-path: ${UPLOAD_BASE_PATH:/var/uploads}
  max-file-size-bytes: 5242880   # 5MB
  max-files-per-device: 5

# Backup retention
backup:
  retention-days: ${BACKUP_RETENTION_DAYS:30}

# Audit retention (archive path a `configs` DB táblából olvasva, nem itt)
audit:
  retention-years: ${AUDIT_RETENTION_YEARS:5}
  alert-email-recipient: ${ALERT_EMAIL_RECIPIENT:admin@tanszek.local}

# Refresh token cleanup (grace period debug-hoz)
refresh:
  cleanup:
    retention-days: ${REFRESH_CLEANUP_RETENTION_DAYS:7}
```

### `application-dev.yml` (dev-only)

```yaml
spring:
  mail:
    host: localhost
    port: 1025           # MailHog
    username: ""
    password: ""
    properties:
      mail.smtp.auth: false
      mail.smtp.starttls.enable: false

otel:
  exporter:
    otlp:
      endpoint: ""       # dev: console output (Micrometer Tracing default)

logging:
  level:
    root: DEBUG
```

### `application-prod.yml` (prod)

```yaml
spring:
  mail:
    host: ${SPRING_MAIL_HOST}
    port: ${SPRING_MAIL_PORT:587}
    username: ${SPRING_MAIL_USERNAME}
    password: ${SPRING_MAIL_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
      mail.smtp.from: ${MAIL_FROM:noreply@tanszek.local}

otel:
  exporter:
    otlp:
      endpoint: ${OTLP_ENDPOINT}    # pl. http://jaeger:4317

logging:
  level:
    root: INFO
    com.tanszek: INFO
```

### Email Alert SMTP konfig (mindkét profilban)

```yaml
spring:
  mail:
    properties:
      mail.smtp.connectiontimeout: 5000
      mail.smtp.timeout: 5000
      mail.smtp.writetimeout: 5000
```

### `.env.example` template (a repo gyökerében, `bootstrap.sh` másolja `.env`-re)

```bash
# === Spring Profile ===
SPRING_PROFILES_ACTIVE=dev

# === Database ===
POSTGRES_DB=tanszek_db
POSTGRES_USER=admin
POSTGRES_PASSWORD=secure_pass
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/tanszek_db
SPRING_DATASOURCE_USERNAME=admin
SPRING_DATASOURCE_PASSWORD=secure_pass

# === JWT ===
JWT_KID_ACTIVE=<base64-256-bit-secret>
JWT_KID_PREVIOUS=
JWT_ACCESS_TTL_MIN=15
JWT_REFRESH_TTL_DAYS=30
JWT_KID_GRACE_PERIOD_SEC=3600

# === Crypto ===
CRYPTO_AES_KEY=<base64-256-bit-key>

# === SMTP (production) ===
SPRING_MAIL_HOST=smtp.tanszek.local
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=noreply@tanszek.local
SPRING_MAIL_PASSWORD=<smtp-password>
MAIL_FROM=noreply@tanszek.local

# === Backup / Audit ===
BACKUP_RETENTION_DAYS=30
AUDIT_RETENTION_YEARS=5
REFRESH_CLEANUP_RETENTION_DAYS=7

# === Pagination ===
PAGINATION_DEFAULT_SIZE=20
PAGINATION_MAX_SIZE=50

# === Monitoring ===
OTLP_ENDPOINT=
ALERT_EMAIL_RECIPIENT=admin@tanszek.local
```

---

## 9. Runbook Struktúra (docs/runbook.md, Fázis 5 - Task 5.4)

A runbook 7 szekciót tartalmaz:

1. **Első telepítés** — `scripts/bootstrap.sh` futtatása, .env kitöltés, `docker-compose up`, demo admin user belépés, jelszócsere.
2. **Napi üzemeltetés** — Backup ellenőrzés (`backup_data` volume-ban `/var/backups/YYYY-MM-DD.sql` fájlok `docker volume inspect backup_data` paranccsal), scheduled job státusz (audit retention, refresh token cleanup, backup pg_dump), log-ok ellenőrzése (MailHog, OTel console).
3. **Gyakori hibák + megoldás** — DB connection failed, JWT invalid signature, refresh token expired, Bucket4j 429, attachment upload túl nagy, audit rollback permission denied.
4. **Rollback eljárás** — `POST /api/audit/rollback/{id}` használata (az audit log `changes_json` mezője alapján), rollback futtatás ellenőrzése új audit logban. **Nincs Flyway undo script** — a séma rollback csak teljes DB restore (`scripts/backup-restore.sh`) esetén releváns.
5. **Backup restore** — `scripts/backup-restore.sh YYYY-MM-DD.sql` futtatása, postgres konténer leállítás, dump visszatöltés, konténer indítás, integritás ellenőrzés.
6. **Credential rotation** — `scripts/rotate-jwt-secret.sh` (új secret generálás, .env frissítés, docker restart, grace period lejárata előtt ne rotate-elj újra).
7. **Incident response** — Security breach esetén: azonnali `UserService.deactivate()` az érintett userekre, `refresh_tokens.revoked = true` bulk update, audit log export, értesítési lánc.
