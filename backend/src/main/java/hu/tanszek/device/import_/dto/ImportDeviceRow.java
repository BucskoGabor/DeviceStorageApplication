package hu.tanszek.device.import_.dto;

import hu.tanszek.device.common.SizeLimits;
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
        @Size(max = SizeLimits.INVENTORY_NUMBER_MAX)
        String inventoryNumber,

        @NotBlank(message = "validation.notBlank")
        @Pattern(regexp = "[a-zA-Z0-9-_]+", message = "validation.pattern")
        @Size(max = SizeLimits.SHORT_TEXT_MAX)
        String type,  // pl. laptop, monitor, projektor

        @NotBlank(message = "validation.notBlank")
        String status,  // PENDING / ASSIGNED / IN_STORAGE / MAINTENANCE / DISPOSED

        @Size(max = SizeLimits.MEDIUM_TEXT_MAX)
        String locationName  // optional — kezdeti location neve
) {}