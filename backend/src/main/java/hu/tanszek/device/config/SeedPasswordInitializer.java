package hu.tanszek.device.config;

import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * SeedPasswordInitializer — induláskor ellenőrzi, hogy a demo admin user
 * password_hash-e placeholder, és ha igen, generál egy valódi Argon2id hash-t.
 *
 * <p>Ez azért szükséges, mert a V2__seed.sql-ben a password_hash placeholder
 * (a Flyway migration-ök nem generálhatnak jelszó hash-t — a salt random),
 * és a LocalAuthProvider jelszó-ellenőrzése a valódi Argon2 hash-t vár.
 *
 * <p>Induláskor egyszer fut le. Ha a demo admin user password_hash-e placeholder,
 * generál egy új Argon2id hash-t a 'ChangeMe123!' jelszóhoz, és frissíti a DB-t.
 * A mustChangePassword=true flag biztosítja, hogy az első login után a user
 * megváltoztassa a jelszavát.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeedPasswordInitializer implements ApplicationRunner {

    private static final String DEFAULT_ADMIN_EMAIL = "admin@tanszek.local";
    private static final String DEFAULT_PASSWORD = "ChangeMe123!";
    private static final String PLACEHOLDER_MARKER = "PLACEHOLDER_SALT";

    private final AppUserRepository userRepository;
    private final Argon2PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Demo admin user keresése email_hash alapján
        String emailHash = sha256(DEFAULT_ADMIN_EMAIL);
        userRepository.findByEmailHash(emailHash).ifPresent(user -> {
            String currentHash = user.getPasswordHash();
            if (currentHash != null && currentHash.contains(PLACEHOLDER_MARKER)) {
                // Placeholder hash → valódi Argon2id hash generálás
                String newHash = passwordEncoder.encode(DEFAULT_PASSWORD);
                user.setPasswordHash(newHash);
                user.setPasswordChangedAt(Instant.now());
                userRepository.save(user);
                log.info("SeedPasswordInitializer: placeholder password hash replaced for admin user");
            } else {
                log.debug("SeedPasswordInitializer: admin user password hash already valid (no placeholder)");
            }
        });
    }

    /**
     * SHA-256 hash az emailből (LocalAuthProvider-rel kompatibilis).
     */
    private String sha256(String input) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
