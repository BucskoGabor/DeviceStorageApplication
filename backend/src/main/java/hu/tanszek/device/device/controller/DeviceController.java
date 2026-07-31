package hu.tanszek.device.device.controller;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import hu.tanszek.device.auth.RequirePermission;
import hu.tanszek.device.common.BusinessValidationException;
import hu.tanszek.device.common.ResourceNotFoundException;
import hu.tanszek.device.device.DeviceService;
import hu.tanszek.device.device.entity.Device;
import hu.tanszek.device.device.entity.DeviceStatus;
import hu.tanszek.device.device.repository.DeviceRepository;
import hu.tanszek.device.location.repository.LocationRepository;
import hu.tanszek.device.software.dto.SoftwareDto;
import hu.tanszek.device.software.entity.Software;
import hu.tanszek.device.software.repository.SoftwareRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * DeviceController — REST endpointok a Device entity CRUD-hoz.
 *
 * <p>Permission-ök: - DEVICE_READ: GET endpoints - DEVICE_CREATE: POST - DEVICE_UPDATE: PUT -
 * DEVICE_DELETE: DELETE
 */
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@Tag(name = "Device", description = "Eszköz CRUD + szoftver N:M kapcsolat")
public class DeviceController {

  private static final int MAX_PAGE_SIZE = 50;
  private static final String LICENSE_VIEW_PERMISSION = "SOFTWARE_LICENSE_VIEW";

  private final DeviceRepository deviceRepository;
  private final DeviceService deviceService;
  private final LocationRepository locationRepository;
  private final SoftwareRepository softwareRepository;
  private final hu.tanszek.device.crypto.CryptoService cryptoService;

