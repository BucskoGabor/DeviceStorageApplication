package hu.tanszek.device.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Login request DTO.
 *
 * @param email a user email címe
 * @param password a user plain text jelszava
 */
public record LoginRequest(
        @NotBlank(message = "validation.notBlank")
        @Email(message = "validation.email")
        String email,

        @NotBlank(message = "validation.notBlank")
        String password
) {}