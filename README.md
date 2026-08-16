# Egyetemi Informatikai Tanszéki Nyilvántartó Rendszer

Egyetemi tanszéki eszköz- és szoftver-nyilvántartó rendszer. Spring Boot 3.3 backend + React/Vite frontend, docker-compose alapú on-premise deployment.

## Áttekintés

Ez a rendszer egy egyetemi informatikai tanszék eszközeinek (laptopok, monitorok, stb.), szoftvereinek (license-ek), helyszíneinek (tantermek, irodák, raktárak), és az eszközök user-ekhez való hozzárendelésének nyilvántartására szolgál.

- **3 role** (ADMIN, TEACHER, STUDENT) granularitású hozzáférés-kezeléssel
- **Row-level security** — minden user csak a saját eszközeit látja (STUDENT), vagy a tanszéki környezetében lévőket (TEACHER)
- **Audit log + rollback** — minden módosítás visszavonható a `changes_json` alapján
- **Excel import** — tömeges felhasználó- és eszköz-import
- **JWT + HttpOnly cookie** refresh token — XSS/CSRF ellen védett
- **Bilingual UI** (magyar + angol, `react-i18next`)
- **Dark mode** (shadcn + next-themes)

## Gyors indítás (fejlesztés)

```bash
# 1. Repo skeleton (Task 1.8) — mappák, .env.example, .gitignore, stb.
git clone <repo-url>
cd device-storage

# 2. Bootstrap script (Task 1.8 része) — .env és backup.env generálás
./scripts/bootstrap.sh

# 3. Docker Compose (Task 1.1) — konténerek indítása
docker compose up -d

# 4. Belépés demo admin user-rel
# http://localhost:5173 (Vite dev server) vagy http://localhost:80 (Nginx)
# Email: admin@tanszek.local
# Password: ChangeMe123!  ← first-login kötelező csere
```

## Architektúra

Részletek: [`implementation_plan.md`](./implementation_plan.md)

- **Monorepo:** `backend/`, `frontend/`, `docs/`, `scripts/`
- **Backend:** Spring Boot 3.3 / Java 21 / Maven, feature-based package-ek, 11 DB tábla + BaseEntity
- **Frontend:** Vite + React 18 + TypeScript + Tailwind + shadcn/ui + i18next
- **Docker Compose:** 5 konténer (postgres, backend, frontend, backup, mailhog-dev)
- **Security:** Argon2id jelszó-titkosítás, JWT kid rotáció, refresh token rotation, CSRF token, Rate limiting, Audit log

## Fejlesztési fázisok

A projekt 5 fázisban készül (33 task, ~2-3 hét single-dev scope-pal):

1. **Fázis 1:** Infrastruktúra, DB, JPA entitások
2. **Fázis 2:** Biztonság, titkosítás, auth, audit alapok
3. **Fázis 3:** Üzleti logika, CRUD, Excel import, hibakezelés
4. **Fázis 4:** Frontend integráció és UX
5. **Fázis 5:** Tesztelés és QA

Részletes task lista és haladás: [`agent_progress.md`](./agent_progress.md)

## Dokumentáció

- [`implementation_plan.md`](./implementation_plan.md) — részletes architektúra és döntések
- [`agent_progress.md`](./agent_progress.md) — task tracker és haladás
- [`docs/architecture.md`](./docs/architecture.md) — magas-szintű architektúra diagram
- [`docs/deployment.md`](./docs/deployment.md) — telepítési útmutató
- [`docs/runbook.md`](./docs/runbook.md) — üzemeltetési kézikönyv

## License

MIT — lásd [`LICENSE`](./LICENSE).