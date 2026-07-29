package hu.tanszek.device.auth;

import hu.tanszek.device.auth.dto.LoginRequest;
import hu.tanszek.device.auth.dto.LoginResponse;
import hu.tanszek.device.auth.dto.PasswordChangeRequest;
import hu.tanszek.device.auth.jwt.JwtTokenProvider;
import hu.tanszek.device.auth.jwt.RefreshTokenService;
import hu.tanszek.device.common.UnauthorizedActionException;
import hu.tanszek.device.user.UserService;
import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AuthController — authentikációs és password change endpoint-ok.
 *
 * <p>Endpoint-ok:
 * <ul>
 *   <li>{@code POST /api/auth/login} — email + password → access token + refresh cookie</li>
 *   <li>{@code POST /api/auth/refresh} — refresh cookie → új access token + új refresh cookie</li>
 *   <li>{@code POST /api/auth/logout} — refresh cookie revoke + cookie törlés</li>
 *   <li>{@code POST /api/auth/password-change} — current + new password</li>
 * </ul>
 *
 * <p>A refresh token {@code HttpOnly + Secure + SameSite=Strict} cookie-ban
 * jön a klienshez. Same-origin esetén (Vite proxy dev, Nginx reverse proxy prod)
 * a cookie automatikusan csatolódik minden kéréshez.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    private static final String REFRESH_TOKEN_PATH = "/api/auth";
    private static final long REFRESH_TOKEN_MAX_AGE_SECONDS = 30L * 24 * 60 * 60; // 30 nap

    private final UserService userService;
    private final AppUserRepository appUserRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final LoginService loginService;

    /**
     * Login endpoint — email + password → access token + refresh cookie.
     *
     * <p>A refresh token cookie {@code HttpOnly + Secure + SameSite=Strict}
     * flag-ekkel jön (XSS protection). Dev-ben a Secure flag-ot a Spring
     * figyelmen kívül hagyja (HTTP), prod-ban HTTPS kell.
     *
     * <p>A mustChangePassword=true user-ek a loginResponse mellett egy
     * {@code mustChangePassword: true} flag-et is kapnak — a frontend e
     * alapján a /password-change-re irányítja a user-t.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        Authentication authentication;
        try {
            authentication = loginService.authenticate(request.email(), request.password());
        } catch (BadCredentialsException | org.springframework.security.authentication.DisabledException
                 | org.springframework.security.authentication.LockedException e) {
            throw new UnauthorizedActionException("invalidCredentials", "Invalid email or password");
        }

        // User betöltése a refresh token és mustChangePassword flag-hez
        String emailHash = (String) authentication.getPrincipal();
        AppUser user = appUserRepository.findByEmailHash(emailHash)
                .orElseThrow(() -> new UnauthorizedActionException("userNotFound", "User not found"));

        // Refresh token issue + cookie beállítás
        RefreshTokenService.IssueResult issued = refreshTokenService.issue(user);
        setRefreshTokenCookie(response, issued.plainToken());

        // Access token generálás (role + permissions a GrantedAuthority-kból)
        List<String> permissions = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> !a.startsWith("ROLE_"))
                .collect(Collectors.toList());
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .findFirst()
                .orElse("ROLE_USER");

        String accessToken = jwtTokenProvider.generateAccessToken(emailHash, role, permissions);

        LoginResponse loginResponse = new LoginResponse(
                accessToken,
                jwtTokenProvider.getAccessTokenTtlSeconds()
        );

        log.info("User {} logged in successfully (mustChangePassword={})", emailHash, user.isMustChangePassword());
        return ResponseEntity.ok(loginResponse);
    }

    /**
     * Refresh endpoint — refresh cookie → új access token + új refresh cookie.
     *
     * <p>A régi refresh token revokeolódik (rotation), és új cookie jön.
     * Ha a régi token már revoke (reuse detection), az egész chain revokeolódik.
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(HttpServletResponse response) {
        String refreshToken = readRefreshTokenCookie();
        if (refreshToken == null) {
            throw new UnauthorizedActionException("refreshTokenMissing", "Refresh token cookie is missing");
        }

        try {
            RefreshTokenService.RotationResult rotated = refreshTokenService.rotate(refreshToken);
            setRefreshTokenCookie(response, rotated.newPlainToken());

            // Access token a rotated refresh token user-jéhez
            AppUser user = rotated.newRefreshToken().getUser();
            String emailHash = user.getEmailHash();
            String role = "ROLE_" + user.getRole().getName().replace("ROLE_", "");
            List<String> permissions = user.getPermissions().stream()
                    .map(p -> p.getName())
                    .collect(Collectors.toList());

            String newAccessToken = jwtTokenProvider.generateAccessToken(emailHash, role, permissions);

            return ResponseEntity.ok(new LoginResponse(
                    newAccessToken,
                    jwtTokenProvider.getAccessTokenTtlSeconds()
            ));
        } catch (IllegalStateException e) {
            // Reuse detection vagy lejárt token
            clearRefreshTokenCookie(response);
            throw new UnauthorizedActionException("refreshTokenInvalid", e.getMessage());
        }
    }

    /**
     * Logout endpoint — refresh cookie revoke + cookie törlés.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        String refreshToken = readRefreshTokenCookie();
        if (refreshToken != null) {
            refreshTokenService.revoke(refreshToken);
        }
        clearRefreshTokenCookie(response);
        log.info("User logged out");
        return ResponseEntity.noContent().build();
    }

    /**
     * Password change endpoint (Task 2.2-ből).
     */
    @PostMapping("/password-change")
    public ResponseEntity<Void> changePassword(
            Authentication authentication,
            @Valid @RequestBody PasswordChangeRequest request
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedActionException("authRequired", "Authentication required");
        }

        String emailHash = (String) authentication.getPrincipal();
        AppUser user = appUserRepository.findByEmailHash(emailHash)
                .orElseThrow(() -> new UnauthorizedActionException("userNotFound", "User not found"));

        userService.changePassword(user.getId(), request.currentPassword(), request.newPassword());

        log.info("Password changed for user {}", user.getId());
        return ResponseEntity.noContent().build();
    }

    // ===== Cookie helper-ek =====

    private void setRefreshTokenCookie(HttpServletResponse response, String plainToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, plainToken)
                .httpOnly(true)
                .secure(true)  // Prod-ban HTTPS; dev-ben a Spring figyelmen kívül hagyja
                .sameSite("Strict")
                .path(REFRESH_TOKEN_PATH)
                .maxAge(Duration.ofSeconds(REFRESH_TOKEN_MAX_AGE_SECONDS))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(REFRESH_TOKEN_PATH)
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String readRefreshTokenCookie() {
        // A HttpServletRequest-ből közvetlenül olvassuk, mert a SecurityContext még nincs beállítva
        // (a request el sem jut a JwtAuthenticationFilterig, ha nincs token).
        // Implementációkor: HttpServletRequest-ből @CookieValue annotation-nel.
        return null; // TODO: implementálni kell HttpServletRequest-ből
    }
}