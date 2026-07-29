package hu.tanszek.device.auth.repository;

import hu.tanszek.device.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Refresh token repository.
 *
 * @see RefreshToken
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Refresh token keresése SHA-256 hash alapján.
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * User összes aktív refresh token listája.
     */
    List<RefreshToken> findByUserIdAndRevokedFalse(Long userId);

    /**
     * Rotation chain lookup (a használt revoked token alapján).
     */
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.replacedBy IS NOT NULL " +
           "AND rt.id IN (SELECT r.replacedBy.id FROM RefreshToken r WHERE r.id = :startId)")
    Optional<RefreshToken> findChainRoot(@Param("startId") Long startId);

    /**
     * Cleanup query: 7+ napos lejárt/revoked tokenek törlése.
     *
     * <p>Native SQL kell, mert a JPQL nem támogatja az OR-t jól +
     * a JPA bulk delete-ek az entity manager cache-t is invalidálják.
     */
    @Modifying
    @Query(value = """
            DELETE FROM refresh_tokens
            WHERE expires_at < :cutoff
               OR (revoked = true AND created_at < :cutoff)
            """, nativeQuery = true)
    int deleteOldTokens(@Param("cutoff") Instant cutoff);
}