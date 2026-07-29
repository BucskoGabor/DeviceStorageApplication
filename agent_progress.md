# Agent Haladási Napló (Progress Tracker)
## Projekt: Egyetemi Informatikai Tanszéki Nyilvántartó Rendszer

> **Utolsó frissítés:** 2026-07-29
>
> **Státusz jelölések:** `[ ]` backlog · `[~]` in progress · `[x]` done · `[!]` blocked · `Started: YYYY-MM-DD` az in-progress taskok mellett
>
> **Definition of Done (single-dev, self-reviewed):**
> 1. A code meg van írva és commitolva.
> 2. Unit és/vagy integration tesztek megírva és zölden futnak (ahol releváns).
> 3. Self-review: saját PR, checklist végignézve (naming, edge cases, biztonság, i18n kulcsok szinkronban, row-level filter minden műveletre).
> 4. **SOLID elvek betartása (a §3.0 Tervezési Elvek alapján):** SRP (minden service egy felelősség), OCP (AuthProvider interface, @RequirePermission aspektus), DIP (repository-k interface-en át injektálva, CryptoService mock-olható). Code review checklist a Task 5.5 CI workflow-ban.
> 5. Manuális smoke teszt a lokális docker-compose környezetben.
> 6. Dokumentáció: Javadoc a public service metódusokon, Springdoc OpenAPI annotációk a REST endpointokon, README frissítés ha az architektúra változik.

---

## Összesítő

| Metrika | Érték |
|---|---|
| Összes task | 33 |
| Kész | 7 |
| In progress | 0 |
| Backlog | 26 |
| Blocked | 0 |
| Haladás | 21% |

---

## Next 3 Task (Indítási Sorrend)

1. **Task 1.8:** Repo skeleton (`backend/`, `frontend/`, `docs/`, `scripts/` mappák, `.env.example`, `.gitignore`, `.editorconfig`, `LICENSE`, `CONTRIBUTING.md`, `README.md`, `bootstrap.sh`). Először ez kell, mert a bootstrap.sh a Task 1.8-ból generálja a `.env` fájlt a `.env.example` alapján, és a docker-compose `env_file: .env` direktívája erre a fájlra hivatkozik — nélküle a konténerek nem indulnak el megfelelő konfigurációval.
2. **Task 1.1:** `docker-compose.yml` elkészítése (5 konténer: postgres, backend, frontend, backup [backup_data volume], mailhog-dev). Ez a kiindulópont, minden más erre épül.
3. **Task 1.2:** Spring Boot projekt init (párhuzamosan az 1.7 frontend init-tel).

_Ezek egymás után: 1.8 (alapok) → 1.1 (compose) → 1.2/1.7 párhuzamosan, mert 1.2 szükséges az 1.3-hoz (Flyway dependency), és 1.1 szükséges a későbbi konténer-build tesztekhez._

---

### Fázis 1: Infrastruktúra, Adatbázis & JPA Entitások

