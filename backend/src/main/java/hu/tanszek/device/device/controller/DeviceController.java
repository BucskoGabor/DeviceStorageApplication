package hu.tanszek.device.device.controller;

import hu.tanszek.device.auth.RequirePermission;
import hu.tanszek.device.common.BusinessValidationException;
import hu.tanszek.device.common.ResourceNotFoundException;
import hu.tanszek.device.device.entity.Device;
import hu.tanszek.device.repository.DeviceRepository;
import hu.tanszek.device.location.entity.Location;
import hu.tanszek.device.location.repository.LocationRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
 * DeviceController — REST endpointok a Device entity CRUD-hoz.
 *
 * Permission-ök:
 * - DEVICE_READ: GET endpoints
 * - DEVICE_CREATE: POST
 * - DEVICE_UPDATE: PUT
 * - DEVICE_DELETE: DELETE
 */
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private static final int MAX_PAGE_SIZE = 50;

    private final DeviceRepository deviceRepository;
    private final LocationRepository locationRepository;

    /**
     * Device lista (lapozva + szűrve).
     * Query params: page, size, sort, filter[inventoryNumber], filter[status], filter[type]
     */
    @GetMapping
    @RequirePermission("DEVICE_READ")
    public ResponseEntity<Map<String, Object>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String inventoryNumber
    ) {
        if (size > MAX_PAGE_SIZE) {
            size = MAX_PAGE_SIZE;
        }
        if (size < 1) {
            size = 1;
        }
        if (page < 0) {
            page = 0;
        }

        // Egyszerűsített szűrés — production-ben a JpaSpecificationExecutor-t használnánk
        Page<Device> result;
        var pageable = PageRequest.of(page, size, Sort.by(sort).ascending());

        if (inventoryNumber != null && !inventoryNumber.isBlank()) {
            result = deviceRepository.findAll(
                    (root, query, cb) -> cb.equal(root.get("inventoryNumber"), inventoryNumber),
                    pageable
            );
        } else if (status != null && !status.isBlank()) {
            result = deviceRepository.findByStatus(
                    hu.tanszek.device.device.entity.DeviceStatus.valueOf(status),
                    pageable
            );
        } else {
            result = deviceRepository.findAll(pageable);
        }

        return ResponseEntity.ok(Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "size", result.getSize(),
                "number", result.getNumber()
        ));
    }

    /**
     * Device keresése ID alapján.
     */
    @GetMapping("/{id}")
    @RequirePermission("DEVICE_READ")
    public ResponseEntity<Device> findById(@PathVariable Long id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + id));
        return ResponseEntity.ok(device);
    }

    /**
     * Új device létrehozása.
     */
    @PostMapping
    @RequirePermission("DEVICE_CREATE")
    public ResponseEntity<Device> create(@Valid @RequestBody CreateDeviceRequest request) {
        // Duplikátum check
        deviceRepository.findByInventoryNumber(request.inventoryNumber()).ifPresent(d -> {
            throw new BusinessValidationException(
                    "deviceInventoryNumberDuplicate",
                    "Inventory number already exists: " + request.inventoryNumber()
            );
        });

        Device device = Device.builder()
                .type(request.type())
                .inventoryNumber(request.inventoryNumber())
                .status(hu.tanszek.device.device.entity.DeviceStatus.valueOf(request.status()))
                .build();

        Device saved = deviceRepository.save(device);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Device módosítása.
     */
    @PutMapping("/{id}")
    @RequirePermission("DEVICE_UPDATE")
    public ResponseEntity<Device> update(@PathVariable Long id, @Valid @RequestBody UpdateDeviceRequest request) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + id));

        if (request.type() != null) device.setType(request.type());
        if (request.status() != null) {
            device.setStatus(hu.tanszek.device.device.entity.DeviceStatus.valueOf(request.status()));
        }

        Device saved = deviceRepository.save(device);
        return ResponseEntity.ok(saved);
    }

    /**
     * Device törlése.
     */
    @DeleteMapping("/{id}")
    @RequirePermission("DEVICE_DELETE")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!deviceRepository.existsById(id)) {
            throw new ResourceNotFoundException("Device not found: " + id);
        }
        deviceRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ===== DTO-k =====

    public record CreateDeviceRequest(
            @NotBlank @Size(max = 50) @Pattern(regexp = "[a-zA-Z0-9-_]+")
            String inventoryNumber,

            @NotBlank @Size(max = 50)
            String type,

            @NotBlank
            String status
    ) {}

    public record UpdateDeviceRequest(
            @Size(max = 50)
            String type,

            String status
    ) {}
}
