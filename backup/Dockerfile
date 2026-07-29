# Backup Container - pg_dump cron
# Alpine Linux + PostgreSQL client + dcron + bash
# Non-root user (backup), read-only root FS, write only /var/backups (backup_data volume)

FROM alpine:3.19

# Telepítés: postgresql-client (pg_dump), dcron (cron), bash, curl (healthcheck)
RUN apk add --no-cache \
    postgresql16-client \
    dcron \
    bash \
    curl \
    tzdata

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

# Jogosultságok
RUN chmod +x /usr/local/bin/backup.sh /usr/local/bin/cleanup.sh && \
    chmod 0644 /etc/crontabs/backup

# User váltás backup-ra
USER backup
WORKDIR /home/backup

# Crond futtatás foreground-ban (a konténer életben tartásához)
CMD ["crond", "-f", "-d", "0"]