- [x] **Task 1.1:** `docker-compose.yml` elkészítése (5 konténer [postgres, backend, frontend, backup, mailhog-dev], **explicit volumes szekció**: postgres_data, uploads_data, backup_data, audit_archive_data, healthcheckek, non-root userek [security_opt: no-new-privileges], resource limits, log rotation [json-file max 10m/3 files], mailhog **profiles: [dev]** host port mapping `1025:1025` + `8025:8025`, backend **read_only: true + tmpfs mount-ok /tmp és /var/run** + `env_file: .env` direktívával + uploads_data:/var/uploads + audit_archive_data:/var/backups/archive/audit, backup konténer **env_file: backup.env** + backup_data:/var/backups + saját `backup.sh`/`cleanup.sh`/`crontab.txt` Alpine Dockerfile-ban, **explicit 'app-network' bridge network**, restart policy `unless-stopped`). **Depends on:** 1.8
- [x] **Task 1.2:** Spring Boot 3.3+ projekt inicializálása Java 21 LTS verzióval (**Maven** build tool, **Spring Boot 3.3.5**, groupId `hu.tanszek.device`, artifactId `device-storage-backend`, version `0.1.0`, multi-stage Dockerfile [maven:3.9-eclipse-temurin-21 → eclipse-temurin:21-jre-alpine, non-root user, healthcheck]). **pom.xml** 24 dependency-vel: core Spring Boot starters (Web, Data JPA, Security, Validation, Actuator), PostgreSQL, Flyway (core + postgresql), Springdoc OpenAPI 2.6.0, Bucket4j 8.10.1, spring-retry, Spring Mail, **spring-boot-starter-actuator** (health/info/refresh), OpenTelemetry Spring Boot starter + Micrometer Tracing bridge, Spring Cloud Context (RefreshScope), Lombok, Apache POI 5.3.0, logstash-logback-encoder 7.4, Spring Boot Test, spring-security-test, Testcontainers (postgresql, junit-jupiter), H2 (test scope). **Spotless + Checkstyle + Jacoco (70% coverage gate)** konfigurálva. **Application.yml + application-dev.yml + application-prod.yml** + logback-spring.xml (structured JSON prod-ban) + messages_hu.properties + messages_en.properties placeholder-ek. **Depends on:** —
- [x] **Task 1.3:** Flyway `V1__init_schema.sql` megírása (11 tábla + 3 join table: configs, permissions, roles, locations [version optimistic lock + parent_id self-reference + type ENUM check constraint], app_users [must_change_password, password_changed_at, failed_login_count, locked_until + email_hash unique index], softwares [license_key_encrypted], devices [status ENUM check + inventory_number unique], device_softwares [join table], device_attachments [mime_type max 100, size_bytes, storage_path, cascade delete on device], device_assignments [egyetlen tábla + active flag + status ENUM + 8 FK-k + partial indexes on active=true], audit_logs [entity_type, entity_id, http_status, changes_json TEXT + descending timestamp index], refresh_tokens [token_hash unique + replaced_by_id ON DELETE SET NULL + partial index on revoked=true + 14 explicit indexes total]). **Classpath:** `backend/src/main/resources/db/migration/`. **`flyway.baseline-on-migrate: false`** + **`spring.flyway.repair-on-migrate: false`** + **Nincs Flyway undo**. **14 CREATE TABLE, 27 CREATE INDEX, 22 FK constraint, 304 sor.** **Depends on:** 1.2
- [x] **Task 1.4:** Flyway `V2__seed.sql` (**14 permission**, **3 role** + role_permissions mapping [ADMIN: minden, TEACHER: 6 permission, STUDENT: 3 permission], **hierarchikus location seed** [1 root OFFICE "Tanszéki Iroda" + 3 child: CLASSROOM "Tanterem 101", STORAGE "Eszköz Raktár", GROUP "Hallgatói Csoport" — DO $$ blokk + RETURNING id], **demo admin user** `admin@tanszek.local` / `ChangeMe123!` Argon2id placeholder hash-sel [implementációkor Argon2PasswordEncoder.encode() hívással generálandó] + `must_change_password=true`, **12 configs** [AUTH_PROVIDER, BACKUP_RETENTION_DAYS=30, AUDIT_RETENTION_YEARS=5, AUDIT_ARCHIVE_PATH, MAX_LOGIN_ATTEMPTS=5, LOCKOUT_DURATION_MIN=15, JWT_ACCESS_TTL_MIN=15, JWT_REFRESH_TTL_DAYS=30, JWT_KID_GRACE_PERIOD_SEC=3600, PAGINATION_DEFAULT_SIZE=20, PAGINATION_MAX_SIZE=50, ALERT_EMAIL_RECIPIENT]). **Depends on:** 1.3
- [x] **Task 1.5:** JPA Entitások + `BaseEntity` abstract class (`@MappedSuperclass`, `id`, `created_at`, `updated_at` JPA Auditing által töltve, `@EnableJpaAuditing` a backend-en, lombok @SuperBuilder) + 14 entitás (Config, Permission, Role, Location [@Version optimistic lock + parent self-reference + LocationType enum], AppUser [must_change_password + email_encrypted + email_hash + @ManyToMany permissions + @ManyToOne role + @ManyToOne officeLocation], Software [license_key_encrypted], Device [type String + status DeviceStatus enum + @ManyToMany softwares], DeviceAttachment [file_name + mime_type + size_bytes + storage_path + cascade delete device-re], DeviceAssignment [active flag + 8 FK + AssignmentStatus enum + state machine mezők], AuditLog [timestamp + user_email + endpoint + method + request_payload + changes_json + http_status + entity_type + entity_id], RefreshToken [token_hash SHA-256 + expires_at + revoked + replaced_by rotation chain + isExpired()/isActive() helper]) + 14 JpaRepository interface (Config/Permission/Role/Location [JpaSpecificationExecutor] /AppUser [JpaSpecificationExecutor + revokeAllRefreshTokensByUserId + findExpiredLocks] /Software/Device [JpaSpecificationExecutor] /DeviceAttachment/DeviceAssignment [JpaSpecificationExecutor] /AuditLog [deleteByTimestampBefore + findByTimestampBefore] /RefreshToken [findByTokenHash + findChainRoot + deleteOldTokens]). **3 enum osztály**: LocationType, DeviceStatus, AssignmentStatus. **Depends on:** 1.3
- [ ] **Task 1.6:** `backup` konténer implementálása (Alpine + bash + postgresql-client + dcron, non-root user, napi 02:00 pg_dump, **30 nap retention a `BACKUP_RETENTION_DAYS` env var-ból** [a backup konténer **saját `env_file: ./backup.env`** direktívával tölti be a `backup.env.example`-ből, default 30 nap — NEM a backend .env-jéből, hogy ne lássa a backend titkait], healthcheck, depends_on postgres-re). **Depends on:** 1.1
- [x] **Task 1.7:** Vite + React + TypeScript + shadcn/ui + Tailwind + next-themes projekt inicializálás (**Vite 6.x, React 18.3.1, TypeScript 5.6, TanStack Query v5, shadcn/ui CLI ready**). **package.json** 30+ dependency-vel: Radix UI primitívek (avatar, dialog, dropdown-menu, label, popover, select, separator, slot, toast, tooltip), axios, i18next + react-i18next, lucide-react, next-themes, react-error-boundary, react-router-dom 6, sonner, tailwind-merge, tailwindcss-animate, zod, zustand. **DevDeps:** TypeScript, ESLint, Prettier, Tailwind, PostCSS, Autoprefixer, vite plugin react. **vite.config.ts** Vite proxy `/api → localhost:8080`, path alias `@/`, manual chunks. **tailwind.config.js** shadcn/ui theme rendszer, CSS variables (light + dark mode). **postcss.config.js** Tailwind + autoprefixer. **index.html** + **.env.example** `VITE_*` prefix-szel. **Multi-stage Dockerfile** (node:20-alpine → nginx:1.27-alpine, healthcheck). **Részletes nginx.conf** (security headers [X-Frame-Options, CSP, X-Content-Type-Options, Referrer-Policy], gzip, /api/ reverse proxy, static asset caching 1y, SPA fallback `try_files`). **src/ struktúra:** App.tsx (provider-ek: ErrorBoundary, I18nextProvider, ThemeProvider, QueryClientProvider, BrowserRouter), main.tsx, index.css (Tailwind + CSS vars), **lib/api/axios.ts** (Bearer + CSRF interceptor, refresh-in-progress lock + queue), **lib/i18n/i18n.ts** (hu + en, navigator locale detection), **lib/theme/theme-provider.tsx** (next-themes wrapper), **components/ui/button.tsx** (shadcn button cva variants), **components/SonnerWrapper/Toaster.tsx**, **components/ErrorBoundary.tsx**, **routes/index.tsx** (központi routes placeholder), **features/auth/pages/LoginPage.tsx** (placeholder i18n használattal), **lib/i18n/{hu,en}.json** (teljes i18n resource fájlok), **lib/i18n/i18n-keys.ts** (MessageKey union type placeholder, Task 2.8-ban code generator). **.eslintrc.json + .prettierrc**. **Depends on:** —
- [x] **Task 1.8:** Repo skeleton: `backend/`, `frontend/`, `docs/`, `scripts/` mappák, `.env.example`, `backup.env.example`, `.gitignore` (multi-stack Java/Node/IDE/Docker), `.editorconfig`, `LICENSE` (MIT), `CONTRIBUTING.md`, monorepo `README.md`, `bootstrap.sh` script (env másolás + JWT/Crypto secret generálás + Docker check, idempotens, tesztelve). **Depends on:** —
- [ ] **Task 1.9:** AuthProvider interface + LocalAuthProvider (Argon2 + DB) + StubAdAuthProvider placeholder (AD integráció hook), AuthProviderFactory a configs.AUTH_PROVIDER alapján. **Depends on:** 1.5

