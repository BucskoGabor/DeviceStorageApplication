# Egyetemi Informatikai Tanszéki Nyilvántartó Rendszer

Egyetemi tanszéki eszköz- és szoftver-nyilvántartó rendszer. Spring Boot 3.3 backend + React / Vite frontend, Docker Compose alapú on-premise környezettel.

## Áttekintés

A rendszer az egyetemi tanszék fizikai eszközeinek (laptopok, monitorok, hálózati eszközök), szoftverlicenceinek, helyszíneinek (tantermek, irodák, raktárak), valamint az eszköz-kiosztások és felelősök nyilvántartását és kezelését biztosítja.

### Főbb funkciók

- **Szerepkör- és hozzáférés-kezelés:** 3 szerepkör (ADMIN, TEACHER, STUDENT) és granuláris jogosultsági rendszer.
- **Row-level security:** Felhasználói szintű adatszűrés (a hallgatók a saját eszközeiket, az oktatók a hozzájuk tartozó tanszéki környezetet kezelik).
- **Audit naplózás és visszavonás (rollback):** Minden módosítás naplózott JSON diff alapján, adminisztrátori visszavonási lehetőséggel.
- **Excel import / export:** Tömeges felhasználó- és eszközimport előnézettel és validációval.
- **Biztonságos autentikáció:** Argon2id jelszóhash, JWT hozzáférési tokenek és HttpOnly cookie refresh token rotáció CSRF védelemmel.
- **Kétnyelvű kezelőfelület:** Magyar és angol nyelv támogatása (`react-i18next`).
- **Sötét / világos téma:** Téma testreszabás (shadcn/ui + Tailwind CSS).

## Gyors indítás (fejlesztői környezet)

```bash
# 1. Repository klónozása
git clone <repo-url>
cd DeviceStorageApplication

# 2. Környezeti változók inicializálása (első indítás előtt)
./scripts/bootstrap.sh

# 3. Konténerek indítása
docker compose up -d

# 4. Alkalmazás megnyitása
# Frontend: http://localhost:5173 (vagy production Nginx esetén http://localhost:80)
# Backend API / Swagger UI: http://localhost:8080/swagger-ui.html
# Alapértelmezett admin belépés: admin@tanszek.local / ChangeMe123! (első belépéskor jelszócsere kötelező)
```

## Architektúra

- **Monorepo struktúra:**
  - `backend/` — Spring Boot 3.3, Java 21, Maven, PostgreSQL, Flyway, Hibernate / JPA, Spring Security 6
  - `frontend/` — React 18, Vite, TypeScript, Tailwind CSS, shadcn/ui, TanStack Table & Query
  - `scripts/` — Karbantartó, bootstrap és tesztelő szkriptek
  - `docs/` — Részletes rendszerdokumentációk
- **Docker infrastruktúra:** 5 konténer (PostgreSQL 16, Spring Boot API, Nginx frontend, pg_dump backup runner, Mailhog teszt SMTP).

## Dokumentáció

- [Architektúra leírás](file:///home/aglathyne/Documents/GitHub/DeviceStorageApplication/docs/architecture.md) — Rendszerfelépítés és komponensek
- [Telepítési útmutató](file:///home/aglathyne/Documents/GitHub/DeviceStorageApplication/docs/deployment.md) — Production környezet beállítása
- [Üzemeltetési kézikönyv](file:///home/aglathyne/Documents/GitHub/DeviceStorageApplication/docs/runbook.md) — Karbantartás, mentés és hibaelhárítás

## Licenc

MIT — lásd a [LICENSE](file:///home/aglathyne/Documents/GitHub/DeviceStorageApplication/LICENSE) fájlt.