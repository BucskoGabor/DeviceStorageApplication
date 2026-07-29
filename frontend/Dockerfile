# Frontend Dockerfile - Multi-stage build
# Build stage: Node 20 + Vite
# Runtime stage: Nginx Alpine

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

# Copy built assets
COPY --from=build /app/dist /usr/share/nginx/html

# Copy Nginx config
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD wget -qO- http://localhost:80/ || exit 1

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]