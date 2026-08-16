package hu.tanszek.device.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import hu.tanszek.device.auth.entity.Permission;
import hu.tanszek.device.auth.entity.Role;
import hu.tanszek.device.config.repository.ConfigRepository;
import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalAuthProviderTest {

  @Mock private AppUserRepository appUserRepository;
  @Mock private ConfigRepository configRepository;

  @Spy
  private Argon2PasswordEncoder passwordEncoder = new Argon2PasswordEncoder(16, 32, 1, 65536, 3);

  @InjectMocks private LocalAuthProvider localAuthProvider;

  private AppUser user;
  private String rawPassword = "ValidPassword123!";
  private String encodedPassword;

  @BeforeEach
  void setUp() {
    encodedPassword = passwordEncoder.encode(rawPassword);
    Role role =
        Role.builder()
            .id(1L)
            .name("ROLE_ADMIN")
            .permissions(Set.of(Permission.builder().name("DEVICE_READ").build()))
            .build();
    user =
        AppUser.builder()
            .id(1L)
            .emailHash(
                "6b86b273ff34fce19d6b804eff5a3f5747ada4eaa22f1d49c01e52ddb7875b4b") // sha256 for
            // "1"
            .emailEncrypted("encEmail")
            .passwordHash(encodedPassword)
            .active(true)
            .role(role)
            .failedLoginCount(0)
            .build();
  }

  @Test
  void authenticate_success() {
    user.setFailedLoginCount(2);
    when(appUserRepository.findByEmailHash(any())).thenReturn(Optional.of(user));

    Authentication auth = localAuthProvider.authenticate("test@tanszek.local", rawPassword);

    assertThat(auth).isNotNull();
    assertThat(auth.isAuthenticated()).isTrue();
    assertThat(user.getFailedLoginCount()).isEqualTo(0);
    verify(appUserRepository).save(user);
  }

  @Test
  void authenticate_throwsBadCredentialsWhenUserNotFound() {
    when(appUserRepository.findByEmailHash(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> localAuthProvider.authenticate("unknown@tanszek.local", "secret"))
        .isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void authenticate_throwsDisabledWhenUserNotActive() {
    user.setActive(false);
    when(appUserRepository.findByEmailHash(any())).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> localAuthProvider.authenticate("test@tanszek.local", rawPassword))
        .isInstanceOf(DisabledException.class);
  }

  @Test
  void authenticate_throwsLockedWhenLockedUntilInFuture() {
    user.setLockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES));
    when(appUserRepository.findByEmailHash(any())).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> localAuthProvider.authenticate("test@tanszek.local", rawPassword))
        .isInstanceOf(LockedException.class);
  }

  @Test
  void authenticate_throwsBadCredentialsAndIncrementsFailedLoginCountOnWrongPassword() {
    when(appUserRepository.findByEmailHash(any())).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> localAuthProvider.authenticate("test@tanszek.local", "WrongPassword!"))
        .isInstanceOf(BadCredentialsException.class);

    assertThat(user.getFailedLoginCount()).isEqualTo(1);
    verify(appUserRepository).save(user);
  }

  @Test
  void authenticate_locksAccountAfterMaxFailedAttempts() {
    user.setFailedLoginCount(4);
    when(appUserRepository.findByEmailHash(any())).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> localAuthProvider.authenticate("test@tanszek.local", "WrongPassword!"))
        .isInstanceOf(BadCredentialsException.class);

    assertThat(user.getFailedLoginCount()).isEqualTo(5);
    assertThat(user.getLockedUntil()).isNotNull();
    verify(appUserRepository).save(user);
  }
}
