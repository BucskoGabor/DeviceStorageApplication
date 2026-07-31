package hu.tanszek.device.user.controller;

import hu.tanszek.device.auth.RequirePermission;
import hu.tanszek.device.auth.repository.RoleRepository;
import hu.tanszek.device.auth.entity.Role;
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
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * UserController — REST endpointok az AppUser entity CRUD-hoz.
 */
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

    @Operation(summary = "Felhasználó lista (lapozott)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sikeres lista"),
            @ApiResponse(responseCode = "403", description = "USER_READ permission hiányzik")
    })
    @GetMapping
    @RequirePermission("USER_READ")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> findAll(
            @Parameter(description = "Oldalszám (0-tól)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Elemek száma (max 50)") @RequestParam(defaultValue = "20") int size
    ) {
        if (size > MAX_PAGE_SIZE) size = MAX_PAGE_SIZE;
        if (size < 1) size = 1;
        if (page < 0) page = 0;

        var pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        Page<AppUser> result = userRepository.findAll(pageable);

        List<UserResponseDto> content = result.getContent().stream()
                .map(u -> UserResponseDto.fromEntity(u, cryptoService))
                .toList();

        return ResponseEntity.ok(Map.of(
                "content", content,
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "size", result.getSize(),
                "number", result.getNumber()
        ));
    }

    @Operation(summary = "Felhasználó részletek")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sikeres lekérdezés"),
            @ApiResponse(responseCode = "404", description = "User nem található"),
            @ApiResponse(responseCode = "403", description = "USER_READ permission hiányzik")
    })
    @GetMapping("/{id}")
    @RequirePermission("USER_READ")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<UserResponseDto> findById(@Parameter(description = "User azonosító") @PathVariable Long id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        return ResponseEntity.ok(UserResponseDto.fromEntity(user, cryptoService));
    }

    @Operation(summary = "Új felhasználó létrehozása", description = "Placeholder jelszó hash-sel jön létre, " +
            "mustChangePassword=true flag-gel — a user első bejelentkezéskor köteles jelszót cserélni.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User létrehozva"),
            @ApiResponse(responseCode = "400", description = "Validációs hiba vagy duplikált email"),
            @ApiResponse(responseCode = "403", description = "USER_MANAGE permission hiányzik")
    })
    @PostMapping
    @RequirePermission("USER_MANAGE")
    public ResponseEntity<AppUser> create(@Valid @RequestBody CreateUserRequest request) {
        String emailHash = cryptoService.sha256(request.email());

        userRepository.findByEmailHash(emailHash).ifPresent(u -> {
            throw new BusinessValidationException(
                    "userEmailDuplicate",
                    "Email already in use: " + request.email()
            );
        });

        // Role lookup a roles táblából (pl. "ROLE_ADMIN" → role.id)
        Role role = roleRepository.findByName(request.role())
                .orElseThrow(() -> new BusinessValidationException(
                        "invalidRole",
                        "Unknown role: " + request.role()
                ));

        // A jelszó hash placeholder — production-ban az Argon2PasswordEncoder generálja
        AppUser user = AppUser.builder()
                .emailEncrypted(cryptoService.encrypt(request.email()))
                .emailHash(emailHash)
                .role(role)
                .passwordHash("$argon2id$PLACEHOLDER_FORCE_RESET")
                .active(request.active() == null || request.active())
                .mustChangePassword(true)
                .failedLoginCount(0)
                .passwordChangedAt(java.time.Instant.now())
                .build();

        AppUser saved = userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @Operation(summary = "Felhasználó módosítása", description = "Partial update — csak a nem-null mezők frissülnek. " +
            "A role név alapján lookup a roles táblában. Ha az active false-ra vált, a user " +
            "refresh tokenjei automatikusan revokeolódnak (session invalidáció).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User módosítva"),
            @ApiResponse(responseCode = "400", description = "Validációs hiba vagy ismeretlen role"),
            @ApiResponse(responseCode = "404", description = "User vagy location nem található"),
            @ApiResponse(responseCode = "403", description = "USER_MANAGE permission hiányzik")
    })
    @PutMapping("/{id}")
    @RequirePermission("USER_MANAGE")
    public ResponseEntity<AppUser> update(
            @Parameter(description = "User azonosító") @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        AppUser saved = userService.update(
                id,
                request.role(),
                request.officeLocationId(),
                Boolean.TRUE.equals(request.clearOfficeLocation()),
                request.active()
        );
        return ResponseEntity.ok(saved);
    }

    @Operation(summary = "Felhasználó törlése")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User törölve"),
            @ApiResponse(responseCode = "404", description = "User nem található"),
            @ApiResponse(responseCode = "403", description = "USER_MANAGE permission hiányzik")
    })
    @DeleteMapping("/{id}")
    @RequirePermission("USER_MANAGE")
    public ResponseEntity<Void> delete(@Parameter(description = "User azonosító") @PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found: " + id);
        }
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Fiók zárolás feloldása", description = "failedLoginCount = 0, lockedUntil = NULL. " +
            "USER_MANAGE permissionnel rendelkező admin hívhatja.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Fiók feloldva"),
            @ApiResponse(responseCode = "404", description = "User nem található"),
            @ApiResponse(responseCode = "403", description = "USER_MANAGE permission hiányzik")
    })
    @PostMapping("/{id}/unlock")
    @RequirePermission("USER_MANAGE")
    public ResponseEntity<Void> unlockAccount(@Parameter(description = "User azonosító") @PathVariable Long id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }

    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank String role,
            Boolean active
    ) {}

    public record UpdateUserRequest(
            String role,
            Long officeLocationId,
            Boolean clearOfficeLocation,
            Boolean active
    ) {}
}
