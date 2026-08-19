#!/usr/bin/env bash
# rotate-jwt-secret.sh — JWT kid rotáció grace period-dal
#
# Használat:
#   ./scripts/rotate-jwt-secret.sh                  # Új secret generál, grace period 1 óra
#   GRACE_PERIOD=3600 ./scripts/rotate-jwt-secret.sh  # Egyedi grace period
#
# Flow:
#   1. Új 256-bit secret generálás (openssl rand -base64 32)
#   2. A jelenlegi active secret átkerül a previous-be
#   3. Az új secret az active
#   4. A .env fájl frissítése
#   5. Backend újraindítása (docker compose restart backend)
#   6. Grace period alatt a régi secret is elfogadott (1 óra)

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

GRACE_PERIOD="${GRACE_PERIOD:-3600}"  # 1 óra default

# ===== Konfiguráció backup =====
cp .env .env.backup.$(date +%Y%m%d_%H%M%S)
log_info "Backup készítve: .env.backup.$(date +%Y%m%d_%H%M%S)"

# ===== Jelenlegi secret ellenőrzése =====
if ! grep -q "^JWT_KID_ACTIVE=" .env; then
  log_err "JWT_KID_ACTIVE nincs a .env fájlban. Futtasd a bootstrap.sh-t először."
  exit 1
fi

# ===== Új secret generálás =====
log_info "Új 256-bit JWT secret generálás..."
NEW_SECRET=$(openssl rand -base64 32)
log_info "Új secret: ${NEW_SECRET:0:8}..."

# ===== .env frissítés =====
# A jelenlegi active átkerül a previous-be, az új lesz az active
CURRENT_ACTIVE=$(grep "^JWT_KID_ACTIVE=" .env | cut -d'=' -f2-)
log_info "Jelenlegi active secret átmozgatása a previous-be (grace period $GRACE_PERIOD másodperc)"

python3 - "$NEW_SECRET" "$CURRENT_ACTIVE" "$GRACE_PERIOD" << 'EOF'
import sys

new_active = sys.argv[1]
prev_active = sys.argv[2]
grace = sys.argv[3]

with open('.env', 'r') as f:
    lines = f.readlines()

has_prev = any(l.startswith('JWT_KID_PREVIOUS=') for l in lines)
new_lines = []
for line in lines:
    if line.startswith('JWT_KID_PREVIOUS='):
        new_lines.append(f'JWT_KID_PREVIOUS={prev_active}\n')
    elif line.startswith('JWT_KID_ACTIVE='):
        if not has_prev:
            new_lines.append(f'JWT_KID_PREVIOUS={prev_active}\n')
            has_prev = True
        new_lines.append(f'JWT_KID_ACTIVE={new_active}\n')
    elif line.startswith('JWT_KID_GRACE_PERIOD_SEC='):
        new_lines.append(f'JWT_KID_GRACE_PERIOD_SEC={grace}\n')
    else:
        new_lines.append(line)

with open('.env', 'w') as f:
    f.writelines(new_lines)
EOF

log_info "JWT_KID_ACTIVE frissítve"
log_info "JWT_KID_PREVIOUS a régi active-re állítva (grace period: $GRACE_PERIOD sec)"

# ===== Backend újraindítása =====
log_info "Backend konténer újraindítása..."
docker compose restart backend

# ===== Health check =====
log_info "Várakozás a backend health check-re..."
for i in 1 2 3 4 5 6 7 8 9 10; do
  sleep 3
  if curl -s -o /dev/null -w "%{http_code}" "http://localhost:8080/actuator/health" | grep -q "200"; then
    log_info "Backend sikeresen újraindult (${i}. próbálkozásra)"
    log_info ""
    log_info "A grace period $GRACE_PERIOD másodpercig tart — ez alatt a régi secret is elfogadott."
    log_info "A grace period letelte után a JWT_KID_PREVIOUS törölhető a .env-ből."
    exit 0
  fi
  log_info "Várakozás... ($i/10)"
done

log_warn "Backend 30 másodperc alatt nem indult el — ellenőrizd manuálisan"
exit 1
