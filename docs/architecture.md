# Architecture

Magas-szintű architektúra diagram és komponens-leírások.

> Részletes terv: [`implementation_plan.md`](../implementation_plan.md)
> Task tracker: [`agent_progress.md`](../agent_progress.md)

## Komponensek

- **Frontend (Vite + React 18 + TypeScript + shadcn/ui):** Single-page alkalmazás, 8 feature package (auth, user, device, location, software, assignment, attachment, audit). Axios HTTP kliens silent refresh-sel, TanStack Query cache, i18next bilingual, dark mode.
- **Backend (Spring Boot 3.3 + Java 21 + Maven):** REST API 14 endpoint-tal, feature-based packages, SOLID elvek szerinti service-réteg. Argon2id jelszó, JWT kid rotáció, refresh token rotation, Bucket4j rate limiting, AOP audit, Flyway migráció, JPA Auditing.
- **Database (PostgreSQL 16):** 11 tábla (configs, permissions, roles, locations, app_users, softwares, devices, device_attachments, device_assignments, audit_logs, refresh_tokens) + BaseEntity (`@MappedSuperclass`).
- **Docker Compose:** 5 konténer — postgres, backend (Java 21), frontend (Nginx + Vite build), backup (pg_dump cron), mailhog (dev only).

## Deployment

- **On-premise** egyetemi szerver, nincs cloud függőség.
- **5 named volume** — postgres_data, uploads_data, backup_data, audit_archive_data, plusz tmpfs a /tmp és /var/run mountok.
- **Production hardening:** non-root userek, read-only FS ahol lehet, resource limits, healthcheckek, structured JSON logging OpenTelemetry tracing-gel.

## Security

- Argon2id (memory-hard, OWASP 2024+ ajánlás) jelszó-titkosítás
- JWT access (15min) + refresh token rotation (30day)
- HttpOnly + Secure + SameSite=Strict cookie a refresh tokennek
- CSRF token state-changing műveletekre (CookieCsrfTokenRepository)
- Rate limiting (Bucket4j): 5/perc per IP, 10/óra per email
- Row-level filter MINDEN műveletre (defense in depth)
- Audit log + rollback (changes_json diff alapján)
- 14 permission, 3 role granularitás

Részletek: [`implementation_plan.md` §3](../implementation_plan.md).