package hu.tanszek.device.auth.jwt;

import hu.tanszek.device.auth.entity.RefreshToken;
import hu.tanszek.device.auth.repository.RefreshTokenRepository;
import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.NoSuchElementException;

/**
 * Refresh token service — RFC 6819 kompatibilis rotation.
 *
 * <p>Flow:
 * <ol>
 *   <li>issue(): random token generálása + SHA-256 hash DB tárolás + user-hez kötés</li>
 *   <li>rotate(): régi token revoke + új token issue (a régi replacedById az újra mutat)</li>
 *   <li>validate(): token hash lookup, ha revoked → reuse detection (egész chain revoke)</li>
 *   <li>revoke(): token revoke (logout)</li>
 * </ol>
 *
 * <p>A plain token soha nincs DB-ben — csak a SHA-256 hash. A plain token
 * a HttpOnly Secure SameSite=Strict cookie-ban utazik a kliens és a backend között.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    /** Refresh token hossza (32 byte = 256 bit) */
    private static final int TOKEN_LENGTH = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final AppUserRepository appUserRepository;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Új refresh token generálása egy user számára.
     *
     * <p>Visszaadja a plain token-t (amit a HttpOnly cookie-ba kell tenni) ÉS
     * a RefreshToken entitást (DB-ben tárolva). A plain token soha többé
     * nem állítható elő — csak a hash van DB-ben.
     *
     * @param user a user, akihez a token tartozik
     * @return IssueResult tartalmazza a plain token-t és az entitást
     */
    @Transactional
    public IssueResult issue(AppUser user) {
        byte[] tokenBytes = new byte[TOKEN_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        String plainToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String tokenHash = sha256(plainToken);

        Instant expiresAt = Instant.now().plus(jwtProperties.getRefreshTokenTtlDays(), ChronoUnit.DAYS);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .revoked(false)
                .build();

        refreshToken = refreshTokenRepository.save(refreshToken);

        log.info("Issued refresh token for user {}, expires at {}", user.getEmailHash(), expiresAt);
        return new IssueResult(plainToken, refreshToken);
    }

    /**
     * Refresh token rotáció.
     *
     * <p>Ha a token valid és nem revoked: revoke + új issue. A régi token
     * {@code replacedById} az új tokenre mutat (rotation chain).
     *
     * <p>Ha a token már revoked: reuse detection — az egész chain revokeolódik.
     *
     * @param plainToken a HttpOnly cookie-ból kapott plain refresh token
     * @return RotationResult az új plain token-nel és entitással
     * @throws NoSuchElementException ha a token nem található
     */
    @Transactional
    public RotationResult rotate(String plainToken) {
        String tokenHash = sha256(plainToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new NoSuchElementException("Refresh token not found"));

        // Reuse detection: ha revoked, az egész chain revokeolódik
        if (token.isRevoked()) {
            log.warn("Refresh token reuse detected for user {} — revoking entire chain",
                    token.getUser().getEmailHash());
            revokeEntireChain(token);
            throw new IllegalStateException("Refresh token reuse detected — chain revoked");
        }

        // Lejárt check
        if (token.isExpired()) {
            log.info("Refresh token expired for user {}", token.getUser().getEmailHash());
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            throw new IllegalStateException("Refresh token expired");
        }

        // Sikeres rotáció
        token.setRevoked(true);
        IssueResult newIssue = issue(token.getUser());
        newIssue.refreshToken().setReplacedBy(token);
        refreshTokenRepository.save(newIssue.refreshToken());
        refreshTokenRepository.save(token);

        return new RotationResult(newIssue.plainToken(), newIssue.refreshToken());
    }

    /**
     * Refresh token revoke (logout).
     *
     * @param plainToken a HttpOnly cookie-ból
     */
    @Transactional
    public void revoke(String plainToken) {
        String tokenHash = sha256(plainToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            log.info("Revoked refresh token for user {}", token.getUser().getEmailHash());
        });
    }

    /**
     * Rotation chain teljes revokeolása (reuse detection).
     *
     * <p>A token chain-jén végigmegyünk a replacedById láncolással, és
     * minden tokent revokeolunk.
     */
    private void revokeEntireChain(RefreshToken startToken) {
        RefreshToken current = startToken;
        int revokedCount = 0;

        // Visszafelé haladva a chain-en
        while (current != null) {
            if (!current.isRevoked()) {
                current.setRevoked(true);
                refreshTokenRepository.save(current);
                revokedCount++;
            }
            // Előre haladva (replacedBy láncolás)
            current = current.getReplacedBy();
        }

        log.warn("Revoked {} refresh tokens in chain (reuse detection)", revokedCount);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Issue eredménye: plain token + DB entitás */
    public record IssueResult(String plainToken, RefreshToken refreshToken) {}

    /** Rotation eredménye: új plain token + új DB entitás */
    public record RotationResult(String newPlainToken, RefreshToken newRefreshToken) {}
}