package hu.tanszek.device.user.controller;

import hu.tanszek.device.auth.RequirePermission;
import hu.tanszek.device.common.ResourceNotFoundException;
import hu.tanszek.device.crypto.CryptoService;
import hu.tanszek.device.user.UserService;
import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
public class UserController {

    private static final int MAX_PAGE_SIZE = 50;

    private final AppUserRepository userRepository;
    private final UserService userService;
    private final CryptoService cryptoService;

    @GetMapping
    @RequirePermission("USER_READ")
    public ResponseEntity<Map<String, Object>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (size > MAX_PAGE_SIZE) size = MAX_PAGE_SIZE;
        if (size < 1) size = 1;
        if (page < 0) page = 0;

        var pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        Page<AppUser> result = userRepository.findAll(pageable);

        return ResponseEntity.ok(Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "size", result.getSize(),
                "number", result.getNumber()
        ));
    }

    @GetMapping("/{id}")
    @RequirePermission("USER_READ")
    public ResponseEntity<AppUser> findById(@PathVariable Long id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        return ResponseEntity.ok(user);
    }

    @PostMapping
    @RequirePermission("USER_MANAGE")
    public ResponseEntity<AppUser> create(@Valid @RequestBody CreateUserRequest request) {
        String emailHash = cryptoService.sha256(request.email());

        userRepository.findByEmailHash(emailHash).ifPresent(u -> {
            throw new hu.tanszek.device.common.BusinessValidationException(
                    "userEmailDuplicate",
                    "Email already in use: " + request.email()
            );
        });

        // A jelszó hash placeholder — production-ban az Argon2PasswordEncoder generálja
        AppUser user = AppUser.builder()
                .emailEncrypted(cryptoService.encrypt(request.email()))
                .emailHash(emailHash)
                .role(null) // TODO: role lookup from request
                .passwordHash("$argon2id$PLACEHOLDER")
                .active(request.active() == null || request.active())
                .mustChangePassword(true)
                .failedLoginCount(0)
                .passwordChangedAt(java.time.Instant.now())
                .build();

        AppUser saved = userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    @RequirePermission("USER_MANAGE")
    public ResponseEntity<AppUser> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        if (request.active() != null) user.setActive(request.active());

        AppUser saved = userRepository.save(user);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    @RequirePermission("USER_MANAGE")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found: " + id);
        }
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/unlock")
    @RequirePermission("USER_MANAGE")
    public ResponseEntity<Void> unlockAccount(@PathVariable Long id) {
        userService.reactivate(id);
        return ResponseEntity.noContent().build();
    }

    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank String role,
            Boolean active
    ) {}

    public record UpdateUserRequest(
            Boolean active
    ) {}
}
