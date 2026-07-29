package hu.tanszek.device.auth;

import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * LoginService — felhasználó authentikáció és password rehash.
 *
 * <p>Az authenticate metódus az {@link AuthProviderFactory} által szolgáltatott
 * aktív provider-re delegál (LOCAL/AD), majd sikeres hitelesítés esetén
 * ellenőrzi az Argon2 hash paramétereit, és szükség esetén transparent
 * újrahasheli + DB-be írja.
 *
 * <p>Rehash flow:
 * <ol>
 *   <li>Sikeres authenticate() a provider-től (LocalAuthProvider.matches())</li>
 *   <li>passwordEncoder.upgradeEncoding(user.passwordHash) — true ha a hash
 *       paraméterei elavultak</li>
 *   <li>Ha true: passwordEncoder.encode(rawPassword) → user.passwordHash = newHash</li>
 *   <li>user.passwordChangedAt = NOW() — first-login reset</li>
 *   <li>user.mustChangePassword = false — first-login flag törlése</li>
 *   <li>appUserRepository.save(user) — egy tranzakcióban</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginService {

    private final AuthProviderFactory authProviderFactory;
    private final AppUserRepository appUserRepository;
    private final Argon2PasswordEncoder passwordEncoder;

    /**
     * Authentikáció az aktív AuthProvider-en keresztül + transparent rehash.
     *
     * @param email a user email címe (login input)
     * @param password a user plain text jelszava (login input)
     * @return Spring Security {@link Authentication} object (username + authorities)
     * @throws BadCredentialsException ha a hitelesítés sikertelen
     * @throws DisabledException ha a user deaktivált
     * @throws LockedException ha a user lockedUntil > now
     */
    @Transactional
    public Authentication authenticate(String email, String password) {
        // 1. Delegate to active provider (LocalAuthProvider vagy StubAdAuthProvider)
        Authentication authentication;
        try {
            authentication = authProviderFactory.getActiveProvider().authenticate(email, password);
        } catch (BadCredentialsException | DisabledException | LockedException e) {
            // Provider már throw-olt a saját kivételével, csak továbbdobjuk
            throw e;
        }

        // 2. Sikeres hitelesítés: rehash check (csak LocalAuthProvider-nél van user objektum)
        if (authentication.getPrincipal() instanceof String emailHash) {
            // emailHash-ből AppUser-t betöltjük a rehash-hez (provider már validálta)
            AppUser user = appUserRepository.findByEmailHash(emailHash)
                    .orElseThrow(() -> new BadCredentialsException("User disappeared during authentication"));

            maybeRehashPassword(user, password);
        }

        return authentication;
    }

    /**
     * Argon2 hash paraméterek ellenőrzése és transparent rehash, ha szükséges.
     *
     * <p>Ez a metódus a Spring Security {@code Argon2PasswordEncoder.upgradeEncoding()}
     * metódusát használja, ami ellenőrzi a hash beágyazott paramétereit
     * (memory, iterations, parallelism) a konfigurációban beállított
     * policy-hez képest.
     *
     * @param user a frissítendő user entitás
     * @param rawPassword a plain text jelszó (hash input)
     */
    private void maybeRehashPassword(AppUser user, String rawPassword) {
        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            log.info("Upgrading password hash for user {} (old params → new params)",
                    user.getEmailHash());

            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            user.setPasswordChangedAt(Instant.now());
            user.setMustChangePassword(false);
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);

            appUserRepository.save(user);
        }
    }
}