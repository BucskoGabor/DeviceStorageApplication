package hu.tanszek.device.auth.jwt;

import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import hu.tanszek.device.auth.entity.RefreshToken;
import hu.tanszek.device.auth.repository.RefreshTokenRepository;
import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tesztek a {@link RefreshTokenService}-hez.
 *
 * <p>A repository-k mockolva vannak, a JWT TTL a JwtProperties default értéke (30 nap).
 */
class RefreshTokenServiceTest {

  private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
  private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
  private final JwtProperties jwtProperties = new JwtProperties();
  private RefreshTokenService service;

  @BeforeEach
  void setUp() {
    service =
        new RefreshTokenService(refreshTokenRepository, appUserRepository, jwtProperties);
  }

  @Test
  void issue_savesTokenWithHashAndReturnsPlainToken() {
    AppUser user = buildUser();
    when(refreshTokenRepository.save(any(RefreshToken.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    RefreshTokenService.IssueResult result = service.issue(user);

    assertThat(result.plainToken()).isNotBlank();
    assertThat(result.refreshToken().getTokenHash()).isEqualTo(sha256(result.plainToken()));
    assertThat(result.refreshToken().getUser()).isSameAs(user);
    assertThat(result.refreshToken().isRevoked()).isFalse();
    assertThat(result.refreshToken().getExpiresAt())
        .isAfter(Instant.now().plus(jwtProperties.getRefreshTokenTtlDays() - 1, ChronoUnit.DAYS));
    verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
  }

  @Test
  void rotate_invalidTokenHashThrows() {
    when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.rotate("not-a-token"))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void rotate_revokedTokenTriggersReuseDetectionChainRevoke() {
    AppUser user = buildUser();
    RefreshToken revoked = buildToken(user, "hash123", true, false);
    when(refreshTokenRepository.findByTokenHash("hash123")).thenReturn(Optional.of(revoked));

    assertThatThrownBy(() -> service.rotate("plain"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("reuse");
    verify(refreshTokenRepository, times(1)).save(revoked);
  }

  @Test
  void rotate_expiredTokenIsRevokedAndThrows() {
    AppUser user = buildUser();
    RefreshToken expired = buildToken(user, "hash456", false, true);
    when(refreshTokenRepository.findByTokenHash("hash456")).thenReturn(Optional.of(expired));

    assertThatThrownBy(() -> service.rotate("plain"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("expired");
    assertThat(expired.isRevoked()).isTrue();
    verify(refreshTokenRepository, times(1)).save(expired);
  }

  @Test
  void rotate_validTokenIssuesNewAndRevokesOld() {
    AppUser user = buildUser();
    RefreshToken valid = buildToken(user, "hash789", false, false);
    when(refreshTokenRepository.findByTokenHash("hash789")).thenReturn(Optional.of(valid));
    when(refreshTokenRepository.save(any(RefreshToken.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    RefreshTokenService.RotationResult result = service.rotate("plain");

    assertThat(valid.isRevoked()).isTrue();
    assertThat(result.newRefreshToken().getReplacedBy()).isNull();
    assertThat(result.newRefreshToken().isRevoked()).isFalse();
    assertThat(result.newRefreshToken().getTokenHash()).isNotEqualTo("hash789");
    verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
  }

  @Test
  void revoke_marksTokenAsRevokedWhenFound() {
    AppUser user = buildUser();
    RefreshToken token = buildToken(user, "hash000", false, false);
    when(refreshTokenRepository.findByTokenHash("hash000")).thenReturn(Optional.of(token));

    service.revoke("plain");

    assertThat(token.isRevoked()).isTrue();
    verify(refreshTokenRepository, times(1)).save(token);
  }

  @Test
  void revoke_doesNothingWhenTokenNotFound() {
    when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

    service.revoke("plain");

    verify(refreshTokenRepository, never()).save(any());
  }

  private static AppUser buildUser() {
    AppUser user = new AppUser();
    user.setEmailHash("userHash");
    user.setActive(true);
    return user;
  }

  private static RefreshToken buildToken(AppUser user, String hash, boolean revoked, boolean expired) {
    RefreshToken token = new RefreshToken();
    token.setUser(user);
    token.setTokenHash(hash);
    token.setRevoked(revoked);
    token.setExpiresAt(expired ? Instant.now().minusSeconds(60) : Instant.now().plusSeconds(3600));
    return token;
  }

  private static String sha256(String input) {
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256")
              .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
