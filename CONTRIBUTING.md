# Fejlesztési és Hozzájárulási Útmutató (Contributing)

Köszönjük az érdeklődést a Tanszéki Nyilvántartó Rendszer fejlesztése iránt! Kérjük, tartsd be az alábbi irányelveket a kódminőség és a stabilitás megőrzése érdekében.

## Fejlesztési Folyamat

1. Hozz létre egy új branch-et a feladatodhoz (`feature/<nev>` vagy `fix/<nev>`).
2. Implementáld a szükséges módosításokat a megadott kódolási konvenciók betartásával.
3. Készíts egység- és integrációs teszteket a módosításokhoz.
4. Futtasd le a helyi formázási és teszt ellenőrzéseket a commitolás előtt.
5. Nyiss egy Pull Request-et a `dev` vagy `main` branch felé részletes leírással.

## Kódolási Konvenciók

- **Backend:**
  - Java 21, Spring Boot 3.3+, Maven.
  - Csomagolási elv: feature-based packages (`hu.tanszek.device.*`).
  - Kódformázás: Google Java Format (ellenőrzés: `mvn spotless:check`, formázás: `mvn spotless:apply`).
  - Statikus analízis: Checkstyle szabályok (`mvn checkstyle:check`).
- **Frontend:**
  - React 18, TypeScript, Vite, Tailwind CSS, shadcn/ui.
  - Formázás és linting: ESLint és Prettier (`npm run lint`, `npx prettier --check 'src/**/*.{ts,tsx,css}'`).
- **Általános:**
  - `.editorconfig` beállítások betartása (4 szóköz Java, 2 szóköz TypeScript/HTML/JSON esetén).

## Biztonsági Irányelvek

- **Soha ne commitolj `.env`, `backup.env` vagy egyéb titkos adatot tartalmazó fájlt!** Csak a `.env.example` és `backup.env.example` fájlok verziókövetettek.
- Minden érzékeny adatot környezeti változókon keresztül juttassunk el az alkalmazáshoz.

## Tesztelés

- **Backend:**
  - Egységtesztek: `mvn test`
  - Integrációs tesztek (Testcontainers PostgreSQL): `mvn verify`
- **Frontend:**
  - Vitest tesztek: `npm run test`
- **Rendszerteszt:**
  - Smoke teszt: `./scripts/smoke-test.sh` (elindított Docker konténerek mellett).

## Kérdések és Hibajelzés

Kérjük, nyiss egy új GitHub Issue-t, vagy olvasd el a rendszer üzemeltetési kézikönyvét a [`docs/runbook.md`](./docs/runbook.md) fájlban.