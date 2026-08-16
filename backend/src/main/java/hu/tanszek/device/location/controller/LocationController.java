package hu.tanszek.device.location.controller;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import hu.tanszek.device.auth.RequirePermission;
import hu.tanszek.device.common.ResourceNotFoundException;
import hu.tanszek.device.location.LocationService;
import hu.tanszek.device.location.dto.LocationTreeDto;
import hu.tanszek.device.location.entity.Location;
import hu.tanszek.device.location.entity.LocationType;
import hu.tanszek.device.location.repository.LocationRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/** LocationController — REST endpointok a Location entity CRUD-hoz. */
@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
@Tag(name = "Location", description = "Hierarchikus helyszín CRUD + tree nézet")
public class LocationController {

  private static final int MAX_PAGE_SIZE = 50;

  private final LocationRepository locationRepository;
  private final LocationService locationService;
  private final hu.tanszek.device.device.repository.DeviceRepository deviceRepository;
  private final hu.tanszek.device.assignment.repository.DeviceAssignmentRepository assignmentRepository;

  /**
   * GET /api/locations/tree A teljes location hierarchia nested DTO formában. Max depth: 10
   * (LocationService.MAX_TREE_DEPTH).
   */
  @Operation(
      summary = "Location fa",
      description =
          "A teljes hierarchia nested DTO-ban. "
              + "Max depth 10 — efelett a node-ok üres children listát kapnak.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Sikeres fa"),
    @ApiResponse(responseCode = "403", description = "LOCATION_READ permission hiányzik")
  })
  @GetMapping("/tree")
  @RequirePermission({"LOCATION_READ", "LOCATION_CREATE", "LOCATION_UPDATE", "LOCATION_DELETE"})
  public ResponseEntity<List<LocationTreeDto>> findTree() {
    return ResponseEntity.ok(locationService.buildTree());
  }

  @Operation(summary = "Helyszín lista (lapozott)")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Sikeres lista"),
    @ApiResponse(responseCode = "403", description = "LOCATION_READ permission hiányzik")
  })
  @GetMapping
  @RequirePermission({"LOCATION_READ", "LOCATION_CREATE", "LOCATION_UPDATE", "LOCATION_DELETE"})
  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public ResponseEntity<Map<String, Object>> findAll(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    if (size > MAX_PAGE_SIZE) {
      size = MAX_PAGE_SIZE;
    }
    if (size < 1) {
      size = 1;
    }
    if (page < 0) {
      page = 0;
    }

    var pageable = PageRequest.of(page, size, Sort.by("name").ascending());
    Page<Location> result = locationRepository.findAll(pageable);

    return ResponseEntity.ok(
        Map.of(
            "content", result.getContent(),
            "totalElements", result.getTotalElements(),
            "totalPages", result.getTotalPages(),
            "size", result.getSize(),
            "number", result.getNumber()));
  }

  @Operation(summary = "Helyszín részletek")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Sikeres lekérdezés"),
    @ApiResponse(responseCode = "404", description = "Helyszín nem található"),
    @ApiResponse(responseCode = "403", description = "LOCATION_READ permission hiányzik")
  })
  @GetMapping("/{id}")
  @RequirePermission({"LOCATION_READ", "LOCATION_CREATE", "LOCATION_UPDATE", "LOCATION_DELETE"})
  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public ResponseEntity<Location> findById(
      @Parameter(description = "Helyszín azonosító") @PathVariable Long id) {
    Location location =
        locationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + id));
    return ResponseEntity.ok(location);
  }

  @Operation(summary = "Helyszínen lévő jelenlegi eszközök")
  @GetMapping("/{id}/devices")
  @RequirePermission({"LOCATION_READ", "LOCATION_CREATE", "LOCATION_UPDATE", "LOCATION_DELETE"})
  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public ResponseEntity<List<hu.tanszek.device.device.entity.Device>> findCurrentDevices(
      @PathVariable Long id) {
    locationRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Location not found: " + id));
    List<hu.tanszek.device.device.entity.Device> devices =
        deviceRepository.findByCurrentLocationIdAndStatusNot(
            id, hu.tanszek.device.device.entity.DeviceStatus.DISPOSED);
    return ResponseEntity.ok(devices);
  }

  @Operation(summary = "Helyszín hozzárendelési előzményei")
  @GetMapping("/{id}/assignments")
  @RequirePermission({"LOCATION_READ", "LOCATION_CREATE", "LOCATION_UPDATE", "LOCATION_DELETE"})
  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public ResponseEntity<List<hu.tanszek.device.assignment.entity.DeviceAssignment>> findAssignmentHistory(
      @PathVariable Long id) {
    locationRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Location not found: " + id));
    List<hu.tanszek.device.assignment.entity.DeviceAssignment> history =
        assignmentRepository.findByToLocationIdOrFromLocationIdOrderByCreatedDateDesc(id, id);
    return ResponseEntity.ok(history);
  }

  @Operation(
      summary = "Root helyszínek",
      description = "Visszaadja a parent == null node-okat (legfelső szint).")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Sikeres lista"),
    @ApiResponse(responseCode = "403", description = "LOCATION_READ permission hiányzik")
  })
  @GetMapping("/roots")
  @RequirePermission({"LOCATION_READ", "LOCATION_CREATE", "LOCATION_UPDATE", "LOCATION_DELETE"})
  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public ResponseEntity<List<Location>> findRoots() {
    return ResponseEntity.ok(locationRepository.findByParentIsNull());
  }

  @Operation(
      summary = "Helyszínek típus szerint",
      description = "Visszaadja az adott típusú helyszíneket.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Sikeres lista"),
    @ApiResponse(responseCode = "400", description = "Érvénytelen típus"),
    @ApiResponse(responseCode = "403", description = "LOCATION_READ permission hiányzik")
  })
  @GetMapping("/by-type/{type}")
  @RequirePermission({"LOCATION_READ", "LOCATION_CREATE", "LOCATION_UPDATE", "LOCATION_DELETE"})
  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public ResponseEntity<List<Location>> findByType(
      @Parameter(description = "Helyszín típus (CLASSROOM/OFFICE/STORAGE/GROUP)") @PathVariable
          String type) {
    LocationType locationType = LocationType.valueOf(type);
    return ResponseEntity.ok(locationRepository.findByType(locationType));
  }

  @Operation(
      summary = "Új helyszín létrehozása",
      description =
          "Cycle check a parentId-re, ha megadott. "
              + "A move() retry logikát használ, ha parentId változik.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Helyszín létrehozva"),
    @ApiResponse(responseCode = "400", description = "Validációs hiba vagy ciklus"),
    @ApiResponse(responseCode = "404", description = "Parent location nem található"),
    @ApiResponse(responseCode = "403", description = "LOCATION_CREATE permission hiányzik")
  })
  @PostMapping
  @RequirePermission("LOCATION_CREATE")
  @org.springframework.transaction.annotation.Transactional
  public ResponseEntity<Location> create(@Valid @RequestBody CreateLocationRequest request) {
    // Cycle check: a parentId nem vezethet ciklusba
    if (request.parentId() != null) {
      locationRepository
          .findById(request.parentId())
          .orElseThrow(
              () ->
                  new ResourceNotFoundException(
                      "Parent location not found: " + request.parentId()));
      // Új location-nál nincs locationId, de a parentId-t ellenőrizzük
      locationService.validateNoCycle(null, request.parentId());
    }

    Location parent =
        request.parentId() != null
            ? locationRepository.findById(request.parentId()).orElse(null)
            : null;

    Location location =
        Location.builder()
            .name(request.name())
            .parent(parent)
            .type(request.type())
            .version(0L)
            .build();

    Location saved = locationRepository.save(location);
    return ResponseEntity.status(HttpStatus.CREATED).body(saved);
  }

  @Operation(
      summary = "Helyszín módosítása",
      description =
          "Partial update — csak a nem-null mezők frissülnek. "
              + "Ha parentId változik, a LocationService.move() hívódik (3x retry optimistic lock esetén).")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Helyszín módosítva"),
    @ApiResponse(responseCode = "400", description = "Ciklus vagy validációs hiba"),
    @ApiResponse(responseCode = "404", description = "Helyszín nem található"),
    @ApiResponse(responseCode = "403", description = "LOCATION_UPDATE permission hiányzik")
  })
  @PutMapping("/{id}")
  @RequirePermission("LOCATION_UPDATE")
  @org.springframework.transaction.annotation.Transactional
  public ResponseEntity<Location> update(
      @Parameter(description = "Helyszín azonosító") @PathVariable Long id,
      @Valid @RequestBody UpdateLocationRequest request) {
    // Ha parentId változik, a move() hívása (retry logikával + cycle check)
    if (request.parentId() != null && !isSameParent(id, request.parentId())) {
      Location moved = locationService.move(id, request.parentId());
      return ResponseEntity.ok(moved);
    }

    Location location =
        locationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + id));

    if (request.name() != null) location.setName(request.name());
    if (request.type() != null) location.setType(request.type());

    Location saved = locationRepository.save(location);
    return ResponseEntity.ok(saved);
  }

  /**
   * Ellenőrzi, hogy a location current parent-je megegyezik-e a kért parentId-vel. Ha igen, a
   * move() felesleges.
   */
  private boolean isSameParent(Long locationId, Long newParentId) {
    return locationRepository
        .findById(locationId)
        .map(
            loc -> {
              Long currentParentId = loc.getParent() != null ? loc.getParent().getId() : null;
              return java.util.Objects.equals(currentParentId, newParentId);
            })
        .orElse(false);
  }

  @Operation(
      summary = "Helyszín törlése",
      description =
          "Hard delete. Ha vannak child location-ok vagy device assignment-ek, "
              + "a foreign key constraint miatt a törlés sikertelen lesz (DB hiba).")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Helyszín törölve"),
    @ApiResponse(responseCode = "404", description = "Helyszín nem található"),
    @ApiResponse(responseCode = "403", description = "LOCATION_DELETE permission hiányzik")
  })
  @DeleteMapping("/{id}")
  @RequirePermission("LOCATION_DELETE")
  public ResponseEntity<Void> delete(
      @Parameter(description = "Helyszín azonosító") @PathVariable Long id) {
    if (!locationRepository.existsById(id)) {
      throw new ResourceNotFoundException("Location not found: " + id);
    }
    locationRepository.deleteById(id);
    return ResponseEntity.noContent().build();
  }

  public record CreateLocationRequest(
      @NotBlank @Size(max = 255) String name, @NotNull LocationType type, Long parentId) {}

  public record UpdateLocationRequest(
      @Size(max = 255) String name, Long parentId, LocationType type) {}
}
