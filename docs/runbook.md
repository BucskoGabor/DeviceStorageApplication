# Runbook

> Ez a dokumentum a Fázis 5 / Task 5.4-ben lesz teljesen kidolgozva (7 szekció: első telepítés, napi üzemeltetés, gyakori hibák, rollback eljárás, backup restore, credential rotation, incident response).
>
> Addig is lásd: [`deployment.md`](./deployment.md) az első telepítéshez.

## Jelenlegi tartalom (placeholder)

- **Első telepítés** — lásd [`deployment.md`](./deployment.md#első-telepítés-production)
- **Backup** — `docker compose exec backup ls /var/backups/` — napi pg_dump fájlok
- **Audit rollback** — `POST /api/audit/rollback/{id}` (csak ADMIN + AUDIT_ROLLBACK permission)