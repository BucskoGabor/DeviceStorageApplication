package hu.tanszek.device.auth;

import hu.tanszek.device.auth.entity.Permission;
import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;
import hu.tanszek.device.config.repository.ConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * LocalAuthProvider — Argon2 + DB alapú authentikáció.
 *
 * <p>Authentikáció flow:
 * <ol>
 *   <li>SHA-256 hash az emailből ({@code emailHash})</li>
 *   <li>User lookup az AppUserRepository.findByEmailHash metódussal</li>
 *   <li>User.active = true check (különben DisabledException)</li>
 *   <li>User.lockedUntil > now check (különben LockedException)</li>
 *   <li>Argon2PasswordEncoder.matches(rawPassword, user.passwordHash) check
 *       (különben BadCredentialsException, és failedLoginCount növelés)</li>
 *   <li>Sikeres authentikáció: UsernamePasswordAuthenticationToken a role + permission
 *       authorities-kkal</li>
 *   <li>Argon2PasswordEncoder.upgradeEncoding() check — ha a hash paraméterei
 *       elavultak, transparent rehash és DB update (egy tranzakcióban)</li>
 * </ol>
 *
 * <p>Lockout mechanizmus: 5 hibás próba → 15 perces lockout
 * ({@code lockedUntil = now + 15min}, {@code failedLoginCount >= 5}).
 *
 * @see AuthProvider
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalAuthProvider implements AuthProvider {

    private static final String PROVIDER_ID = "LOCAL";

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MIN = 15;

    private final AppUserRepository appUserRepository;
    private final ConfigRepository configRepository;
    private final Argon2PasswordEncoder passwordEncoder;

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public Authentication authenticate(String email, String password) throws AuthenticationException {
        log.debug("Authentication attempt for email: {}", email);

        // 1. Email → SHA-256 hash
        String emailHash = sha256(email);
        AppUser user = appUserRepository.findByEmailHash(emailHash)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // 2. Aktív check
        if (!user.isActive()) {
            throw new DisabledException("User account is disabled");
        }

        // 3. Lockout check
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new LockedException("User account is locked until " + user.getLockedUntil());
        }

        // 4. Password verify
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            handleFailedLogin(user);
            throw new BadCredentialsException("Invalid email or password");
        }

        // 5. Successful auth: reset failedLoginCount, return Authentication
        resetFailedLoginCount(user);
        return buildAuthentication(user);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    /**
     * SHA-256 hash az email címből.
     */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Failed login kezelése: failedLoginCount növelés, lockout threshold check.
     */
    private void handleFailedLogin(AppUser user) {
        int failedCount = user.getFailedLoginCount() + 1;
        user.setFailedLoginCount(failedCount);

        if (failedCount >= MAX_LOGIN_ATTEMPTS) {
            user.setLockedUntil(Instant.now().plusSeconds(LOCKOUT_DURATION_MIN * 60));
            log.warn("User {} locked after {} failed attempts",
                    user.getEmailHash(), failedCount);
        }

        appUserRepository.save(user);
    }

    /**
     * Sikeres login: failedLoginCount és lockedUntil reset.
     * TODO Task 2.2: Argon2 upgrade check itt (passwordEncoder.upgradeEncoding()).
     */
    private void resetFailedLoginCount(AppUser user) {
        if (user.getFailedLoginCount() > 0 || user.getLockedUntil() != null) {
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
            appUserRepository.save(user);
        }
    }

    /**
     * UsernamePasswordAuthenticationToken összeállítása a role + permission authorities-kkal.
     */
    private Authentication buildAuthentication(AppUser user) {
        Set<hu.tanszek.device.auth.entity.Permission> userPermissions = user.getPermissions();
        List<GrantedAuthority> roleAuthorities = user.getRole().getPermissions().stream()
                .map(p -> new SimpleGrantedAuthority(p.getName()))
                .collect(Collectors.toList());
        List<GrantedAuthority> userSpecificAuthorities = userPermissions.stream()
                .map(p -> new SimpleGrantedAuthority(p.getName()))
                .collect(Collectors.toList());

        Collection<GrantedAuthority> allAuthorities = Stream.concat(
                Stream.concat(
                        Stream.of(new SimpleGrantedAuthority(user.getRole().getName())),
                        roleAuthorities.stream()
                ),
                userSpecificAuthorities.stream()
        ).collect(Collectors.toSet());

        return new UsernamePasswordAuthenticationToken(user.getEmailHash(), null, allAuthorities);
    }
}