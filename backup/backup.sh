#!/usr/bin/env bash
# backup.sh — napi pg_dump a /var/backups/YYYY-MM-DD.sql fájlba
# Backup konténer futtatja 02:00-kor (UTC).

set -euo pipefail

BACKUP_DIR="/var/backups"
TIMESTAMP=$(date +%Y-%m-%d_%H-%M-%S)
DATE=$(date +%Y-%m-%d)
BACKUP_FILE="${BACKUP_DIR}/${DATE}.sql"

# Cron környezetben a dcron nem örökli automatikusan a konténer env-et,
# ezért a hiányzó változókat a /etc/backup.env fájlból töltjük be
# (a docker-compose env_file direktívával mountolja, vagy COPY-val kerül a képbe).
if [ -z "${PGPASSWORD:-}" ] && [ -f /etc/backup.env ]; then
  set -a
  # shellcheck disable=SC1091
  . /etc/backup.env
  set +a
fi

export PGPASSWORD="${PGPASSWORD:-${POSTGRES_PASSWORD:-}}"
# PostgreSQL connection params (env var-okból)
PG_HOST="${POSTGRES_HOST:-postgres}"
PG_DB="${POSTGRES_DB:-tanszek_db}"
PG_USER="${POSTGRES_USER:-admin}"
# Log

# pg_dump futtatása
pg_dump \
    --host="${PG_HOST}" \
    --port="${PG_PORT:-5432}" \
    --username="${PG_USER}" \
    --dbname="${PG_DB}" \
    --no-password \
    --format=plain \
    --no-owner \
    --no-privileges \
    --file="${BACKUP_FILE}"

# Ellenőrzés: a fájl nem üres
if [ ! -s "${BACKUP_FILE}" ]; then
    echo "[$(date -Iseconds)] HIBA: backup fájl üres (${BACKUP_FILE})" >&2
    exit 1
fi

# Méret
BACKUP_SIZE=$(du -h "${BACKUP_FILE}" | cut -f1)
echo "[$(date -Iseconds)] Backup kész: ${BACKUP_FILE} (${BACKUP_SIZE})"

# Cleanup is futtatása
/usr/local/bin/cleanup.sh