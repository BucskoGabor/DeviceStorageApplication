package hu.tanszek.device.location.controller;

import hu.tanszek.device.auth.RequirePermission;
import hu.tanszek.device.common.ResourceNotFoundException;
import hu.tanszek.device.location.entity.Location;
import hu.tanszek.device.location.entity.LocationType;
import hu.tanszek.device.location.repository.LocationRepository;
import hu.tanszek.device.location.LocationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * LocationController — REST endpointok a Location entity CRUD-hoz.
 */
@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController {

    private static final int MAX_PAGE_SIZE = 50;

    private final LocationRepository locationRepository;
    private final LocationService locationService;

    @GetMapping
    @RequirePermission("LOCATION_READ")
    public ResponseEntity<Map<String, Object>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (size > MAX_PAGE_SIZE) size = MAX_PAGE_SIZE;
        if (size < 1) size = 1;
        if (page < 0) page = 0;

        var pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<Location> result = locationRepository.findAll(pageable);

        return ResponseEntity.ok(Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "size", result.getSize(),
                "number", result.getNumber()
        ));
    }

    @GetMapping("/{id}")
    @RequirePermission("LOCATION_READ")
    public ResponseEntity<Location> findById(@PathVariable Long id) {
        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + id));
        return ResponseEntity.ok(location);
    }

    @GetMapping("/roots")
    @RequirePermission("LOCATION_READ")
    public ResponseEntity<List<Location>> findRoots() {
        return ResponseEntity.ok(locationRepository.findByParentIsNull());
    }

    @GetMapping("/by-type/{type}")
    @RequirePermission("LOCATION_READ")
    public ResponseEntity<List<Location>> findByType(@PathVariable String type) {
        LocationType locationType = LocationType.valueOf(type);
        return ResponseEntity.ok(locationRepository.findByType(locationType));
    }

    @PostMapping
    @RequirePermission("LOCATION_MANAGE")
    public ResponseEntity<Location> create(@Valid @RequestBody CreateLocationRequest request) {
        // Cycle check: a parentId nem vezethet ciklusba
        if (request.parentId() != null) {
            locationRepository.findById(request.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent location not found: " + request.parentId()));
            // Új location-nál nincs locationId, de a parentId-t ellenőrizzük
            locationService.validateNoCycle(null, request.parentId());
        }

        Location parent = request.parentId() != null
                ? locationRepository.findById(request.parentId()).orElse(null)
                : null;

        Location location = Location.builder()
                .name(request.name())
                .parent(parent)
                .type(request.type())
                .version(0L)
                .build();

        Location saved = locationRepository.save(location);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    @RequirePermission("LOCATION_MANAGE")
    public ResponseEntity<Location> update(@PathVariable Long id, @Valid @RequestBody UpdateLocationRequest request) {
        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + id));

        // Cycle check: ha parentId változik, ellenőrizzük a ciklust
        if (request.parentId() != null) {
            locationRepository.findById(request.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent location not found: " + request.parentId()));
            locationService.validateNoCycle(id, request.parentId());
        }

        if (request.name() != null) location.setName(request.name());
        if (request.parentId() != null) {
            Location parent = locationRepository.findById(request.parentId()).orElse(null);
            location.setParent(parent);
        }
        if (request.type() != null) location.setType(request.type());

        Location saved = locationRepository.save(location);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    @RequirePermission("LOCATION_MANAGE")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!locationRepository.existsById(id)) {
            throw new ResourceNotFoundException("Location not found: " + id);
        }
        locationRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    public record CreateLocationRequest(
            @NotBlank @Size(max = 255) String name,
            @NotNull LocationType type,
            Long parentId
    ) {}

    public record UpdateLocationRequest(
            @Size(max = 255) String name,
            Long parentId,
            LocationType type
    ) {}
}
