# ===== Frontend Dockerfile - Multi-stage build =====
# Stage 1: Build (Node 20 + Vite)
# Stage 2: Runtime (Nginx 1.27 + Alpine, non-root user)

# ===== STAGE 1: Build =====
FROM node:20-alpine AS build
WORKDIR /app

# Cache npm dependencies
COPY package*.json ./
RUN npm ci

# Copy source and build
COPY . .
RUN npm run build

# ===== STAGE 2: Runtime (Nginx) =====
FROM nginx:1.27-alpine

# Az alapértelmezett nginx image-ben már létezik egy `nginx` user (uid 101).
# A non-root user-ként futtatás biztonsági baseline: ha egy nginx exploit
# sikeres, a támadó a konténeren belül nem kap root jogosultságot.
# A nginx user a bind-mountolt volume-okhoz NEM fér hozzá, csak a /usr/share/nginx/html
# alatti read-only másolatot olvassa.

# A nginx user saját temp könyvtárai (cache, proxy, client_body, fastcgi, uwsgi, scgi)
RUN mkdir -p /var/cache/nginx /var/log/nginx /var/run \
    && chown -R nginx:nginx /var/cache/nginx /var/log/nginx /var/run \
    && touch /var/run/nginx.pid \
    && chown nginx:nginx /var/run/nginx.pid \
    && chown -R nginx:nginx /usr/share/nginx/html

# A /etc/nginx mappa alapértelmezetten root-owned marad — a configot nem kell
# futásidőben írni, így ez nem okoz problémát.

# Copy built assets (root-ként másolunk, utána chown-olunk az nginx usernek)
COPY --from=build --chown=nginx:nginx /app/dist /usr/share/nginx/html

# Copy Nginx config
COPY --chown=nginx:nginx nginx.conf /etc/nginx/conf.d/default.conf

# User váltás — minden további parancs nginx userként fut.
USER nginx

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD wget --spider -q http://127.0.0.1:8080/ || exit 1

# A 8080-as porton hallgatunk (a non-root user nem bindelhet 1024 alatti portra
# privilege nélkül). A docker-compose-ban a 8080-as port van mapping-olva a host 80-as
# portjára, így a user-facing végpont változatlan marad.
EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]