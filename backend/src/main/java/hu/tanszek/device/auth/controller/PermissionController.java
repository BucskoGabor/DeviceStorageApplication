package hu.tanszek.device.auth.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import hu.tanszek.device.auth.RequirePermission;
import hu.tanszek.device.auth.entity.Permission;
import hu.tanszek.device.auth.repository.PermissionRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** PermissionController — Rendszerben elérhető jogosultságok listája. */
@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
@Tag(name = "Permission", description = "Rendszerbeli granularis jogosultságok")
public class PermissionController {

  private final PermissionRepository permissionRepository;

  @Operation(summary = "Jogosultságok listája")
  @GetMapping
  @RequirePermission({"ROLE_READ", "ROLE_MANAGE", "USER_READ", "USER_CREATE", "USER_UPDATE"})
  public ResponseEntity<List<Permission>> findAll() {
    return ResponseEntity.ok(permissionRepository.findAll());
  }
}
