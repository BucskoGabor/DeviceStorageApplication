package hu.tanszek.device.user.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import hu.tanszek.device.user.entity.AppUser;

/**
 * AppUser repository.
 *
 * <p>Az {@code email_hash} (SHA-256) alapján történő keresés az {@code email_encrypted} (AES-GCM
 * titkosított) visszafejtése nélkül — gyors, minden request-en használható.
 *
 * <p>A {@link JpaSpecificationExecutor} a row-level szűréshez és jogosultsági lekérdezésekhez biztosít támogatást.
 *
 * @see AppUser
 */
@Repository
public interface AppUserRepository
    extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {

  /**
   * User keresése email_hash alapján, a role.permissions és user.permissions EAGER betöltésével
   * (N+1 query elkerülése).
   *
   * <p>CustomUserDetailsService.loadUserByUsername hívja login során.
   */
  @EntityGraph(attributePaths = {"role", "role.permissions", "permissions"})
  Optional<AppUser> findByEmailHash(String emailHash);

  /**
   * Find by ID with EAGER loading of role + permissions. Avoids N+1 query in controller endpoints.
   */
  @EntityGraph(attributePaths = {"role", "role.permissions", "permissions"})
  Optional<AppUser> findWithDetailsById(Long id);

  /** User keresése role alapján. */
  List<AppUser> findByRoleId(Long roleId);

  /** Ellenőrzi, hogy van-e user az adott role-lal. */
  boolean existsByRoleId(Long roleId);

  /** Aktív user-ek listája. */
  List<AppUser> findByActiveTrue();

  /** Locked_until lejárt user-ek — cleanup query. */
  @Query("SELECT u FROM AppUser u WHERE u.lockedUntil IS NOT NULL AND u.lockedUntil < :now")
  List<AppUser> findExpiredLocks(@Param("now") Instant now);

  /** Refresh token-ek bulk revoke deactivation-kor (UserService.deactivate). */
  @Modifying
  @Query(
      "UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.id = :userId AND rt.revoked = false")
  int revokeAllRefreshTokensByUserId(@Param("userId") Long userId);
}
