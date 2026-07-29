package hu.tanszek.device.auth.dto;

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
        String currentPassword,

        @NotBlank(message = "validation.notBlank")
        @Size(min = 12, max = 128, message = "validation.minLength")
        String newPassword
) {}