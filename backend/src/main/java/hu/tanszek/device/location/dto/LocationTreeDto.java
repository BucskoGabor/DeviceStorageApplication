package hu.tanszek.device.location.dto;

import java.util.List;

import hu.tanszek.device.location.entity.Location;
import hu.tanszek.device.location.entity.LocationType;

/**
 * LocationTreeDto — hierarchikus location fa nested DTO.
 *
 * <p>A {@link Location} entitás {@code children} kollekciója {@code @JsonIgnore}-elt (LAZY fetch
 * elkerülése végett), ezért ez a DTO szolgál a fa megjelenítésére.
 *
 * <p>A tree építése service-szinten történik (lásd {@code LocationController.findTree}), hogy a
 * JSON válasz garantáltan véges mélységű és a ciklusok ellen védett.
 */
public record LocationTreeDto(
    Long id,
    String name,
    LocationType type,
    Long parentId,
    int depth,
    List<LocationTreeDto> children) {
  public static LocationTreeDto fromEntity(Location location, Long parentId, int depth) {
    return new LocationTreeDto(
        location.getId(), location.getName(), location.getType(), parentId, depth, List.of());
  }
}
