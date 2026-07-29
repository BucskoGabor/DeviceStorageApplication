package hu.tanszek.device.auth;

import hu.tanszek.device.auth.dto.PasswordChangeRequest;
import hu.tanszek.device.common.UnauthorizedActionException;
import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AuthController — authentikációs és password change endpoint-ok.
 *
 * <p>TODO Task 2.3: a /api/auth/login és /api/auth/refresh endpoint-ok
 * implementálása (JWT token generálás, refresh token cookie beállítás).
 * Most csak a /api/auth/password-change van kész, mert az Task 2.2-höz tartozik.
 *
 * @see LoginService
 * @see UserService
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AppUserRepository appUserRepository;

    /**
     * Password change endpoint.
     *
     * <p>A user a currentPassword megadásával cserélheti a jelszavát.
     * Sikeres végrehajtás után a must_change_password flag törlődik,
     * és minden aktív refresh token revokeolódik (security).
     *
     * <p>Authentikáció: a SecurityContext-ből vesszük a currentUser-t
     * (az email_hash az Authentication principal). A userId-t az
     * AppUserRepository-ból query-zzük.
     *
     * @param authentication a Spring Security context (injected)
     * @param request a password change request DTO (validated)
     * @return 204 No Content sikeres esetben
     */
    @PostMapping("/password-change")
    public ResponseEntity<Void> changePassword(
            Authentication authentication,
            @Valid @RequestBody PasswordChangeRequest request
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedActionException("authRequired", "Authentication required");
        }

        // Principal = email_hash (LocalAuthProvider beállítja UsernamePasswordAuthenticationToken-ban)
        String emailHash = (String) authentication.getPrincipal();
        AppUser user = appUserRepository.findByEmailHash(emailHash)
                .orElseThrow(() -> new UnauthorizedActionException("userNotFound", "User not found"));

        userService.changePassword(user.getId(), request.currentPassword(), request.newPassword());

        log.info("Password changed for user {}", user.getId());
        return ResponseEntity.noContent().build();
    }
}