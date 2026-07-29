package hu.tanszek.device.import_.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Excel import — device sor DTO.
 *
 * <p>A header sorrend: inventoryNumber, type, status, locationName.
 *
 * <p>Az importáláskor inventory_number alapján UPDATE-or-SKIP logika:
 * <ul>
 *   <li>Ha az inventory_number már létezik → UPDATE</li>
 *   <li>Ha nem létezik → INSERT</li>
 * </ul>
 */
public record ImportDeviceRow(
        @NotBlank(message = "validation.notBlank")
        @Size(max = 50)
        String inventoryNumber,

        @NotBlank(message = "validation.notBlank")
        @Pattern(regexp = "[a-zA-Z0-9-_]+", message = "validation.pattern")
        @Size(max = 50)
        String type,  // pl. laptop, monitor, projektor

        @NotBlank(message = "validation.notBlank")
        String status,  // PENDING / ASSIGNED / IN_STORAGE / MAINTENANCE / DISPOSED

        @Size(max = 255)
        String locationName  // optional — kezdeti location neve
) {}