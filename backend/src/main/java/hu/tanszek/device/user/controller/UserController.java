package hu.tanszek.device.user.controller;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import hu.tanszek.device.auth.RequirePermission;
import hu.tanszek.device.auth.entity.Role;
import hu.tanszek.device.auth.repository.PermissionRepository;
import hu.tanszek.device.auth.repository.RoleRepository;
import hu.tanszek.device.common.BusinessValidationException;
import hu.tanszek.device.common.ResourceNotFoundException;
import hu.tanszek.device.crypto.CryptoService;
import hu.tanszek.device.user.UserService;
import hu.tanszek.device.user.dto.UserResponseDto;
import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/** UserController — REST endpointok az AppUser entity CRUD-hoz. */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "Felhasználó CRUD, role- és profil-kezelés")
public class UserController {

  private static final int MAX_PAGE_SIZE = 50;

  private final AppUserRepository userRepository;
  private final UserService userService;
  private final CryptoService cryptoService;
  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;
  private final org.springframework.security.crypto.argon2.Argon2PasswordEncoder passwordEncoder;
  private final hu.tanszek.device.assignment.repository.DeviceAssignmentRepository assignmentRepository;

  @Operation(summary = "Felhasználó lista (lapozott)")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Sikeres lista"),
    @ApiResponse(responseCode = "403", description = "USER_READ vagy USER_MANAGE permission hiányzik")
  })
  @GetMapping
  @RequirePermission({"USER_READ", "USER_CREATE", "USER_UPDATE", "USER_DELETE"})
  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public ResponseEntity<Map<String, Object>> findAll(
      @Parameter(description = "Oldalszám (0-tól)") @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "Elemek száma (max 50)") @RequestParam(defaultValue = "20")
          int size) {
    if (size > MAX_PAGE_SIZE) {
      size = MAX_PAGE_SIZE;
    }
    if (size < 1) {
      size = 1;
    }
    if (page < 0) {
      page = 0;
    }

    var pageable = PageRequest.of(page, size, Sort.by("id").ascending());
    Page<AppUser> result = userRepository.findAll(pageable);

    List<UserResponseDto> content =
        result.getContent().stream()
            .map(u -> UserResponseDto.fromEntity(u, cryptoService))
            .toList();

    return ResponseEntity.ok(
        Map.of(
            "content", content,
            "totalElements", result.getTotalElements(),
            "totalPages", result.getTotalPages(),
            "size", result.getSize(),
            "number", result.getNumber()));
  }

  @Operation(summary = "Felhasználó részletek")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Sikeres lekérdezés"),
    @ApiResponse(responseCode = "404", description = "User nem található"),
    @ApiResponse(responseCode = "403", description = "USER_READ permission hiányzik")
  })
  @GetMapping("/{id}")
  @RequirePermission({"USER_READ", "USER_CREATE", "USER_UPDATE", "USER_DELETE"})
  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public ResponseEntity<UserResponseDto> findById(
      @Parameter(description = "User azonosító") @PathVariable Long id) {
    AppUser user =
        userRepository
            .findWithDetailsById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    return ResponseEntity.ok(UserResponseDto.fromEntity(user, cryptoService));
  }

  @Operation(summary = "Felhasználóhoz jelenleg hozzárendelt eszközök")
  @GetMapping("/{id}/devices")
  @RequirePermission({"USER_READ", "USER_CREATE", "USER_UPDATE", "USER_DELETE"})
  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public ResponseEntity<List<hu.tanszek.device.device.entity.Device>> findCurrentDevices(
      @PathVariable Long id) {
    userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    List<hu.tanszek.device.device.entity.Device> devices =
        assignmentRepository.findCurrentDevicesByUserId(id);
    return ResponseEntity.ok(devices);
  }

  @Operation(summary = "Felhasználó hozzárendelési előzményei")
  @GetMapping("/{id}/assignments")
  @RequirePermission({"USER_READ", "USER_CREATE", "USER_UPDATE", "USER_DELETE"})
  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public ResponseEntity<List<hu.tanszek.device.assignment.entity.DeviceAssignment>> findAssignmentHistory(
      @PathVariable Long id) {
    userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    List<hu.tanszek.device.assignment.entity.DeviceAssignment> history =
        assignmentRepository.findByToUserIdOrFromUserIdOrderByCreatedDateDesc(id, id);
    return ResponseEntity.ok(history);
  }

  @Operation(
      summary = "Új felhasználó létrehozása",
      description =
          "Placeholder jelszó hash-sel jön létre, "
              + "mustChangePassword=true flag-gel — a user első bejelentkezéskor köteles jelszót cserélni.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "User létrehozva"),
    @ApiResponse(responseCode = "400", description = "Validációs hiba vagy duplikált email"),
    @ApiResponse(responseCode = "403", description = "USER_CREATE permission hiányzik")
  })
  @PostMapping
  @RequirePermission("USER_CREATE")
  public ResponseEntity<AppUser> create(@Valid @RequestBody CreateUserRequest request) {
    String emailHash = cryptoService.sha256(request.email());

    userRepository
        .findByEmailHash(emailHash)
        .ifPresent(
            u -> {
              throw new BusinessValidationException(
                  "userEmailDuplicate", "Email already in use: " + request.email());
            });

    Role role =
        roleRepository
            .findByName(request.role())
            .orElseThrow(
                () ->
                    new BusinessValidationException(
                        "invalidRole", "Unknown role: " + request.role()));

    java.util.Set<hu.tanszek.device.auth.entity.Permission> directPerms = new java.util.HashSet<>();
    if (request.directPermissionIds() != null && !request.directPermissionIds().isEmpty()) {
      directPerms.addAll(permissionRepository.findAllById(request.directPermissionIds()));
    }

    String initialPassword =
        (request.initialPassword() != null && !request.initialPassword().isBlank())
            ? request.initialPassword()
            : "ChangeMe123!";

    AppUser user =
        AppUser.builder()
            .emailEncrypted(cryptoService.encrypt(request.email()))
            .emailHash(emailHash)
            .role(role)
            .permissions(directPerms)
            .passwordHash(passwordEncoder.encode(initialPassword))
            .active(request.active() == null || request.active())
            .mustChangePassword(true)
            .failedLoginCount(0)
            .passwordChangedAt(java.time.Instant.now())
            .build();

    AppUser saved = userRepository.save(user);
    return ResponseEntity.status(HttpStatus.CREATED).body(saved);
  }

  @Operation(
      summary = "Felhasználó módosítása",
      description =
          "Partial update — csak a nem-null mezők frissülnek.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "User módosítva"),
    @ApiResponse(responseCode = "400", description = "Validációs hiba vagy ismeretlen role"),
    @ApiResponse(responseCode = "404", description = "User vagy location nem található"),
    @ApiResponse(responseCode = "403", description = "USER_UPDATE permission hiányzik")
  })
  @PutMapping("/{id}")
  @RequirePermission("USER_UPDATE")
  public ResponseEntity<AppUser> update(
      @Parameter(description = "User azonosító") @PathVariable Long id,
      @Valid @RequestBody UpdateUserRequest request) {
    AppUser saved =
        userService.update(
            id,
            request.role(),
            request.officeLocationId(),
            Boolean.TRUE.equals(request.clearOfficeLocation()),
            request.active(),
            request.directPermissionIds());
    return ResponseEntity.ok(saved);
  }

  @Operation(summary = "Felhasználó törlése")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "User törölve"),
    @ApiResponse(responseCode = "404", description = "User nem található"),
    @ApiResponse(responseCode = "403", description = "USER_DELETE permission hiányzik")
  })
  @DeleteMapping("/{id}")
  @RequirePermission("USER_DELETE")
  public ResponseEntity<Void> delete(
      @Parameter(description = "User azonosító") @PathVariable Long id) {
    userService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @Operation(
      summary = "Fiók zárolás feloldása",
      description =
          "failedLoginCount = 0, lockedUntil = NULL.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Fiók feloldva"),
    @ApiResponse(responseCode = "404", description = "User nem található"),
    @ApiResponse(responseCode = "403", description = "USER_UPDATE permission hiányzik")
  })
  @PostMapping("/{id}/unlock")
  @RequirePermission("USER_UPDATE")
  public ResponseEntity<Void> unlockAccount(
      @Parameter(description = "User azonosító") @PathVariable Long id) {
    AppUser user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

    user.setFailedLoginCount(0);
    user.setLockedUntil(null);
    userRepository.save(user);
    return ResponseEntity.noContent().build();
  }

  public record CreateUserRequest(
      @NotBlank @Email @Size(max = 255) String email,
      @NotBlank String role,
      String initialPassword,
      Boolean active,
      java.util.Set<Long> directPermissionIds) {}

  public record UpdateUserRequest(
      String role,
      Long officeLocationId,
      Boolean clearOfficeLocation,
      Boolean active,
      java.util.Set<Long> directPermissionIds) {}
}
