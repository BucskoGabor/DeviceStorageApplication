package hu.tanszek.device.auth.controller;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import hu.tanszek.device.auth.RequirePermission;
import hu.tanszek.device.auth.RoleService;
import hu.tanszek.device.auth.entity.Role;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * RoleController — REST endpointok a Role entitás CRUD műveleteihez.
 */
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@Tag(name = "Role", description = "Szerepkörök és hozzájuk tartozó jogosultságok kezelése")
public class RoleController {

  private final RoleService roleService;

  @Operation(summary = "Szerepkörök listája")
  @GetMapping
  @RequirePermission({"ROLE_READ", "ROLE_MANAGE"})
  public ResponseEntity<List<Role>> findAll() {
    return ResponseEntity.ok(roleService.findAll());
  }

  @Operation(summary = "Szerepkör részletei")
  @GetMapping("/{id}")
  @RequirePermission({"ROLE_READ", "ROLE_MANAGE"})
  public ResponseEntity<Role> findById(@PathVariable Long id) {
    return ResponseEntity.ok(roleService.findById(id));
  }

  @Operation(summary = "Új szerepkör létrehozása")
  @PostMapping
  @RequirePermission("ROLE_MANAGE")
  public ResponseEntity<Role> create(@Valid @RequestBody CreateRoleRequest request) {
    Role created = roleService.create(request.name(), request.permissionIds());
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @Operation(summary = "Szerepkör módosítása")
  @PutMapping("/{id}")
  @RequirePermission("ROLE_MANAGE")
  public ResponseEntity<Role> update(
      @PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
    Role updated = roleService.update(id, request.name(), request.permissionIds());
    return ResponseEntity.ok(updated);
  }

  @Operation(summary = "Szerepkör törlése")
  @DeleteMapping("/{id}")
  @RequirePermission("ROLE_MANAGE")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    roleService.delete(id);
    return ResponseEntity.noContent().build();
  }

  public record CreateRoleRequest(
      @NotBlank @Size(max = 50) String name, Set<Long> permissionIds) {}

  public record UpdateRoleRequest(@Size(max = 50) String name, Set<Long> permissionIds) {}
}