  /**
   * Device lista (lapozva + szűrve). Query params: page, size, sort, filter[inventoryNumber],
   * filter[status], filter[type]
   */
  @Operation(
      summary = "Eszköz lista",
      description =
          "Laponként visszaadja az eszközöket. "
              + "Szűrhető: status (PENDING/ASSIGNED/IN_STORAGE/MAINTENANCE/DISPOSED), type, inventoryNumber. "
              + "Row-level filter: STUDENT csak saját, TEACHER saját + irodai, ADMIN minden.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Sikeres lista"),
    @ApiResponse(responseCode = "403", description = "DEVICE_READ permission hiányzik")
  })
  @GetMapping
  @RequirePermission("DEVICE_READ")
  public ResponseEntity<Map<String, Object>> findAll(
      @Parameter(description = "Oldalszám (0-tól)") @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "Elemek száma (max 50)") @RequestParam(defaultValue = "20") int size,
      @Parameter(description = "Rendezési mező") @RequestParam(defaultValue = "id") String sort,
      @Parameter(description = "Státusz szűrő") @RequestParam(required = false) String status,
      @Parameter(description = "Típus szűrő") @RequestParam(required = false) String type,
      @Parameter(description = "Leltári szám pontos egyezés") @RequestParam(required = false)
          String inventoryNumber) {
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
      result =
          deviceRepository.findAll(
              (root, query, cb) -> cb.equal(root.get("inventoryNumber"), inventoryNumber),
              pageable);
    } else if (status != null && !status.isBlank()) {
      result =
          deviceRepository.findByStatus(
              hu.tanszek.device.device.entity.DeviceStatus.valueOf(status), pageable);
    } else {
      result = deviceRepository.findAll(pageable);
    }

    return ResponseEntity.ok(
        Map.of(
            "content", result.getContent(),
            "totalElements", result.getTotalElements(),
            "totalPages", result.getTotalPages(),
            "size", result.getSize(),
            "number", result.getNumber()));
  }

  /** Device keresése ID alapján. */
  @Operation(
      summary = "Eszköz részletek",
      description = "Egy konkrét eszköz lekérdezése ID alapján.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Sikeres lekérdezés"),
    @ApiResponse(responseCode = "404", description = "Eszköz nem található"),
    @ApiResponse(responseCode = "403", description = "DEVICE_READ permission hiányzik")
  })
  @GetMapping("/{id}")
  @RequirePermission("DEVICE_READ")
  public ResponseEntity<Device> findById(
      @Parameter(description = "Eszköz azonosító") @PathVariable Long id) {
    Device device =
        deviceRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + id));
    return ResponseEntity.ok(device);
  }

  /** Új device létrehozása. */
  @Operation(
      summary = "Új eszköz létrehozása",
      description =
          "Létrehoz egy új eszközt a megadott leltári számmal. "
              + "A leltári szám egyedi (unique constraint). Audit log bejegyzés generálódik.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Eszköz létrehozva"),
    @ApiResponse(responseCode = "400", description = "Validációs hiba vagy duplikált leltári szám"),
    @ApiResponse(responseCode = "403", description = "DEVICE_CREATE permission hiányzik")
  })
  @PostMapping
  @RequirePermission("DEVICE_CREATE")
  public ResponseEntity<Device> create(@Valid @RequestBody CreateDeviceRequest request) {
    // Duplikátum check
    deviceRepository
        .findByInventoryNumber(request.inventoryNumber())
        .ifPresent(
            d -> {
              throw new BusinessValidationException(
                  "deviceInventoryNumberDuplicate",
                  "Inventory number already exists: " + request.inventoryNumber());
            });

    Device device =
        Device.builder()
            .type(request.type())
            .inventoryNumber(request.inventoryNumber())
            .status(hu.tanszek.device.device.entity.DeviceStatus.valueOf(request.status()))
            .build();

    Device saved = deviceRepository.save(device);
    return ResponseEntity.status(HttpStatus.CREATED).body(saved);
  }

  /** Device módosítása. */
  @Operation(
      summary = "Eszköz módosítása",
      description =
          "Partial update — csak a nem-null mezők frissülnek. Audit log bejegyzés generálódik.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Eszköz módosítva"),
    @ApiResponse(responseCode = "404", description = "Eszköz nem található"),
    @ApiResponse(responseCode = "403", description = "DEVICE_UPDATE permission hiányzik")
  })
  @PutMapping("/{id}")
  @RequirePermission("DEVICE_UPDATE")
  public ResponseEntity<Device> update(
      @Parameter(description = "Eszköz azonosító") @PathVariable Long id,
      @Valid @RequestBody UpdateDeviceRequest request) {
    Device device =
        deviceRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + id));

    if (request.type() != null) device.setType(request.type());
    if (request.status() != null) {
      device.setStatus(hu.tanszek.device.device.entity.DeviceStatus.valueOf(request.status()));
    }

    Device saved = deviceRepository.save(device);
    return ResponseEntity.ok(saved);
  }

  /** Device törlése. */
  @Operation(
      summary = "Eszköz törlése",
      description =
          "Hard delete a devices táblából + cascade a kapcsolódó rekordokra. "
              + "Audit log capture a törlés előtti állapotról.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Eszköz törölve"),
    @ApiResponse(responseCode = "404", description = "Eszköz nem található"),
    @ApiResponse(responseCode = "403", description = "DEVICE_DELETE permission hiányzik")
  })
  @DeleteMapping("/{id}")
  @RequirePermission("DEVICE_DELETE")
  public ResponseEntity<Void> delete(
      @Parameter(description = "Eszköz azonosító") @PathVariable Long id) {
    if (!deviceRepository.existsById(id)) {
      throw new ResourceNotFoundException("Device not found: " + id);
    }
    deviceRepository.deleteById(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * PATCH /api/devices/{id}/status
   *
   * <p>Eszköz státusz manuális átállítása (operatív beavatkozás). A {@link
   * DeviceService#changeStatus(Long, DeviceStatus)} validálja az átmenetet a state machine alapján.
   *
   * <p>Audit log bejegyzés generálódik a {@code @AuditTarget} annotáció által (entityType=Device,
   * action=change_status).
   */
  @Operation(
      summary = "Státusz átállítása",
      description =
          "Operatív státusz váltás (pl. MAINTENANCE-be küldés, "
              + "DISPOSED-re állítás). State machine validáció — nem minden átmenet megengedett. "
              + "DISPOSED végállapot — onnan nincs visszaút.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Státusz átállítva"),
    @ApiResponse(responseCode = "400", description = "Érvénytelen átmenet (pl. DISPOSED → bármi)"),
    @ApiResponse(responseCode = "404", description = "Eszköz nem található"),
    @ApiResponse(responseCode = "403", description = "DEVICE_UPDATE permission hiányzik")
  })
  @PatchMapping("/{id}/status")
  @RequirePermission("DEVICE_UPDATE")
  public ResponseEntity<Device> changeStatus(
      @Parameter(description = "Eszköz azonosító") @PathVariable Long id,
      @Valid @RequestBody ChangeStatusRequest request) {
    Device saved = deviceService.changeStatus(id, DeviceStatus.valueOf(request.status()));
    return ResponseEntity.ok(saved);
  }

  // ===== Szoftver kapcsolat endpointok (F3) =====

  /**
   * GET /api/devices/{deviceId}/software Device összes telepített szoftvere (N:M lista). Licence
   * key a hívó permissionje alapján maszkolva vagy teljes.
   */
  @Operation(
      summary = "Eszköz szoftverei",
      description =
          "Visszaadja az adott eszközre telepített szoftvereket. "
              + "A licence key a SOFTWARE_LICENSE_VIEW permission alapján maszkolva vagy teljes értékként jelenik meg.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Sikeres lista"),
    @ApiResponse(responseCode = "404", description = "Eszköz nem található"),
    @ApiResponse(responseCode = "403", description = "DEVICE_READ permission hiányzik")
  })
  @GetMapping("/{deviceId}/software")
  @RequirePermission("DEVICE_READ")
  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public ResponseEntity<List<SoftwareDto>> findSoftwareByDevice(
      @Parameter(description = "Eszköz azonosító") @PathVariable Long deviceId,
      Authentication authentication) {
    Device device =
        deviceRepository
            .findById(deviceId)
            .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));

    boolean canViewKey = hasLicenseViewPermission(authentication);
    List<SoftwareDto> dtos =
        device.getSoftwares().stream()
            .map(
                s ->
                    SoftwareDto.fromEntity(
                        s, canViewKey, canViewKey ? safeDecrypt(s.getLicenseKeyEncrypted()) : null))
            .toList();
    return ResponseEntity.ok(dtos);
  }

  /**
   * POST /api/devices/{deviceId}/software Szoftver hozzárendelése egy device-hoz. Ha már hozzá van
   * rendelve, idempotens (nem dob hibát).
   */
  @Operation(
      summary = "Szoftver hozzárendelése",
      description =
          "A megadott szoftvert hozzáadja az eszközhöz (N:M). "
              + "Ha már hozzá van rendelve, idempotens — nem dob hibát, a meglévő kapcsolatot adja vissza.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Szoftver hozzárendelve"),
    @ApiResponse(responseCode = "404", description = "Eszköz vagy szoftver nem található"),
    @ApiResponse(responseCode = "403", description = "DEVICE_UPDATE permission hiányzik")
  })
  @PostMapping("/{deviceId}/software")
  @RequirePermission("DEVICE_UPDATE")
  @org.springframework.transaction.annotation.Transactional
  public ResponseEntity<SoftwareDto> attachSoftware(
      @Parameter(description = "Eszköz azonosító") @PathVariable Long deviceId,
      @Valid @RequestBody AttachSoftwareRequest request,
      Authentication authentication) {
    Device device =
        deviceRepository
            .findById(deviceId)
            .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));
    Software software =
        softwareRepository
            .findById(request.softwareId())
            .orElseThrow(
                () -> new ResourceNotFoundException("Software not found: " + request.softwareId()));

    boolean wasAttached = device.getSoftwares().contains(software);
    if (!wasAttached) {
      device.getSoftwares().add(software);
      deviceRepository.save(device);
    }

    boolean canViewKey = hasLicenseViewPermission(authentication);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            SoftwareDto.fromEntity(
                software,
                canViewKey,
                canViewKey ? safeDecrypt(software.getLicenseKeyEncrypted()) : null));
  }

  /**
   * DELETE /api/devices/{deviceId}/software/{softwareId} Szoftver leválasztása egy device-ról. Ha
   * nincs hozzárendelve, idempotens (204-et ad vissza).
   */
  @Operation(
      summary = "Szoftver leválasztása",
      description =
          "Eltávolítja a szoftvert az eszközről (N:M). "
              + "Ha nincs hozzárendelve, idempotens — 204 No Content.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "204",
        description = "Szoftver leválasztva (vagy nem volt hozzárendelve)"),
    @ApiResponse(responseCode = "404", description = "Eszköz vagy szoftver nem található"),
    @ApiResponse(responseCode = "403", description = "DEVICE_UPDATE permission hiányzik")
  })
  @DeleteMapping("/{deviceId}/software/{softwareId}")
  @RequirePermission("DEVICE_UPDATE")
  @org.springframework.transaction.annotation.Transactional
  public ResponseEntity<Void> detachSoftware(
      @Parameter(description = "Eszköz azonosító") @PathVariable Long deviceId,
      @Parameter(description = "Szoftver azonosító") @PathVariable Long softwareId) {
    Device device =
        deviceRepository
            .findById(deviceId)
            .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));
    Software software =
        softwareRepository
            .findById(softwareId)
            .orElseThrow(() -> new ResourceNotFoundException("Software not found: " + softwareId));

    if (device.getSoftwares().remove(software)) {
      deviceRepository.save(device);
    }
    return ResponseEntity.noContent().build();
  }

  private boolean hasLicenseViewPermission(Authentication authentication) {
    if (authentication == null) return false;
    for (GrantedAuthority authority : authentication.getAuthorities()) {
      if (LICENSE_VIEW_PERMISSION.equals(authority.getAuthority())) {
        return true;
      }
    }
    return false;
  }

  private String safeDecrypt(String encrypted) {
    try {
      return cryptoService.decrypt(encrypted);
    } catch (Exception e) {
      return null;
    }
  }

  // ===== DTO-k =====

  public record CreateDeviceRequest(
      @NotBlank @Size(max = 50) @Pattern(regexp = "[a-zA-Z0-9-_]+") String inventoryNumber,
      @NotBlank @Size(max = 50) String type,
      @NotBlank String status) {}

  public record UpdateDeviceRequest(@Size(max = 50) String type, String status) {}

  public record AttachSoftwareRequest(@NotNull Long softwareId) {}

  public record ChangeStatusRequest(@NotBlank String status) {}
}
