package hu.tanszek.device.auth;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import hu.tanszek.device.auth.entity.Role;
import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tesztek a {@link LoginService}-hez.
 *
 * <p>Az AuthProviderFactory, AuthProvider, AppUserRepository és Argon2PasswordEncoder mockolva van,
 * a LoginService belső rehash logikáját közvetlenül teszteljük.
 */
class LoginServiceTest {

  private final AuthProviderFactory providerFactory = mock(AuthProviderFactory.class);
  private final AuthProvider provider = mock(AuthProvider.class);
  private final AppUserRepository userRepository = mock(AppUserRepository.class);
  private final Argon2PasswordEncoder passwordEncoder =
      new Argon2PasswordEncoder(16, 32, 1, 65536, 3);
  private final LoginService service =
      new LoginService(providerFactory, userRepository, passwordEncoder);

  @Test
  void authenticate_delegatesToProviderAndReturnsAuthentication() {
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            "userHash", null, java.util.List.of(new SimpleGrantedAuthorityStub("ROLE_ADMIN")));
    when(providerFactory.getActiveProvider()).thenReturn(provider);
    when(provider.authenticate("user@example.com", "secret")).thenReturn(auth);

    Authentication result = service.authenticate("user@example.com", "secret");

    assertThat(result).isSameAs(auth);
    verify(provider, times(1)).authenticate("user@example.com", "secret");
  }

  @Test
  void authenticate_propagatesBadCredentialsException() {
    when(providerFactory.getActiveProvider()).thenReturn(provider);
    when(provider.authenticate(anyString(), anyString()))
        .thenThrow(new BadCredentialsException("bad"));

    assertThatThrownBy(() -> service.authenticate("user@example.com", "wrong"))
        .isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void authenticate_propagatesDisabledException() {
    when(providerFactory.getActiveProvider()).thenReturn(provider);
    when(provider.authenticate(anyString(), anyString()))
        .thenThrow(new DisabledException("disabled"));

    assertThatThrownBy(() -> service.authenticate("user@example.com", "pw"))
        .isInstanceOf(DisabledException.class);
  }

  @Test
  void authenticate_propagatesLockedException() {
    when(providerFactory.getActiveProvider()).thenReturn(provider);
    when(provider.authenticate(anyString(), anyString())).thenThrow(new LockedException("locked"));

    assertThatThrownBy(() -> service.authenticate("user@example.com", "pw"))
        .isInstanceOf(LockedException.class);
  }

  @Test
  void authenticate_performsRehashWhenEncoderSignalsUpgradeNeeded() {
    String weakHash = "$argon2id$v=19$m=4096,t=3,p=1$abc$def"; // 4096K < 65536K policy
    AppUser user = buildUser("userHash", weakHash);
    Authentication auth =
        new UsernamePasswordAuthenticationToken("userHash", null, java.util.List.of());
    when(providerFactory.getActiveProvider()).thenReturn(provider);
    when(provider.authenticate("user@example.com", "pw")).thenReturn(auth);
    when(userRepository.findByEmailHash("userHash")).thenReturn(Optional.of(user));

    service.authenticate("user@example.com", "pw");

    assertThat(user.getPasswordHash()).isNotEqualTo(weakHash);
    assertThat(user.getPasswordHash()).startsWith("$argon2id$");
    assertThat(user.getPasswordChangedAt()).isAfter(Instant.now().minusSeconds(60));
    assertThat(user.isMustChangePassword()).isFalse();
    assertThat(user.getFailedLoginCount()).isZero();
    assertThat(user.getLockedUntil()).isNull();
    verify(userRepository, times(1)).save(user);
  }

  @Test
  void authenticate_doesNotRehashWhenHashIsCurrent() {
    String currentHash = passwordEncoder.encode("pw");
    AppUser user = buildUser("userHash", currentHash);
    Authentication auth =
        new UsernamePasswordAuthenticationToken("userHash", null, java.util.List.of());
    when(providerFactory.getActiveProvider()).thenReturn(provider);
    when(provider.authenticate("user@example.com", "pw")).thenReturn(auth);
    when(userRepository.findByEmailHash("userHash")).thenReturn(Optional.of(user));

    service.authenticate("user@example.com", "pw");

    assertThat(user.getPasswordHash()).isEqualTo(currentHash);
    verify(userRepository, never()).save(any());
  }

  @Test
  void authenticate_throwsWhenUserDisappearsAfterAuthenticate() {
    String currentHash = passwordEncoder.encode("pw");
    Authentication auth =
        new UsernamePasswordAuthenticationToken("userHash", null, java.util.List.of());
    when(providerFactory.getActiveProvider()).thenReturn(provider);
    when(provider.authenticate("user@example.com", "pw")).thenReturn(auth);
    when(userRepository.findByEmailHash("userHash")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.authenticate("user@example.com", "pw"))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessageContaining("disappeared");
  }

  private static AppUser buildUser(String emailHash, String passwordHash) {
    Role role = new Role();
    role.setName("ROLE_ADMIN");
    role.setPermissions(new HashSet<>());
    AppUser user = new AppUser();
    user.setEmailHash(emailHash);
    user.setPasswordHash(passwordHash);
    user.setActive(true);
    user.setRole(role);
    user.setPermissions(new HashSet<>());
    return user;
  }

  /**
   * SimpleGrantedAuthority-t helyettesítő stub, hogy ne importáljuk a Spring Security-t mégegyszer.
   */
  private record SimpleGrantedAuthorityStub(String authority)
      implements org.springframework.security.core.GrantedAuthority {
    @Override
    public String getAuthority() {
      return authority;
    }
  }
}
