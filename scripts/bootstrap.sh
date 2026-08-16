#!/usr/bin/env bash
# bootstrap.sh — első indítás a projekt klónozása után
#
# Feladatok:
#   1. .env.example → .env másolása (ha .env még nem létezik)
#   2. backup.env.example → backup.env másolása (ha backup.env még nem létezik)
#   3. JWT secret és Crypto AES key generálása (ha még placeholder a .env-ben)
#   4. Ellenőrzés, hogy a Docker elérhető
#
# Exit code 0 = sikeres, nem 0 = hiba.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

# Színek a kimenethez
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_err()  { echo -e "${RED}[ERROR]${NC} $*"; }

# 1. .env másolása
if [ ! -f ".env" ]; then
    if [ -f ".env.example" ]; then
        cp .env.example .env
        log_info ".env létrehozva a .env.example alapján"
    else
        log_err ".env.example nem található!"
        exit 1
    fi
else
    log_warn ".env már létezik, kihagyva"
fi

# 2. backup.env másolása
if [ ! -f "backup.env" ]; then
    if [ -f "backup.env.example" ]; then
        cp backup.env.example backup.env
        log_info "backup.env létrehozva a backup.env.example alapján"
    else
        log_err "backup.env.example nem található!"
        exit 1
    fi
else
    log_warn "backup.env már létezik, kihagyva"
fi

# 3. JWT secret és Crypto AES key generálása (ha placeholder)
generate_secret() {
    # openssl rand -base64 32 → 32 byte = 256 bit Base64 kódolással
    openssl rand -base64 32
}

if grep -q "^JWT_KID_ACTIVE=<base64-256-bit-secret>" .env; then
    NEW_SECRET=$(generate_secret)
    # macOS és Linux sed különbözik — itt portable megoldás
    sed -i.bak "s|^JWT_KID_ACTIVE=<base64-256-bit-secret>|JWT_KID_ACTIVE=${NEW_SECRET}|" .env
    rm -f .env.bak
    log_info "JWT_KID_ACTIVE generálva (256-bit Base64)"
fi

if grep -q "^CRYPTO_AES_KEY=<base64-256-bit-key>" .env; then
    NEW_KEY=$(generate_secret)
    sed -i.bak "s|^CRYPTO_AES_KEY=<base64-256-bit-key>|CRYPTO_AES_KEY=${NEW_KEY}|" .env
    rm -f .env.bak
    log_info "CRYPTO_AES_KEY generálva (256-bit Base64)"
fi

# 4. SMTP password placeholder ellenőrzése (production-höz kötelező kitölteni)
if grep -q "^SPRING_MAIL_PASSWORD=<smtp-password>" .env; then
    log_warn "SPRING_MAIL_PASSWORD még placeholder — production build előtt töltsd ki!"
fi

# 5. Docker elérhetőség ellenőrzése
if command -v docker &>/dev/null; then
    log_info "Docker megtalálva: $(docker --version)"
    if command -v docker compose &>/dev/null; then
        log_info "Docker Compose (v2) elérhető"
    elif command -v docker-compose &>/dev/null; then
        log_warn "Docker Compose v1 (docker-compose) — javasolt a v2-re frissítés"
    else
        log_warn "Docker Compose nem található — a docker-compose up parancs nem fog működni"
    fi
else
    log_err "Docker nem található — telepítsd a Docker Desktop / Docker Engine-t"
    exit 1
fi

log_info "Bootstrap kész!"
echo ""
echo "Következő lépések:"
echo "  1. Szerkeszd a .env fájlt (SMTP password, production kulcsok)"
echo "  2. docker compose up -d"
echo "  3. Nyisd meg a http://localhost:5173 -t (Vite dev) vagy http://localhost:80 -at (Nginx prod)"
echo "  4. Lépj be admin@tanszek.local / ChangeMe123! — first-login kötelező csere"