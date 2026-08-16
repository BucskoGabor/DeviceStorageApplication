package hu.tanszek.device.user;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import hu.tanszek.device.auth.entity.Role;
import hu.tanszek.device.auth.repository.RefreshTokenRepository;
import hu.tanszek.device.auth.repository.RoleRepository;
import hu.tanszek.device.common.BusinessValidationException;
import hu.tanszek.device.common.ResourceNotFoundException;
import hu.tanszek.device.location.entity.Location;
import hu.tanszek.device.location.repository.LocationRepository;
import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tesztek a {@link UserService#update(Long, String, Long, boolean, Boolean)} metódushoz.
 *
 * <p>Teszteli:
 *
 * <ul>
 *   <li>Partial update — csak a megadott mezők frissülnek
 *   <li>Role lookup — ismeretlen role esetén kivétel
 *   <li>Office location hozzárendelés / törlés
 *   <li>Active flag deaktiváláskor refresh token revoke
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UserServiceUpdateTest {

  @Mock private AppUserRepository appUserRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private RoleRepository roleRepository;
  @Mock private LocationRepository locationRepository;
  @Mock private hu.tanszek.device.auth.repository.PermissionRepository permissionRepository;

  @Mock
  private hu.tanszek.device.assignment.repository.DeviceAssignmentRepository assignmentRepository;

  @Mock private Argon2PasswordEncoder passwordEncoder;

  @InjectMocks private UserService userService;

  private AppUser user;
  private Role teacherRole;

  @BeforeEach
  void setUp() {
    teacherRole = Role.builder().id(2L).name("ROLE_TEACHER").build();
    user = AppUser.builder().id(1L).emailHash("hash-xyz").active(true).role(teacherRole).build();
  }

  @Test
  void update_changesRoleWithLookup() {
    Role adminRole = Role.builder().id(1L).name("ROLE_ADMIN").build();
    when(appUserRepository.findWithDetailsById(1L)).thenReturn(Optional.of(user));
    when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
    when(appUserRepository.save(user)).thenReturn(user);

    AppUser result = userService.update(1L, "ROLE_ADMIN", null, false, null, null);

    assertThat(result.getRole().getName()).isEqualTo("ROLE_ADMIN");
  }

  @Test
  void update_rejectsUnknownRole() {
    when(appUserRepository.findWithDetailsById(1L)).thenReturn(Optional.of(user));
    when(roleRepository.findByName("ROLE_INVALID")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.update(1L, "ROLE_INVALID", null, false, null, null))
        .isInstanceOf(BusinessValidationException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessValidationException) ex).getMessageKey())
                    .isEqualTo("invalidRole"));

    verify(appUserRepository, never()).save(user);
  }

  @Test
  void update_setsOfficeLocation() {
    Location office = Location.builder().id(5L).name("Iroda").version(0L).build();
    when(appUserRepository.findWithDetailsById(1L)).thenReturn(Optional.of(user));
    when(locationRepository.findById(5L)).thenReturn(Optional.of(office));
    when(appUserRepository.save(user)).thenReturn(user);

    AppUser result = userService.update(1L, null, 5L, false, null, null);

    assertThat(result.getOfficeLocation()).isSameAs(office);
  }

  @Test
  void update_clearsOfficeLocation() {
    Location office = Location.builder().id(5L).name("Iroda").version(0L).build();
    user.setOfficeLocation(office);
    when(appUserRepository.findWithDetailsById(1L)).thenReturn(Optional.of(user));
    when(appUserRepository.save(user)).thenReturn(user);

    AppUser result = userService.update(1L, null, null, true, null, null);

    assertThat(result.getOfficeLocation()).isNull();
  }

  @Test
  void update_deactivateRevokesRefreshTokens() {
    when(appUserRepository.findWithDetailsById(1L)).thenReturn(Optional.of(user));
    when(appUserRepository.save(user)).thenReturn(user);

    AppUser result = userService.update(1L, null, null, false, false, null);

    assertThat(result.isActive()).isFalse();
    verify(refreshTokenRepository, times(1)).revokeAllRefreshTokensByUserId(1L);
  }

  @Test
  void update_reactivateDoesNotRevokeTokens() {
    user.setActive(false);
    when(appUserRepository.findWithDetailsById(1L)).thenReturn(Optional.of(user));
    when(appUserRepository.save(user)).thenReturn(user);

    AppUser result = userService.update(1L, null, null, false, true, null);

    assertThat(result.isActive()).isTrue();
    verify(refreshTokenRepository, never()).revokeAllRefreshTokensByUserId(any());
  }

  @Test
  void update_sameActiveFlagDoesNotRevoke() {
    when(appUserRepository.findWithDetailsById(1L)).thenReturn(Optional.of(user));
    when(appUserRepository.save(user)).thenReturn(user);

    AppUser result = userService.update(1L, null, null, false, true, null);

    assertThat(result.isActive()).isTrue();
    verify(refreshTokenRepository, never()).revokeAllRefreshTokensByUserId(any());
  }

  @Test
  void update_throwsWhenUserNotFound() {
    when(appUserRepository.findWithDetailsById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.update(99L, null, null, false, null, null))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void update_allFieldsNullIsNoOp() {
    when(appUserRepository.findWithDetailsById(1L)).thenReturn(Optional.of(user));
    when(appUserRepository.save(user)).thenReturn(user);

    AppUser result = userService.update(1L, null, null, false, null, null);

    assertThat(result.isActive()).isTrue();
    assertThat(result.getRole()).isSameAs(teacherRole);
    verify(refreshTokenRepository, never()).revokeAllRefreshTokensByUserId(any());
  }

  @Test
  void update_officeLocationThrowsWhenLocationNotFound() {
    when(appUserRepository.findWithDetailsById(1L)).thenReturn(Optional.of(user));
    when(locationRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.update(1L, null, 99L, false, null, null))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
