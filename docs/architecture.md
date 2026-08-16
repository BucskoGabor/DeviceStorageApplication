# Architecture

Magas szintű architektúra diagram és komponens-leírások.

## Komponensek

- **Frontend (Vite + React 18 + TypeScript + shadcn/ui):** Single-page alkalmazás moduláris felépítéssel (auth, user, device, location, software, assignment, attachment, audit). Axios HTTP kliens automatikus tokenfrissítéssel, TanStack Query cache, i18next kétnyelvű támogatás, dark mode.
- **Backend (Spring Boot 3.3 + Java 21 + Maven):** REST API, funkció alapú csomagstruktúra (feature-based packages), rétegzett architektúra és SOLID elvek. Argon2id jelszókezelés, JWT kid rotáció, refresh token rotáció, Bucket4j rate limiting, AOP audit naplózás, Flyway adatbázis-migráció, JPA Auditing.
- **Database (PostgreSQL 16):** 11 tábla (configs, permissions, roles, locations, app_users, softwares, devices, device_attachments, device_assignments, audit_logs, refresh_tokens) + BaseEntity (`@MappedSuperclass`).
- **Docker Compose:** 5 konténer — postgres, backend (Java 21), frontend (Nginx + Vite build), backup (pg_dump cron), mailhog (dev profil).

## Deployment

- **On-premise** egyetemi szerver, nincs felhő függőség.
- **Named volume-ok** — postgres_data, uploads_data, backup_data, audit_archive_data, plusz tmpfs átmeneti könyvtárak.
- **Production hardening:** non-root felhasználók a konténerekben, read-only fájlrendszerek, erőforráskorlátok, healthcheck végpontok, strukturált JSON naplózás OpenTelemetry tracing támogatással.

## Security

- Argon2id jelszó-hashelés
- JWT access token (15 perc) + refresh token rotáció (30 nap)
- HttpOnly + Secure + SameSite=Strict cookie a refresh tokenhez
- CSRF token védelem az állapotmódosító kérésekhez
- Rate limiting (Bucket4j): IP és email alapú korlátozás
- Row-level szűrés minden adathozzáférési műveletre
- Audit log és automatikus rollback támogatás
- 14 jogosultság és 3 szerepkör granuláris kezelése