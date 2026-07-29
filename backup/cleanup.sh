#!/usr/bin/env bash
# cleanup.sh — törli a BACKUP_RETENTION_DAYS napnál régebbi backup fájlokat
# A backup.sh hívja minden futás után.

set -euo pipefail

BACKUP_DIR="/var/backups"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-30}"

# Log
echo "[$(date -Iseconds)] Cleanup indítása (retention: ${RETENTION_DAYS} nap)"

# Régi fájlok törlése (biztonságos -find parancsokkal)
DELETED_COUNT=0
while IFS= read -r -d '' file; do
    echo "  Törlés: ${file}"
    rm -f "${file}"
    DELETED_COUNT=$((DELETED_COUNT + 1))
done < <(find "${BACKUP_DIR}" \
    -maxdepth 1 \
    -type f \
    -name "*.sql" \
    -mtime "+${RETENTION_DAYS}" \
    -print0)

echo "[$(date -Iseconds)] Cleanup kész: ${DELETED_COUNT} fájl törölve"

# Aktuális backup-ok listázása
BACKUP_COUNT=$(find "${BACKUP_DIR}" -maxdepth 1 -type f -name "*.sql" | wc -l)
echo "[$(date -Iseconds)] Jelenlegi backup-ok száma: ${BACKUP_COUNT}"