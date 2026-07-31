package hu.tanszek.device.user;

import hu.tanszek.device.auth.repository.RefreshTokenRepository;
import hu.tanszek.device.common.BusinessValidationException;
import hu.tanszek.device.common.ResourceNotFoundException;
import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tesztek a {@link UserService}-hez.
 *
 * <p>Teszteli:
 * <ul>
 *   <li>changePassword: sikeres, hibás current password, túl rövid new password,
 *       new == current, user nem található, refresh token revoke</li>
 *   <li>deactivate: sikeres, már deaktivált user, refresh token revoke</li>
 *   <li>reactivate: sikeres, már aktív user</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private AppUserRepository appUserRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private hu.tanszek.device.auth.repository.RoleRepository roleRepository;
    @Mock private hu.tanszek.device.location.repository.LocationRepository locationRepository;

    private Argon2PasswordEncoder passwordEncoder;

    @InjectMocks private UserService userService;

    private AppUser user;

    @BeforeEach
    void setUp() {
        passwordEncoder = new Argon2PasswordEncoder(16, 32, 1, 65536, 3);
        userService = new UserService(appUserRepository, refreshTokenRepository, roleRepository, locationRepository, passwordEncoder);

        user = AppUser.builder()
                .id(1L)
                .emailEncrypted("encrypted")
                .emailHash("hash123")
                .passwordHash(passwordEncoder.encode("CurrentPass123!"))
                .active(true)
                .mustChangePassword(false)
                .failedLoginCount(0)
                .lockedUntil(null)
                .build();
    }

    // ===== changePassword =====

    @Test
    void changePasswordSuccessRevokesRefreshTokens() {
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.revokeAllRefreshTokensByUserId(1L)).thenReturn(2);

        userService.changePassword(1L, "CurrentPass123!", "NewSecurePass456!");

        // Argon2 hash újragenerálódott
        assertThat(passwordEncoder.matches("NewSecurePass456!", user.getPasswordHash())).isTrue();
        // mustChangePassword törölve
        assertThat(user.isMustChangePassword()).isFalse();
        // Refresh token-ek revoke-olva
        verify(refreshTokenRepository, times(1)).revokeAllRefreshTokensByUserId(1L);
        // User save-olva
        verify(appUserRepository, times(1)).save(user);
    }

    @Test
    void changePasswordWithIncorrectCurrentPasswordThrowsException() {
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.changePassword(1L, "WrongPassword!", "NewSecurePass456!"))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("incorrect");

        verify(refreshTokenRepository, never()).revokeAllRefreshTokensByUserId(anyLong());
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void changePasswordWithTooShortNewPasswordThrowsException() {
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.changePassword(1L, "CurrentPass123!", "short"))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("at least 12");
    }

    @Test
    void changePasswordWithSamePasswordThrowsException() {
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));

        // Same password: currentPassword == newPassword
        assertThatThrownBy(() -> userService.changePassword(1L, "CurrentPass123!", "CurrentPass123!"))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("different");
    }

    @Test
    void changePasswordForNonExistentUserThrowsException() {
        when(appUserRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changePassword(99L, "anything", "NewSecurePass456!"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ===== deactivate =====

    @Test
    void deactivateSuccessRevokesRefreshTokens() {
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.revokeAllRefreshTokensByUserId(1L)).thenReturn(3);

        userService.deactivate(1L);

        assertThat(user.isActive()).isFalse();
        verify(refreshTokenRepository, times(1)).revokeAllRefreshTokensByUserId(1L);
    }

    @Test
    void deactivateAlreadyDeactivatedUserDoesNothing() {
        user.setActive(false);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.deactivate(1L);

        verify(refreshTokenRepository, never()).revokeAllRefreshTokensByUserId(anyLong());
        verify(appUserRepository, never()).save(any());
    }

    // ===== reactivate =====

    @Test
    void reactivateSuccess() {
        user.setActive(false);
        user.setFailedLoginCount(3);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.reactivate(1L);

        assertThat(user.isActive()).isTrue();
        assertThat(user.getFailedLoginCount()).isZero();
        verify(appUserRepository, times(1)).save(user);
    }

    @Test
    void reactivateAlreadyActiveUserDoesNothing() {
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.reactivate(1L);

        verify(appUserRepository, never()).save(any());
    }
}