package hu.tanszek.device.auth;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hu.tanszek.device.audit.AuditTarget;
import hu.tanszek.device.auth.entity.Permission;
import hu.tanszek.device.auth.entity.Role;
import hu.tanszek.device.auth.repository.PermissionRepository;
import hu.tanszek.device.auth.repository.RoleRepository;
import hu.tanszek.device.common.BusinessValidationException;
import hu.tanszek.device.common.ResourceNotFoundException;
import hu.tanszek.device.user.repository.AppUserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * RoleService — Role management business logic & permission assignment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

  private static final Set<String> SYSTEM_ROLES =
      Set.of("ROLE_ADMIN", "ROLE_TEACHER", "ROLE_STUDENT");

  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;
  private final AppUserRepository userRepository;

  @Transactional(readOnly = true)
  public List<Role> findAll() {
    return roleRepository.findAllWithPermissions();
  }

  @Transactional(readOnly = true)
  public Role findById(Long id) {
    return roleRepository
        .findByIdWithPermissions(id)
        .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + id));
  }

  @Transactional
  @AuditTarget(entityType = "Role", action = "create_role")
  public Role create(String name, Set<Long> permissionIds) {
    if (name == null || name.isBlank()) {
      throw new BusinessValidationException("invalidRoleName", "Role name cannot be empty");
    }

    String formattedName = name.trim();
    if (!formattedName.startsWith("ROLE_")) {
      formattedName = "ROLE_" + formattedName.toUpperCase();
    }

    if (roleRepository.findByName(formattedName).isPresent()) {
      throw new BusinessValidationException("roleAlreadyExists", "Role already exists: " + formattedName);
    }

    Set<Permission> permissions = new HashSet<>();
    if (permissionIds != null && !permissionIds.isEmpty()) {
      permissions.addAll(permissionRepository.findAllById(permissionIds));
    }

    Role role = Role.builder().name(formattedName).permissions(permissions).build();

    Role saved = roleRepository.save(role);
    log.info("Created new role: {} (id={}) with {} permissions", saved.getName(), saved.getId(), permissions.size());
    return saved;
  }

  @Transactional
  @AuditTarget(entityType = "Role", action = "update_role")
  public Role update(Long id, String name, Set<Long> permissionIds) {
    Role role = findById(id);

    if (name != null && !name.isBlank()) {
      String formattedName = name.trim();
      if (!formattedName.startsWith("ROLE_")) {
        formattedName = "ROLE_" + formattedName.toUpperCase();
      }

      if (!formattedName.equals(role.getName())) {
        if (SYSTEM_ROLES.contains(role.getName())) {
          throw new BusinessValidationException("systemRoleNameCannotBeChanged", "System role name cannot be changed");
        }
        if (roleRepository.findByName(formattedName).isPresent()) {
          throw new BusinessValidationException("roleAlreadyExists", "Role already exists: " + formattedName);
        }
        role.setName(formattedName);
      }
    }

    if (permissionIds != null) {
      Set<Permission> permissions = new HashSet<>();
      if (!permissionIds.isEmpty()) {
        permissions.addAll(permissionRepository.findAllById(permissionIds));
      }
      role.getPermissions().clear();
      role.getPermissions().addAll(permissions);
    }

    Role saved = roleRepository.save(role);
    log.info("Updated role: {} (id={}) with {} permissions", saved.getName(), saved.getId(), saved.getPermissions().size());
    return saved;
  }

  @Transactional
  @AuditTarget(entityType = "Role", action = "delete_role")
  public void delete(Long id) {
    Role role = findById(id);

    if (SYSTEM_ROLES.contains(role.getName())) {
      throw new BusinessValidationException("systemRoleCannotBeDeleted", "System roles cannot be deleted");
    }

    if (userRepository.existsByRoleId(id)) {
      throw new BusinessValidationException("roleInUseCannotBeDeleted", "Role is currently assigned to users");
    }

    roleRepository.delete(role);
    log.info("Deleted role: {} (id={})", role.getName(), id);
  }
}
