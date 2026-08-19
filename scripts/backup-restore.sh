#!/usr/bin/env bash
# backup-restore.sh — pg_dump visszaállítás a backup_data volume-ból
#
# Használat:
#   ./scripts/backup-restore.sh 2026-07-29              # Restore a mai dátummal
#   ./scripts/backup-restore.sh 2026-07-29_14-30-00    # Restore pontos timestamp-pel
#   ./scripts/backup-restore.sh latest                # Restore a legutóbbi backup
#
# Feltételezi, hogy a postgres + backup konténer fut.
# Ha a DB down, először leállítja a backend konténert (megakadályozza a connection-öket),
# aztán visszaállítja a DB-t, aztán újraindítja a backend konténert.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_err()  { echo -e "${RED}[ERROR]${NC} $*"; }

# ===== Konfiguráció =====
BACKUP_DATE="${1:-latest}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-device-storage-postgres}"
BACKEND_CONTAINER="${BACKEND_CONTAINER:-device-storage-backend}"
BACKUP_CONTAINER="${BACKUP_CONTAINER:-device-storage-backup}"
POSTGRES_DB="${POSTGRES_DB:-tanszek_db}"
POSTGRES_USER="${POSTGRES_USER:-admin}"

cleanup_on_error() {
  log_err "An error occurred during restore. Ensuring backend container is started..."
  docker start "$BACKEND_CONTAINER" 2>/dev/null || true
}
trap cleanup_on_error ERR
# ===== Backup fájl keresése =====
BACKUP_FILE=""

if [ "$BACKUP_DATE" = "latest" ]; then
  log_info "Looking for latest backup..."
  BACKUP_FILE=$(docker exec "$BACKUP_CONTAINER" ls -t /var/backups/*.sql 2>/dev/null | head -1 | tr -d '\r')
  if [ -z "$BACKUP_FILE" ]; then
    log_err "No backup files found in /var/backups"
    exit 1
  fi
else
  BACKUP_FILE="/var/backups/${BACKUP_DATE}.sql"
  if ! docker exec "$BACKUP_CONTAINER" test -f "$BACKUP_FILE" 2>/dev/null; then
    log_err "Backup file not found: $BACKUP_FILE"
    exit 1
  fi
fi

log_info "Selected backup file: $BACKUP_FILE"

# ===== Backend leállítása =====
log_info "Stopping backend container..."
docker stop "$BACKEND_CONTAINER" || log_warn "Backend already stopped"

# ===== DB drop + recreate =====
log_info "Dropping and recreating database '$POSTGRES_DB'..."
docker exec "$POSTGRES_CONTAINER" \
  psql -U "$POSTGRES_USER" -d postgres -c "DROP DATABASE IF EXISTS $POSTGRES_DB;"
docker exec "$POSTGRES_CONTAINER" \
  psql -U "$POSTGRES_USER" -d postgres -c "CREATE DATABASE $POSTGRES_DB;"

# ===== Restore =====
log_info "Restoring backup..."
docker exec -i "$BACKUP_CONTAINER" cat "$BACKUP_FILE" | \
  docker exec -i "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"

log_info "Restore completed successfully"

# ===== Backend indítása =====
log_info "Starting backend container..."
docker start "$BACKEND_CONTAINER"

# ===== Health check =====
log_info "Waiting for backend health check..."
for i in 1 2 3 4 5; do
  sleep 5
  if curl -s -o /dev/null -w "%{http_code}" "http://localhost:8080/actuator/health" | grep -q "200"; then
    log_info "Backend is healthy"
    exit 0
  fi
  log_info "Waiting... ($i/5)"
done

log_warn "Backend did not become healthy in 25 seconds — check manually"
exit 0
