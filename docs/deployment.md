# Deployment

## Első telepítés (production)

1. **Szerver előfeltételek:**
   - Docker Engine 24+ és Docker Compose v2
   - 4 GB RAM minimum, 10 GB szabad lemezterület
   - Portok: 80 (frontend), 8080 (backend belső, nem exposed), 5432 (postgres belső)
   - SSL reverse proxy (nginx, traefik, vagy Caddy) — a `/etc/letsencrypt` certbot-tal

2. **Repo klónozás:**
   ```bash
   git clone <repo-url> /opt/device-storage
   cd /opt/device-storage
   ```

3. **Bootstrap:**
   ```bash
   chmod +x scripts/*.sh
   ./scripts/bootstrap.sh
   ```
   Ez legenerálja a `.env` és `backup.env` fájlokat, plusz a JWT secret + Crypto AES key-t.

4. **Production env kitöltés:**
   ```bash
   nano .env
   ```
   - `SPRING_MAIL_PASSWORD` — valódi SMTP jelszó
   - `JWT_KID_ACTIVE` — marad a bootstrap által generált
   - `OTLP_ENDPOINT` — pl. `http://jaeger.internal:4317`
   - `SPRING_PROFILES_ACTIVE=prod`

5. **Indítás:**
   ```bash
   docker compose up -d
   docker compose ps  # ellenőrzés
   docker compose logs -f backend  # logok
   ```

6. **Első belépés:**
   - Böngésző: `http://<server-ip>`
   - Email: `admin@tanszek.local`
   - Password: `ChangeMe123!`
   - **First-login kötelező jelszócsere** (a `must_change_password` flag miatt)

## Frissítés

```bash
cd /opt/device-storage
git pull
docker compose build --no-cache
docker compose up -d
# Flyway migrációk automatikusan futnak
docker compose logs -f backend
```

## Rollback

- **Konténer rollback:** `docker compose down && git checkout <previous-commit> && docker compose up -d`
- **DB rollback:** `scripts/backup-restore.sh YYYY-MM-DD.sql` — részletek a runbookban
- **Audit log rollback:** `POST /api/audit/rollback/{audit_log_id}` — admin-only

## Backup

- Napi pg_dump a backup konténerből 02:00-kor, `backup_data` volume-ba (30 nap retention).
- Backup ellenőrzés: `docker compose exec backup ls -la /var/backups/`
- Hoszt gépről: `docker volume inspect backup_data` → `Mountpoint` könyvtárban vannak a `.sql` fájlok.

Részletek: [`runbook.md`](./runbook.md) (Task 5.4-ben készül el).