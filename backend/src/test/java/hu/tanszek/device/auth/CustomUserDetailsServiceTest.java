package hu.tanszek.device.auth;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import hu.tanszek.device.auth.entity.Permission;
import hu.tanszek.device.auth.entity.Role;
import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tesztek a {@link CustomUserDetailsService}-hez.
 *
 * <p>Az AppUserRepository mockolva van, az AppUser/Role/Permission entitások builderrel épülnek.
 */
class CustomUserDetailsServiceTest {

  private final AppUserRepository userRepository = mock(AppUserRepository.class);
  private final CustomUserDetailsService service = new CustomUserDetailsService(userRepository);

  @Test
  void loadUserByUsername_returnsUserDetailsWithRoleAndPermissions() {
    AppUser user = buildUser("hash1", "ROLE_ADMIN", true, null, new HashSet<>());
    when(userRepository.findByEmailHash("hash1")).thenReturn(Optional.of(user));

    UserDetails details = service.loadUserByUsername("hash1");

    assertThat(details.getUsername()).isEqualTo("hash1");
    assertThat(details.getPassword()).isEqualTo("argon2hash");
    assertThat(details.isEnabled()).isTrue();
    assertThat(details.isAccountNonLocked()).isTrue();
    assertThat(details.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ROLE_ADMIN");
  }

  @Test
  void loadUserByUsername_disabledUserIsMarkedDisabled() {
    AppUser user = buildUser("hash2", "ROLE_TEACHER", false, null, new HashSet<>());
    when(userRepository.findByEmailHash("hash2")).thenReturn(Optional.of(user));

    UserDetails details = service.loadUserByUsername("hash2");

    assertThat(details.isEnabled()).isFalse();
    assertThat(details.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .contains("ROLE_TEACHER");
  }

  @Test
  void loadUserByUsername_lockedUntilFutureIsMarkedLocked() {
    Instant futureLock = Instant.now().plusSeconds(3600);
    AppUser user = buildUser("hash3", "ROLE_STUDENT", true, futureLock, new HashSet<>());
    when(userRepository.findByEmailHash("hash3")).thenReturn(Optional.of(user));

    UserDetails details = service.loadUserByUsername("hash3");

    assertThat(details.isAccountNonLocked()).isFalse();
  }

  @Test
  void loadUserByUsername_lapsedLockIsNotLocked() {
    Instant pastLock = Instant.now().minusSeconds(3600);
    AppUser user = buildUser("hash4", "ROLE_STUDENT", true, pastLock, new HashSet<>());
    when(userRepository.findByEmailHash("hash4")).thenReturn(Optional.of(user));

    UserDetails details = service.loadUserByUsername("hash4");

    assertThat(details.isAccountNonLocked()).isTrue();
  }

  @Test
  void loadUserByUsername_combinesRolePermissionsAndUserPermissions() {
    Permission rolePerm = perm("DEVICE_READ");
    Permission userPerm = perm("AUDIT_READ");
    Set<Permission> userPermissions = new HashSet<>();
    userPermissions.add(userPerm);
    AppUser user = buildUser("hash5", "ROLE_TEACHER", true, null, userPermissions);
    user.getRole().getPermissions().add(rolePerm);
    when(userRepository.findByEmailHash("hash5")).thenReturn(Optional.of(user));

    UserDetails details = service.loadUserByUsername("hash5");

    assertThat(details.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ROLE_TEACHER", "DEVICE_READ", "AUDIT_READ");
  }

  @Test
  void loadUserByUsername_throwsWhenUserNotFound() {
    when(userRepository.findByEmailHash("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.loadUserByUsername("missing"))
        .isInstanceOf(UsernameNotFoundException.class)
        .hasMessageContaining("missing");
  }

  private static AppUser buildUser(
      String emailHash,
      String roleName,
      boolean active,
      Instant lockedUntil,
      Set<Permission> userPerms) {
    Role role = new Role();
    role.setName(roleName);
    role.setPermissions(new HashSet<>());
    AppUser user = new AppUser();
    user.setEmailHash(emailHash);
    user.setPasswordHash("argon2hash");
    user.setActive(active);
    user.setLockedUntil(lockedUntil);
    user.setRole(role);
    user.setPermissions(userPerms);
    return user;
  }

  private static Permission perm(String name) {
    Permission p = new Permission();
    p.setName(name);
    return p;
  }
}
