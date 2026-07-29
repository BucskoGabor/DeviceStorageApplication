package hu.tanszek.device.import_.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Excel import — user sor DTO.
 *
 * <p>A header sorrend: email, firstName, lastName, role, active, officeLocationName.
 *
 * <p>Az importáláskor email_hash (SHA-256) alapján UPDATE-or-SKIP logika:
 * <ul>
 *   <li>Ha az email_hash már létezik → UPDATE (meglévő user frissítése)</li>
 *   <li>Ha nem létezik → INSERT (új user létrehozása)</li>
 * </ul>
 */
public record ImportUserRow(
        @NotBlank(message = "validation.notBlank")
        @Email(message = "validation.email")
        String email,

        @NotBlank(message = "validation.notBlank")
        @Size(max = 100)
        String firstName,

        @NotBlank(message = "validation.notBlank")
        @Size(max = 100)
        String lastName,

        @NotBlank(message = "validation.notBlank")
        String role,  // ROLE_ADMIN / ROLE_TEACHER / ROLE_STUDENT

        Boolean active,  // nullable — default true

        @Size(max = 255)
        String officeLocationName  // optional — iroda neve
) {}