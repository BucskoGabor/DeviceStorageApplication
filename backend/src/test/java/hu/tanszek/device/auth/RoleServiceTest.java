package hu.tanszek.device.auth;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import hu.tanszek.device.auth.entity.Permission;
import hu.tanszek.device.auth.entity.Role;
import hu.tanszek.device.auth.repository.PermissionRepository;
import hu.tanszek.device.auth.repository.RoleRepository;
import hu.tanszek.device.common.BusinessValidationException;
import hu.tanszek.device.common.ResourceNotFoundException;
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
 * Unit tesztek a {@link RoleService}-hez.
 *
 * <p>A repository-k mockolva vannak, az entitások builderrel épülnek.
 */
class RoleServiceTest {

  private final RoleRepository roleRepository = mock(RoleRepository.class);
  private final PermissionRepository permissionRepository = mock(PermissionRepository.class);
  private final AppUserRepository userRepository = mock(AppUserRepository.class);
  private final RoleService service = new RoleService(roleRepository, permissionRepository, userRepository);

  @Test
  void findAll_delegatesToRepository() {
    Role role = new Role();
    role.setName("ROLE_ADMIN");
    when(roleRepository.findAllWithPermissions()).thenReturn(List.of(role));

    List<Role> result = service.findAll();

    assertThat(result).containsExactly(role);
  }

  @Test
  void findById_returnsRole() {
    Role role = new Role();
    role.setName("ROLE_ADMIN");
    when(roleRepository.findByIdWithPermissions(1L)).thenReturn(Optional.of(role));

    assertThat(service.findById(1L)).isSameAs(role);
  }

  @Test
  void findById_throwsWhenNotFound() {
    when(roleRepository.findByIdWithPermissions(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findById(99L)).isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void create_validatesBlankName() {
    assertThatThrownBy(() -> service.create("  ", null))
        .isInstanceOf(BusinessValidationException.class)
        .hasMessageContaining("empty");
  }

  @Test
  void create_prependsRolePrefixWhenMissing() {
    when(roleRepository.findByName("ROLE_EDITOR")).thenReturn(Optional.empty());
    when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

    Role role = service.create("editor", null);

    assertThat(role.getName()).isEqualTo("ROLE_EDITOR");
    assertThat(role.getPermissions()).isEmpty();
  }

  @Test
  void create_keepsExistingPrefix() {
    when(roleRepository.findByName("ROLE_VIEWER")).thenReturn(Optional.empty());
    when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

    Role role = service.create("ROLE_VIEWER", null);

    assertThat(role.getName()).isEqualTo("ROLE_VIEWER");
  }

  @Test
  void create_throwsWhenRoleAlreadyExists() {
    when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.of(new Role()));

    assertThatThrownBy(() -> service.create("admin", null))
        .isInstanceOf(BusinessValidationException.class)
        .hasMessageContaining("exists");
  }

  @Test
  void create_attachesPermissionsById() {
    Permission perm = new Permission();
    perm.setName("DEVICE_READ");
    when(roleRepository.findByName("ROLE_VIEWER")).thenReturn(Optional.empty());
    when(permissionRepository.findAllById(Set.of(10L))).thenReturn(List.of(perm));
    when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

    Role role = service.create("viewer", Set.of(10L));

    assertThat(role.getPermissions()).containsExactly(perm);
  }

  @Test
  void update_throwsWhenRoleNotFound() {
    when(roleRepository.findByIdWithPermissions(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update(99L, "new", null))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void update_allowsChangingNameOfNonSystemRole() {
    Role existing = buildRole(1L, "ROLE_OLD");
    when(roleRepository.findByIdWithPermissions(1L)).thenReturn(Optional.of(existing));
    when(roleRepository.findByName("ROLE_NEW")).thenReturn(Optional.empty());
    when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

    Role updated = service.update(1L, "new", null);

    assertThat(updated.getName()).isEqualTo("ROLE_NEW");
  }

  @Test
  void update_rejectsRenamingSystemRole() {
    Role admin = buildRole(1L, "ROLE_ADMIN");
    when(roleRepository.findByIdWithPermissions(1L)).thenReturn(Optional.of(admin));

    assertThatThrownBy(() -> service.update(1L, "SUPERADMIN", null))
        .isInstanceOf(BusinessValidationException.class)
        .hasMessageContaining("system");
  }

  @Test
  void update_rejectsDuplicateName() {
    Role existing = buildRole(1L, "ROLE_OLD");
    Role conflict = buildRole(2L, "ROLE_NEW");
    when(roleRepository.findByIdWithPermissions(1L)).thenReturn(Optional.of(existing));
    when(roleRepository.findByName("ROLE_NEW")).thenReturn(Optional.of(conflict));

    assertThatThrownBy(() -> service.update(1L, "new", null))
        .isInstanceOf(BusinessValidationException.class)
        .hasMessageContaining("exists");
  }

  @Test
  void update_replacesPermissionsWhenProvided() {
    Role existing = buildRole(1L, "ROLE_OLD");
    existing.getPermissions().add(new Permission());
    when(roleRepository.findByIdWithPermissions(1L)).thenReturn(Optional.of(existing));
    when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

    service.update(1L, null, new HashSet<>());

    assertThat(existing.getPermissions()).isEmpty();
  }

  @Test
  void delete_throwsOnSystemRole() {
    Role admin = buildRole(1L, "ROLE_ADMIN");
    when(roleRepository.findByIdWithPermissions(1L)).thenReturn(Optional.of(admin));

    assertThatThrownBy(() -> service.delete(1L))
        .isInstanceOf(BusinessValidationException.class)
        .hasMessageContaining("system");
    verify(roleRepository, never()).delete(any());
  }

  @Test
  void delete_throwsWhenRoleInUse() {
    Role custom = buildRole(2L, "ROLE_CUSTOM");
    when(roleRepository.findByIdWithPermissions(2L)).thenReturn(Optional.of(custom));
    when(userRepository.existsByRoleId(2L)).thenReturn(true);

    assertThatThrownBy(() -> service.delete(2L))
        .isInstanceOf(BusinessValidationException.class)
        .hasMessageContaining("assigned");
    verify(roleRepository, never()).delete(any());
  }

  @Test
  void delete_removesUnusedRole() {
    Role custom = buildRole(2L, "ROLE_CUSTOM");
    when(roleRepository.findByIdWithPermissions(2L)).thenReturn(Optional.of(custom));
    when(userRepository.existsByRoleId(2L)).thenReturn(false);

    service.delete(2L);

    verify(roleRepository, times(1)).delete(custom);
  }

  private static Role buildRole(Long id, String name) {
    Role role = new Role();
    role.setId(id);
    role.setName(name);
    role.setPermissions(new HashSet<>());
    return role;
  }
}
