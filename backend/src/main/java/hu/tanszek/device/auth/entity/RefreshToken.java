package hu.tanszek.device.auth.entity;

import hu.tanszek.device.common.BaseEntity;
import hu.tanszek.device.user.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * JWT refresh token entitás (rotation chain, RFC 6819).
 *
 * <p>A refresh tokent SHA-256 hash-szel tároljuk ({@code tokenHash}). A plain
 * token soha nincs adatbázisban — csak a HttpOnly Secure SameSite=Strict
 * cookie-ban a kliensnél.
 *
 * <p>Rotation chain:
 * <ul>
 *   <li>Minden refresh híváskor új refresh token generálódik</li>
 *   <li>A régi token {@code revoked = true} lesz, és {@code replacedById}
 *       mutat az új tokenre</li>
 *   <li>Ha egy revoked tokennel próbálkoznak, az egész chain revokeolódik
 *       (reuse detection)</li>
 * </ul>
 *
 * <p>Cleanup: napi 04:00-kor {@code @Scheduled} job törli a 7+ napos
 * lejárt/revoked tokeneket.
 *
 * @see hu.tanszek.device.auth.repository.RefreshTokenRepository
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class RefreshToken extends BaseEntity<Long> {

    /** A user, akihez a refresh token tartozik */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    /** SHA-256 hash a refresh token értékből (egyedi) */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** Lejárat időbélyege (30 nap a létrehozástól) */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** true = nem használható (rotation vagy logout miatt) */
    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    /** A token cseréje során erre az új tokenre mutat (rotation chain) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_id")
    private RefreshToken replacedBy;

    /**
     * Helper: ellenőrzi, hogy a token lejárt-e.
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Helper: ellenőrzi, hogy a token használható-e (nem revoked és nem lejárt).
     */
    public boolean isActive() {
        return !revoked && !isExpired();
    }
}