### Fázis 2: Biztonság, Titkosítás, Auth & Audit Alapok

- [ ] **Task 2.1:** `CryptoService` (AES-GCM 256 + SHA-256, license_key_encrypted visszafejtés SOFTWARE_LICENSE_VIEW permission check-kel, maszkolás: full decrypt ha van permission, különben `****-****-****-<utolsó 4 char>`). **Depends on:** 1.5
- [ ] **Task 2.2:** Spring Security 6 + Argon2id + rehash logika (login service `Argon2PasswordEncoder.upgradeEncoding()` metódussal ellenőrzi a hash paramétereit, ha elavultak `encode(rawPassword)` hívással újrahasheli és DB-be írja, egy tranzakcióban). **Depends on:** 1.5
- [ ] **Task 2.3:** JWT auth (access 15min + refresh 30day), kid alapú rotáció (**1 óra grace period** a JWT_KID_GRACE_PERIOD_SEC alapján), refresh token rotation reuse detection-nel, UserService.deactivate() auto-revoke minden aktív session-re, **Spring Security CSRF token** state-changing műveletekre (POST/PUT/DELETE, **CookieCsrfTokenRepository.withHttpOnlyFalse()**, X-XSRF-TOKEN header ellenőrzés, /api/auth/* endpointokon kikapcsolva), **JwtAuthenticationFilter extends OncePerRequestFilter** (Bearer token → SecurityContext), **Security Filter Chain rendje** (1. RequestIdFilter → 2. RateLimitFilter → 3. CsrfFilter → 4. JwtAuthenticationFilter → 5. UsernamePasswordAuthenticationFilter → 6. ExceptionTranslationFilter → 7. AuthorizationFilter). **Depends on:** 1.5
- [ ] **Task 2.4:** Refresh token cleanup `@Scheduled` napi 04:00, törli a **7+ napos** lejárt/revoked tokeneket (grace period debug-hoz, config: `refresh.cleanup.retention-days: 7`) + email alert on failure. **Depends on:** 2.3
- [ ] **Task 2.5:** Bucket4j rate limiting (login per-IP 5/perc, per-email 10/óra) + account lockout (5 próba → 15min). **RateLimitFilter extends OncePerRequestFilter** a Security Filter Chain legelején (CsrfFilter előtt), in-memory `ConcurrentHashMap<String, Bucket>` (kulcs: IP vagy email SHA-256 hash), 429-es válasz `BusinessValidationException("rateLimitExceeded")` dobásával (GlobalExceptionHandler kezeli). **Depends on:** 2.2 (SecurityConfig-ba kell regisztrálni a Filter láncban).
- [ ] **Task 2.6:** `@RequirePermission` custom annotáció + aspektus, RoleHierarchyConverter a ROLE_ prefix-hez. A `@RequirePermission("DEVICE_READ")` aspektus a `SecurityContext`-ből olvassa a user permission-jeit, **nincs natív `@PreAuthorize`** — minden permission check a custom aspektuson megy, mert a row-level filtert is itt kell ellenőrizni. A role-ok `ROLE_` prefix-szel mennek a Spring Security-nek a `RoleHierarchyConverter`-en keresztül. **Depends on:** 1.2
- [ ] **Task 2.7:** AOP Audit Interceptor (entity_type, entity_id capture, maszkolás, **changes_json formátum: `{"before": {...}, "after": {...}}`** a rollback támogatáshoz). **Depends on:** 1.2
- [ ] **Task 2.8:** Backend i18n MessageSource ResourceBundle hu+en, camelCase kulcsok, **saját Maven code generator plugin** (messages_hu.properties + messages_en.properties olvasás → `frontend/src/lib/i18n/i18n-keys.ts` TypeScript union type + `i18n-defaults.json` dict, build-time generálás a `mvn package` során, frontend `npm run build` utána felhasználja, CI type-check drift ellen). **Depends on:** 1.2
- [ ] **Task 2.9:** Structured JSON logging (Logback + logstash-logback-encoder), MDC: request_id, user_id, user_email, endpoint, method, trace_id, span_id (OpenTelemetry + Micrometer Tracing), OTLP exporter dev → console, prod → env. **RequestIdFilter extends OncePerRequestFilter** a Security Filter Chain legelején (RateLimitFilter előtt): UUID `request_id` generálás vagy kliens `X-Request-ID` header átvétel, MDC-be rakás, response `X-Request-ID` headerben visszaadása. **Depends on:** 1.2
- [ ] **Task 2.10:** Mail konfig (Spring Mail + Spring Retry, dev → MailHog :1025, prod → env SMTP, 3 attempt exponential backoff). **Depends on:** 1.1

### Fázis 3: Üzleti Logika, CRUD, Excel Import & Hibakezelés

- [ ] **Task 3.1:** GlobalExceptionHandler (`@RestControllerAdvice` + `@ExceptionHandler` minden exception típushoz: BusinessValidationException, ResourceNotFoundException, UnauthorizedActionException, OptimisticLockException, MethodArgumentNotValidException, Exception fallback) + egységes JSON body (timestamp/status/error/message/messageKey/path/details) + **FieldError lista a details-ben** validation hibáknál (`{field, message, rejectedValue}`) + **Admin unlock endpoint `POST /api/admin/users/{id}/unlock`** (USER_MANAGE permission, `failed_login_count = 0` + `locked_until = NULL`, audit log). **Depends on:** 1.5
- [ ] **Task 3.2:** Repositories JpaSpecificationExecutor-ral + Pageable, **row-level szűrés MINDEN műveletre** (DEVICE_READ/UPDATE/DELETE/ASSIGN/UNASSIGN), RowLevelSecurityPredicate helper, STUDENT: csak to_user_id saját, TEACHER: saját + irodai, ADMIN: minden. **Depends on:** 2.6
- [ ] **Task 3.3:** Assign/unassign service (GROUP location tiltás **forrás ÉS cél** is, MAINTENANCE/DISPOSED tiltás, state machine PENDING_ASSIGNMENT → ASSIGNED → PENDING_UNASSIGNMENT). **Depends on:** 2.6
- [ ] **Task 3.4:** Excel import service (Apache POI, users + devices, idempotens UPDATE-OR-SKIP, AUTH_PROVIDER figyelembevétele — LOCAL/AD eltérő validáció). **Külön `com.tanszek.device.import` feature package** (ImportController, ImportService, DTO-k). **Biztonság:** USER_MANAGE permission + mime check (xlsx) + max 10MB. **Concurrency:** `synchronized` metódus — egyszerre csak egy import, 409 Conflict a többire. **Depends on:** 1.5
- [ ] **Task 3.5:** Device attachments service (upload/download, max 5MB/fájl, max 5/device, mime validation, lokális volume storage `./uploads/devices/{device_id}/{uuid}.{ext}`, **cascade delete** device törléskor + audit log). **Depends on:** 1.5
- [ ] **Task 3.6:** Audit retention `@Scheduled` vasárnap 03:00, 1 év archív, 5 év törlés, `/var/backups/archive/audit/YYYY/` (configs AUDIT_ARCHIVE_PATH + audit_archive_data volume mount), **email alert on failure**. **Depends on:** 1.6, 2.7
- [ ] **Task 3.7:** Audit rollback endpoint `/api/audit/rollback/{id}` (AUDIT_ROLLBACK permission, supported entity types: **Device, User, Location, Assignment, Software, Attachment**, új audit log generálás, changes_json diff inverz alkalmazása **@Transactional REQUIRED, all-or-nothing**). **Depends on:** 2.7
- [ ] **Task 3.8:** Request méretkorlát háromszintű (Nginx `client_max_body_size 10M` a /api/ location block-ban + Spring multipart 10M + Bean Validation @Size). **Depends on:** 1.1, 1.5
- [ ] **Task 3.9:** Springdoc OpenAPI integráció (JWT bearer security scheme, /v3/api-docs, /swagger-ui.html dev profile-ban). **Depends on:** 2.3
- [ ] **Task 3.10:** **Application.yml konfiguráció (§8 a tervben):** `application.yml` (közös), `application-dev.yml` (mailhog, debug log), `application-prod.yml` (env SMTP, OTLP endpoint, info log), profile szétválasztás `SPRING_PROFILES_ACTIVE` env var-ból. **Depends on:** 2.10

### Fázis 4: Frontend Integráció & UX

- [ ] **Task 4.1:** Axios HTTP kliens (**withCredentials: true** minden kéréshez, JWT access token memory-ban, **refresh token HttpOnly Secure SameSite=Strict cookie-ból automatikusan** [Axios interceptor 401 → POST /api/auth/refresh, cookie automatikusan csatolódik, retry az eredeti kéréssel, nincs user-facing warning modal, csak refresh failure esetén redirect /login, **refresh-in-progress lock + queue** hogy párhuzamos 401-ek ne indítsanak több refresh-t], **X-XSRF-TOKEN header automatikus küldése** state-changing kéréseknél [axios interceptor a cookie-ból olvassa a XSRF-TOKEN-t és headerbe rakja], globális Sonner toast hibakezelő [messageKey → i18next fordítás wrapper komponensben, ha nincs kulcs → backend message fallback], request méretkorlát), **React Error Boundary** a route-ok köré wrap-elve [runtime render hibák elkapása, fallback UI, error log]. **Depends on:** 2.3
- [ ] **Task 4.2:** i18next bekötés (hu + en, nyelv választó a header-ben, user choice > localStorage > Accept-Language header > hu fallback, code generator output integráció). **Depends on:** 2.8
- [ ] **Task 4.3:** Login oldal + first-login jelszócsere kényszerítés (`must_change_password = true` esetén a route loader **dinamikus redirect** a `/password-change` page-re — a routes/index.ts-ben NINCS explicit `/password-change` route, a route loader guard-ja kezeli a SecurityContext `mustChangePassword` flag alapján). A jelszócsere törli a flaget és frissíti a `password_changed_at` timestampet. **Depends on:** 4.1, 4.2
- [ ] **Task 4.4:** Szerepkörfüggő dashboardok (`/my-dashboard` user saját eszközei + hozzárendelések, `/admin` teljes CRUD). **Route védelem a `routes/index.ts`-ben:** minden route `protected: true` flag-gel + opcionális `roles: ['ROLE_ADMIN']` és `permissions: ['USER_MANAGE']` tömbbel. A route loader ellenőrzi a SecurityContext-ből, és ha nincs megfelelő role/permission, redirect `/login`-re vagy `/403`-ra. **Depends on:** 4.3
- [ ] **Task 4.5:** Lapozható, szűrhető shadcn DataTable komponensek (users, devices, locations, softwares) TanStack Query-vel. **Depends on:** 3.2, 4.1
- [ ] **Task 4.6:** Excel import UI (Upload → Preview → Confirm flow, drag-drop, hibás sorok CSV export). **Depends on:** 3.4, 4.1
- [ ] **Task 4.7:** Device attachments UI (drag-drop upload, preview grid, delete, kliens-oldali mime/size validation [5MB, elfogadott mime típusok]). **Depends on:** 3.5, 4.5
- [ ] **Task 4.8:** Audit log viewer (szűrés, lapozás, diff side panel pretty-print, rollback gomb jogosult entitásoknál). **Depends on:** 3.7, 4.5
- [ ] **Task 4.9:** Végleges Dockerfile-ok + `scripts/smoke-test.sh` + monorepo `docker-compose up` smoke teszt. **Depends on:** 1.1, 4.4

### Fázis 5: Tesztelés & QA

- [ ] **Task 5.1:** Unit tesztek (service réteg, min. 70% lefedettség Jacoco, row-level filter minden műveletre kitesztelve, license key maszkolás mindkét ág). **Depends on:** 1.5
- [ ] **Task 5.2:** Testcontainers setup (pom.xml dependency, PostgresContainer shared instance, base teszt osztály). **Depends on:** 1.2
- [ ] **Task 5.3:** Integration tesztek (@SpringBootTest + MockMvc: auth flow, CRUD, jogosultság, rate limit, rollback, refresh rotation reuse detection, row-level filter írásra is, attachment cascade delete, kid grace period). **Depends on:** 5.2
- [ ] **Task 5.4:** Standard runbook (`docs/runbook.md`, **7 szekció**): (1) Első telepítés (bootstrap.sh, demo admin, jelszócsere), (2) Napi üzemeltetés (backup ellenőrzés, scheduled job státusz, log-ok), (3) Gyakori hibák + megoldás (DB connection, JWT invalid, 429, upload túl nagy, rollback permission), (4) Rollback eljárás (POST /api/audit/rollback — **nincs Flyway undo**, rollback az audit log changes_json-ból), (5) Backup restore (scripts/backup-restore.sh), (6) Credential rotation (scripts/rotate-jwt-secret.sh), (7) Incident response (UserService.deactivate, refresh token bulk revoke, audit log export). **Depends on:** 4.9
- [ ] **Task 5.5:** GitHub Actions workflow (`.github/workflows/ci.yml`: Spotless + Checkstyle + OWASP párhuzamosan → Unit (Jacoco ≥70%) → Integration → Docker build → Smoke test, status check). **SOLID code review checklist a workflow-ban:** SRP (minden service egy felelősség, nincs "god service"), DIP (repository-k interface-en át injektálva, mock-olhatók), OCP (új feature-ök extension pointokon mennek, nem módosítják a meglévő kódot). **Depends on:** 5.3

---

## Future Considerations (Out of Scope, későbbre)

*   **Argon2id → modernebb paraméterek** (memory/iterations növelés) - rehash logika már támogatja
*   **Jaeger/Tempo OTLP endpoint** production-ben (most csak console, dev)
*   **OWASP Dependency-Check report publikálás** (GitHub Security tab)
*   **Mobile app** (React Native / Flutter) - eszköz QR-kód olvasással
*   **2FA / MFA** (TOTP, SMS) - második faktor a login-hoz
*   **OIDC / SSO integráció** (Keycloak, university SAML)
*   **AD/LDAP integráció** (StubAdAuthProvider → tényleges LDAP connector)
*   **Email értesítések user-eknek** (assign/unassign confirmation, lockout notification, password expiry warning)
*   **Push notification** (böngésző push API)
*   **Dashboard analitika** (Chart.js / Recharts, riportok, statisztikák)
*   **Asset QR-kód generálás és nyomtatás** (eszközök fizikai címkézése)
*   **Bulk Excel export** (jelenlegi állapot exportálása, pl. éves leltár)
*   **Maintenance ticket rendszer** (MAINTENANCE státuszú eszközökhöz kapcsolódó javítási workflow)
*   **HA deployment** (Redis-backed Bucket4j, multi-instance backend, read replica DB)
*   **PWA / offline mód** (service worker, install-to-home-screen)
*   **Soft delete** entitásokon (most hard delete van)
*   **WebSocket real-time notifications** (assign/unassign azonnali push a dashboard-ra)
*   **Audit log viewer dashboard analitika** (trend riportok, gyanús activity detekció)
*   **Bulk operations** (tömeges device assign/unassign Excel-ből, nem csak import)

---

## Megjegyzések

*   A projekt jelenleg **tervezési fázisban** van, 0% haladás.
*   Single-developer scope (agent), reálisan 2-3 hét a teljes scope-hoz (nem "pár nap").
*   A taskok a `Depends on:` mezők alapján párhuzamosíthatók ahol a dependency megengedi.
*   **Fázis párhuzamossági mapping:**
    *   Fázis 1 → Fázis 2 (szekvenciális, Fázis 2 függ Fázis 1-től)
    *   Fázis 2 → Fázis 3 (szekvenciális)
    *   Fázis 3 ↔ Fázis 4 (párhuzamosan indítható, ha a task dependency megengedi: pl. Task 4.5 [DataTable] futhat a 3.4 [Excel import service] mellett, mert 3.2 már kész)
    *   Fázis 4 → Fázis 5 (szekvenciális, Fázis 5 a teljes scope-ra épít)
*   **Indítás:** Task 1.8 (repo skeleton, .env.example) → 1.1 (docker-compose) → 1.2/1.7 (backend + frontend init párhuzamosan) → 1.3 (Flyway V1) → 1.4 (Flyway V2 seed) → 1.5 (JPA entitások) → ...
*   **Kritikus útvonal:** 1.1 → 1.2 → 1.3 → 1.5 → 2.6 → 3.2 → 4.5 → 5.1 → 5.3 → 5.5 (a deployment és CI-hez elengedhetetlen lánc).
