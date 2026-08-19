# Backup Container - pg_dump cron
# Alpine Linux + PostgreSQL client + dcron + bash
# Non-root user (backup), read-only root FS, write only /var/backups (backup_data volume)

FROM alpine:3.19

# Telepítés: postgresql-client (pg_dump), dcron (cron), bash, curl (healthcheck)
RUN apk add --no-cache \
    postgresql-client \
    dcron \
    bash \
    curl \
    tzdata \
    su-exec

# Időzóna beállítás
ENV TZ=UTC

# Non-root user
RUN addgroup -g 1000 backup && \
    adduser -D -u 1000 -G backup backup && \
    mkdir -p /var/backups && \
    chown -R backup:backup /var/backups

# Scriptek másolása (root-ként, hogy a jogosultságok rendben legyenek)
COPY --chown=backup:backup backup.sh /usr/local/bin/backup.sh
COPY --chown=backup:backup cleanup.sh /usr/local/bin/cleanup.sh
COPY --chown=backup:backup crontab.txt /etc/crontabs/backup
# backup.env.example bemásolása /etc/backup.env néven a konténerbe.
# A docker-compose env_file direktíva felülírja ezt a fájlt a valódi titkokkal
# futásidőben, így a backup.sh a /etc/backup.env-ből mindig tud olvasni.
COPY --chown=backup:backup backup.env.example /etc/backup.env

# Jogosultságok
RUN chmod +x /usr/local/bin/backup.sh /usr/local/bin/cleanup.sh && \
    chmod 0644 /etc/crontabs/backup
# Crond root-ként fut, mert a non-root user CAP_SETGID nélkül indul
# (a --cap-add SETGID csak bounding set-be kerül, effektívbe nem), és a
# BusyBox dcron setpgid hívásához effektív CAP_SETGID kell. A tényleges
# backup parancs su-exec-kel backup userre vált.
#
# A CMD `sh -c "..."` formátumban van, hogy a BusyBox dcron ne legyen
# közvetlenül PID 1 — a kernel EPERM-et ad a setpgid(0,0) hívásra, ha
# a hívó processz a PID-namespace init folyamata. Az sh PID 1, a crond
# child processzként indul, és a setpgid sikeresen lefut.
USER root
WORKDIR /home/backup
# A `crond &` háttérbe küldése + `wait` kell, mert különben a busybox sh az
# utolsó parancsot automatikusan exec-eli, és a crond PID 1-gyé válik.
# A BusyBox dcron setpgid(0,0) hívása EPERM-et ad PID 1-ként (PID ns init
# folyamat nem válthat sessiont), ezért kell a sh mint PID 1 + crond child.
CMD ["sh", "-c", "crond -f -d 0 & wait"]