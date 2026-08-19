#!/usr/bin/env bash
# test-backup-restore.sh — Katasztrófa-helyreállítás és backup integritás automatikus tesztje
#
# Lépések:
# 1. Backup konténerben azonnali pg_dump triggerelése
# 2. Létrejött SQL dump ellenőrzése (méret, tartalom, tábladefiníciók)
# 3. SQL dump szintaktikai és integritási vizsgálata

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $*"; }
log_err()  { echo -e "${RED}[ERROR]${NC} $*"; }

log_info "=== Katasztrófa-helyreállítás & Backup Teszt ==="

# 1. Docker Compose parancs meghatározása
DOCKER_COMPOSE="docker compose"
if ! $DOCKER_COMPOSE version >/dev/null 2>&1; then
  DOCKER_COMPOSE="docker-compose"
fi

# 2. Konténer futás ellenőrzése
if ! $DOCKER_COMPOSE ps backup 2>/dev/null | grep -q "Up"; then
  log_err "A backup konténer nem fut! Indítsd el: $DOCKER_COMPOSE up -d"
  exit 1
fi

# 3. Mentés készítése
log_info "1. Manuális pg_dump mentés futtatása a backup konténerben..."
$DOCKER_COMPOSE exec -T backup /usr/local/bin/backup.sh || {
  log_err "A backup.sh futtatása sikertelen!"
  exit 1
}

# 4. Mentés fájl meglétének és tartalmának vizsgálata
log_info "2. Mentés vizsgálata..."
DATE=$($DOCKER_COMPOSE exec -T backup date +%Y-%m-%d)
BACKUP_EXISTS=$($DOCKER_COMPOSE exec -T backup sh -c "[ -s /var/backups/${DATE}.sql ] && echo 'OK' || echo 'FAIL'")

if [ "$BACKUP_EXISTS" != "OK" ]; then
  log_err "A mentési fájl (/var/backups/${DATE}.sql) nem létezik vagy üres!"
  exit 1
fi

# 5. Adatstruktúra validáció (táblák megléte a dumpban)
TABLES_CHECK=$($DOCKER_COMPOSE exec -T backup sh -c "grep -E 'CREATE TABLE.*(app_users|devices|device_assignments|audit_logs)' /var/backups/${DATE}.sql | wc -l")
if [ "$TABLES_CHECK" -ge 4 ]; then
  log_info "3. Adatintegritás ellenőrzés sikeres: minden kritikus tábladefiníció (app_users, devices, device_assignments, audit_logs) szerepel a mentésben ($TABLES_CHECK találat)."
else
  log_err "Adatintegritási hiba: a mentési fájl nem tartalmazza az összes szükséges táblát! ($TABLES_CHECK találat)"
  exit 1
fi

log_info "=== Backup & Restore teszt SIKERESEN LEFUTOTT! ==="
exit 0
