# Runbook

> Ez a dokumentum a Task 5.4-ben készült, 7 szekcióval az üzemeltetési kézikönyvhöz.

## 1. Első telepítés (Production)

### Előfeltételek
- Docker Engine 24+ és Docker Compose v2
- 4 GB RAM minimum, 10 GB szabad lemezterület
- 80-as port (frontend), 8080-as port backend (belső, nem exposed)
- SSL reverse proxy (nginx, traefik, Caddy) a 443-as porton

### Lépések

```bash
# 1. Repo klónozás
git clone <repo-url> /opt/device-storage
cd /opt/device-storage

# 2. Bootstrap script – .env és backup.env generálás
chmod +x scripts/*.sh
./scripts/bootstrap.sh

# 3. .env testreszabása (SMTP jelszó, OPenTelemetry endpoint, stb.)
nano .env

# 4. SSL reverse proxy beállítása (példa nginx)
# A Let's Encrypt cert a frontend konténer előtt legyen

# 5. Konténerek indítása
docker compose --profile prod up -d

# 6. Ellenőrzés
./scripts/smoke-test.sh
```

### Demo admin user
- **Email:** `admin@tanszek.local`
- **Jelszó:** `ChangeMe123!` (first-login kötelező csere)

## 2. Napi üzemeltetés

### Backup ellenőrzés
```bash
# Legutóbbi backup fájlok listázása
docker compose exec backup ls -t /var/backups/*.sql | head -5

# Backup méret ellenőrzés
docker compose exec backup du -sh /var/backups/

# Backup könyvtár a hoszton (named volume)
docker volume inspect device-storage-backup-data
```

### Scheduled job státusz
A 3 scheduled job ellenőrzése a logokból:
- `refresh-token-cleanup` – napi 04:00-kor fut
- `audit-retention-job` – heti vasárnap 03:00-kor fut
- `backup` konténer saját cron – napi 02:00-kor fut

```bash
# Összes @Scheduled job log
docker compose logs backend | grep "Scheduled job"

# Egy adott job legutóbbi futásának keresése
docker compose logs backend | grep "audit-retention-job" | tail -10
```

### Log-ok ellenőrzése
```bash
# Összes konténer logja
docker compose logs --tail=100

# Csak a backend logja request_id-val
docker compose logs backend | grep "request_id=abc-123"
```

### Disk usage
```bash
# PostgreSQL adatméret
docker compose exec postgres du -sh /var/lib/postgresql/data

# Audit log archív méret
docker compose exec backend du -sh /var/backups/archive/audit/

# Device attachment méret
docker compose exec backend du -sh /var/uploads/
```

## 3. Gyakori hibák + megoldás

### DB connection failed
**Hiba:** `org.postgresql.Net.PSQLException: Connection refused`
**Megoldás:**
- Ellenőrizd a postgres konténer fut-e: `docker compose ps postgres`
- Ellenőrizd a `SPRING_DATASOURCE_URL` a `.env`-ben: `jdbc:postgresql://postgres:5432/tanszek_db`
- Ellenőrizd a `POSTGRES_PASSWORD` megegyezik-e a backend `.env` és a postgres konténer között

### JWT invalid signature
**Hiba:** `io.jsonwebtoken.security.SignatureException: JWT signature does not match`
**Megoldás:**
- A `JWT_KID_ACTIVE` értéke megváltozott, de a régi refresh token még érvényes
- Várj a grace period leteltéig (alapértelmezetten 3600 sec = 1 óra)
- Vagy logout + új login

### Refresh token expired
**Hiba:** `Refresh token expired` toast a frontend-en
**Megoldás:** Normál viselkedés – 30 nap után a refresh token lejár, a user újra be kell jelentkezzen

### Bucket4j 429 (rate limit)
**Hiba:** A frontend "Too Many Requests" toast-ot kap
**Megoldás:**
- Várj 1 percet (per-IP) vagy 1 órát (per-email)
- A `RATE_LIMIT_*` env var-okkal lehet módosítani a limiteket (production-ben: alkalmazás restart)

### Attachment upload túl nagy
**Hiba:** `File too large (max 5MB)`
**Megoldás:**
- A backend 5MB limitet alkalmaz service-szinten
- A frontend is ellenőrzi kliens-oldalon (react-dropzone maxSize)
- Ha production-ben nagyobb fájlok kellenek, módosítsd a `MAX_FILE_SIZE` konstansot a `AttachmentService`-ben

