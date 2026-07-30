package hu.tanszek.device.user;

import hu.tanszek.device.auth.repository.RefreshTokenRepository;
import hu.tanszek.device.audit.AuditTarget;
import hu.tanszek.device.common.BusinessValidationException;
import hu.tanszek.device.common.ResourceNotFoundException;
import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * UserService — felhasználó-kezelési üzleti logika.
 *
 * <p>A UserService a CRUD műveleteken kívül a password change és a
 * deactivation flow-t kezeli. Az authentication a {@link hu.tanszek.device.auth.LoginService}-ben van.
 *
 * @see LoginService
 * @see AppUserRepository
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository appUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Argon2PasswordEncoder passwordEncoder;

    /**
     * User jelszavának cseréje.
     *
     * <p>A metódus ellenőrzi a currentPassword-t (biztonsági okokból), és ha
     * helyes, beállítja az új jelszót (Argon2id hash), törli a must_change_password
     * flag-et, és frissíti a password_changed_at timestampet.
     *
     * @param userId a user ID-ja
     * @param currentPassword a jelenlegi plain text jelszó (megerősítés)
     * @param newPassword az új plain text jelszó
     * @throws ResourceNotFoundException ha a user nem található
     * @throws BusinessValidationException ha a currentPassword hibás
     */
    @AuditTarget(entityType = "AppUser", action = "change_password")
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        // 1. Current password verify (Argon2)
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            log.warn("Password change failed for user {}: incorrect current password", userId);
            throw new BusinessValidationException("passwordChangeInvalidCurrent", "Current password is incorrect");
        }

        // 2. New password validation (min 12 karakter a jelenlegi policy)
        if (newPassword == null || newPassword.length() < 12) {
            throw new BusinessValidationException("passwordChangeTooShort", "New password must be at least 12 characters");
        }

        // 3. New password != current password
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessValidationException("passwordChangeSameAsOld", "New password must be different from current");
        }

        // 4. Update
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(Instant.now());
        user.setMustChangePassword(false);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        appUserRepository.save(user);

        // 5. Bulk revoke minden aktív refresh token (security: régi session-ök elvesznek)
        int revokedCount = refreshTokenRepository.revokeAllRefreshTokensByUserId(userId);
        log.info("Password changed for user {}, revoked {} refresh tokens", userId, revokedCount);
    }

    /**
     * User deaktiválása.
     *
     * <p>A deaktivált user nem tud belépni ({@code active = false}). Az aktív
     * refresh tokenjei automatikusan revokeolódnak, így a meglévő session-jei
     * is érvénytelenítődnek.
     *
     * @param userId a user ID-ja
     * @throws ResourceNotFoundException ha a user nem található
     */
    @AuditTarget(entityType = "AppUser", action = "deactivate")
    @Transactional
    public void deactivate(Long userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (!user.isActive()) {
            log.info("User {} is already deactivated", userId);
            return;
        }

        user.setActive(false);
        appUserRepository.save(user);

        // Bulk revoke refresh tokens — UserSession azonnal lejár
        int revokedCount = refreshTokenRepository.revokeAllRefreshTokensByUserId(userId);
        log.info("User {} deactivated, revoked {} refresh tokens", userId, revokedCount);
    }

    /**
     * User reaktiválása (admin által).
     *
     * @param userId a user ID-ja
     * @throws ResourceNotFoundException ha a user nem található
     */
    @Transactional
    public void reactivate(Long userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (user.isActive()) {
            log.info("User {} is already active", userId);
            return;
        }

        user.setActive(true);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        appUserRepository.save(user);

        log.info("User {} reactivated", userId);
    }
}