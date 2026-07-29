package hu.tanszek.device.auth.dto;

import hu.tanszek.device.common.SizeLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Password change kérés DTO.
 *
 * <p>A user a /api/auth/password-change endpoint-ra küldi a currentPassword-öt
 * (megerősítés) és az új newPassword-öt. A service validálja mindkettőt
 * (Argon2 match a current, min 12 karakter a new).
 *
 * @see hu.tanszek.device.user.UserService#changePassword
 */
public record PasswordChangeRequest(
        @NotBlank(message = "validation.notBlank")
        @Size(max = SizeLimits.MEDIUM_TEXT_MAX)
        String currentPassword,

        @NotBlank(message = "validation.notBlank")
        @Size(min = 12, max = SizeLimits.PASSWORD_HASH_MAX, message = "validation.minLength")
        String newPassword
) {}