### Audit rollback permission denied
**Hiba:** `permissionDenied` toast
**Megoldás:** Csak `AUDIT_ROLLBACK` permission-nel rendelkező user rollbackolhat (alapértelmezetten csak ROLE_ADMIN)

## 4. Rollback eljárás

### Audit log rollback
```bash
# POST /api/audit/rollback/{id} endpoint hívása
curl -X POST http://localhost:8080/api/audit/rollback/123 \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -b /tmp/cookies.txt
```

### Konténer rollback (új konténer image)
```bash
# 1. Aktuális image-ek listázása
docker images | grep device-storage

# 2. Backup image-ek (vagy rollback commit checkout)
git log --oneline
git checkout <previous-commit>

# 3. Konténer újraépítés
docker compose build
docker compose up -d
```

### DB rollback
Lásd: `scripts/backup-restore.sh` (5. szekció)

## 5. Backup restore

```bash
# Legutóbbi backup visszaállítása
./scripts/backup-restore.sh latest

# Pontos timestamp megadása
./scripts/backup-restore.sh 2026-07-29_02-00-00

# Specifikus dátum
./scripts/backup-restore.sh 2026-07-29
```

A script:
1. Leállítja a backend konténert
2. Drop + recreate a DB
3. psql restore a backup fájlból
4. Újraindítja a backend konténert
5. Health check (30 sec)

## 6. Credential rotation

### JWT secret rotáció grace period-dal
```bash
# Alapértelmezetten 1 óra grace period
./scripts/rotate-jwt-secret.sh

# Egyedi grace period
GRACE_PERIOD=7200 ./scripts/rotate-jwt-secret.sh  # 2 óra
```

A script:
1. Backup a `.env` fájlról (.env.backup.{timestamp})
2. Új 256-bit secret generálás (openssl rand -base64 32)
3. `JWT_KID_PREVIOUS` = régi active (grace period)
4. `JWT_KID_ACTIVE` = új
5. Backend restart
6. Health check

A grace period letelte után a `JWT_KID_PREVIOUS` törölhető a `.env`-ből.

### Database password rotáció
```bash
# 1. Új jelszó generálás
NEW_PASS=$(openssl rand -base64 32)

# 2. .env frissítése
sed -i.bak "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=$NEW_PASS|" .env

# 3. Postgres konténer újraindítása
docker compose up -d postgres

# 4. Új jelszó alkalmazása a postgres user-en
docker compose exec postgres psql -U admin -d postgres -c "ALTER USER admin WITH PASSWORD '$NEW_PASS';"

# 5. Backend konténer újraindítása
docker compose up -d backend
```

## 7. Incident response

### Security breach esetén

1. **Azonnali izoláció:**
   ```bash
   # Backend konténer leállítása
   docker compose stop backend
   # Network izoláció
   docker network disconnect device-storage-network device-storage-backend
   ```

2. **Érintett userek deaktiválása:**
   ```bash
   # Database-be közvetlen belépés
   docker compose exec postgres psql -U admin -d tanszek_db \
     -c "UPDATE app_users SET active = false WHERE email_hash IN ('hash1', 'hash2');"
   ```

3. **Refresh token-ek bulk revoke:**
   ```bash
   docker compose exec postgres psql -U admin -d tanszek_db \
     -c "UPDATE refresh_tokens SET revoked = true WHERE user_id IN (123, 456);"
   ```

4. **Audit log export:**
   ```bash
   # Érintett időszak logjai
   docker compose logs --since="2026-07-29T08:00:00" --until="2026-07-29T18:00:00" backend > incident-logs.txt
   ```

5. **Értesítési lánc:**
   - Azonnali: rendszergazda + IT biztonsági vezető
   - 24 órán belül: GDPR bejelentés (ha személyes adat érintett)
   - 72 órán belül: felhasználók értesítése (ha jelszavak kompromittálódtak)

6. **Post-incident:**
   - Audit log review (Task 5.4)
   - Jelszó force-reset minden user számára
   - JWT secret rotáció (Task 5.4 6. szekció)
   - Incident report készítése
