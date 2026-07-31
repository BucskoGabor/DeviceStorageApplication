package hu.tanszek.device.software.controller;

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
import hu.tanszek.device.common.ResourceNotFoundException;
import hu.tanszek.device.crypto.CryptoService;
import hu.tanszek.device.device.entity.Device;
import hu.tanszek.device.device.repository.DeviceRepository;
import hu.tanszek.device.software.SoftwareService;
import hu.tanszek.device.software.dto.SoftwareDto;
import hu.tanszek.device.software.entity.Software;
import hu.tanszek.device.software.repository.SoftwareRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * SoftwareController — REST endpointok a szoftverek és licencek CRUD műveleteihez.
 *
 * <p>A módosító műveletek (create, update, delete) a {@link SoftwareService}-en keresztül futnak,
 * így az AOP audit interceptor (lásd {@code AuditAspect}) minden tranzakciót automatikusan naplóz.
 *
 * <p>Licence key maszkolás (F3):
 *
 * <ul>
 *   <li>SOFTWARE_LICENSE_VIEW permission: {@code licenseKey} mező tartalmazza a visszafejtett
 *       értéket
 *   <li>egyébként: {@code licenseKeyMasked} mező tartalmazza a maszkolt formátumot ({@code
 *       ****-****-****-<utolsó 4 karakter>}) — az encrypted blob-ból
 *   <li>A két mező közül pontosan az egyik kitöltött (defense-in-depth a DTO szintjén)
 * </ul>
 */
@RestController
@RequestMapping("/api/software")
@RequiredArgsConstructor
@Tag(
    name = "Software",
    description = "Szoftver és licence key CRUD, valamint N:M kapcsolat eszközökhöz")
public class SoftwareController {

  private static final int MAX_PAGE_SIZE = 50;
  private static final String LICENSE_VIEW_PERMISSION = "SOFTWARE_LICENSE_VIEW";

  private final SoftwareRepository softwareRepository;
  private final SoftwareService softwareService;
  private final DeviceRepository deviceRepository;
  private final CryptoService cryptoService;

  /**
   * Szoftver lista (lapozva). Licence key a hívó permissionje alapján maszkolva vagy teljes
   * értékként jelenik meg.
   */
  @Operation(
      summary = "Szoftver lista",
      description =
          "Laponként visszaadja a szoftvereket. "
              + "A licence key a hívó SOFTWARE_LICENSE_VIEW permissionje alapján maszkolva vagy teljes értékként jelenik meg.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Sikeres lista"),
    @ApiResponse(responseCode = "401", description = "Hitelesítés szükséges"),
    @ApiResponse(responseCode = "403", description = "SOFTWARE_LICENSE_VIEW permission hiányzik")
  })
  @GetMapping
  @RequirePermission("SOFTWARE_LICENSE_VIEW")
  public ResponseEntity<Map<String, Object>> findAll(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Authentication authentication) {
    if (size > MAX_PAGE_SIZE) size = MAX_PAGE_SIZE;
    if (size < 1) size = 1;
    if (page < 0) page = 0;

    var pageable = PageRequest.of(page, size, Sort.by("name").ascending());
    Page<Software> result = softwareRepository.findAll(pageable);

    boolean canViewKey = hasLicenseViewPermission(authentication);

    List<SoftwareDto> content =
        result.getContent().stream()
            .map(
                s ->
                    SoftwareDto.fromEntity(
                        s, canViewKey, canViewKey ? safeDecrypt(s.getLicenseKeyEncrypted()) : null))
            .toList();

    return ResponseEntity.ok(
        Map.of(
            "content", content,
            "totalElements", result.getTotalElements(),
            "totalPages", result.getTotalPages(),
            "size", result.getSize(),
            "number", result.getNumber()));
  }

  /** Szoftver létrehozása. A licence key titkosítva kerül DB-be. */
  @Operation(
      summary = "Új szoftver létrehozása",
      description = "A licence key AES-GCM 256 titkosítással kerül tárolásra.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Szoftver létrehozva"),
    @ApiResponse(responseCode = "400", description = "Validációs hiba"),
    @ApiResponse(responseCode = "403", description = "SOFTWARE_MANAGE permission hiányzik")
  })
  @PostMapping
  @RequirePermission("SOFTWARE_MANAGE")
  public ResponseEntity<SoftwareDto> create(
      @Valid @RequestBody CreateSoftwareRequest request, Authentication authentication) {
    boolean canViewKey = hasLicenseViewPermission(authentication);

    Software saved = softwareService.create(request.name(), request.licenseKey());

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(SoftwareDto.fromEntity(saved, canViewKey, canViewKey ? request.licenseKey() : null));
  }

  /**
   * Szoftver módosítása (F6). Csak a megadott mezők frissülnek. Ha a {@code licenseKey}
   * megváltozik, újra titkosítjuk.
   */
  @Operation(
      summary = "Szoftver módosítása",
      description =
          "A name és/vagy licenseKey mezők frissíthetők. "
              + "Csak a nem-null mezők frissülnek (partial update). Audit log bejegyzés generálódik.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Szoftver módosítva"),
    @ApiResponse(responseCode = "400", description = "Validációs hiba"),
    @ApiResponse(responseCode = "404", description = "Szoftver nem található"),
    @ApiResponse(responseCode = "403", description = "SOFTWARE_MANAGE permission hiányzik")
  })
  @PutMapping("/{id}")
  @RequirePermission("SOFTWARE_MANAGE")
  public ResponseEntity<SoftwareDto> update(
      @PathVariable Long id,
      @Valid @RequestBody UpdateSoftwareRequest request,
      Authentication authentication) {
    boolean canViewKey = hasLicenseViewPermission(authentication);

    Software saved = softwareService.update(id, request.name(), request.licenseKey());

    String decryptedKey = null;
    if (canViewKey && request.licenseKey() != null && !request.licenseKey().isBlank()) {
      decryptedKey = request.licenseKey();
    } else if (canViewKey) {
      decryptedKey = safeDecrypt(saved.getLicenseKeyEncrypted());
    }

    return ResponseEntity.ok(SoftwareDto.fromEntity(saved, canViewKey, decryptedKey));
  }

  /** Szoftver törlése. */
  @Operation(summary = "Szoftver törlése")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Szoftver törölve"),
    @ApiResponse(responseCode = "404", description = "Szoftver nem található"),
    @ApiResponse(responseCode = "403", description = "SOFTWARE_MANAGE permission hiányzik")
  })
  @DeleteMapping("/{id}")
  @RequirePermission("SOFTWARE_MANAGE")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    softwareService.delete(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Azon eszközök listája, amelyekre ez a szoftver telepítve van.
   *
   * <p>A Software entitás nem tartalmaz visszafelé mutató {@code devices} kollekciót, ezért ez a
   * végpont a {@code device_softwares} join táblán keresztül, a {@code
   * DeviceRepository.findDevicesBySoftwareId} JPQL lekérdezéssel dolgozik.
   */
  @Operation(
      summary = "Szoftverhez tartozó eszközök",
      description =
          "Visszaadja az összes eszközt, "
              + "amelyre az adott szoftver telepítve van (N:M kapcsolat a device_softwares join táblán).")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Sikeres lista"),
    @ApiResponse(responseCode = "404", description = "Szoftver nem található"),
    @ApiResponse(responseCode = "403", description = "SOFTWARE_LICENSE_VIEW permission hiányzik")
  })
  @GetMapping("/{id}/devices")
  @RequirePermission("SOFTWARE_LICENSE_VIEW")
  public ResponseEntity<List<Device>> findDevicesBySoftware(@PathVariable Long id) {
    if (!softwareRepository.existsById(id)) {
      throw new ResourceNotFoundException("Software not found: " + id);
    }
    return ResponseEntity.ok(deviceRepository.findDevicesBySoftwareId(id));
  }

  /** Megnézi, hogy a hívó user rendelkezik-e a SOFTWARE_LICENSE_VIEW permissionnel. */
  private boolean hasLicenseViewPermission(Authentication authentication) {
    if (authentication == null) return false;
    for (GrantedAuthority authority : authentication.getAuthorities()) {
      if (LICENSE_VIEW_PERMISSION.equals(authority.getAuthority())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Biztonságos decrypt — ha bármi hiba történik (sérült blob), null-t ad vissza ahelyett, hogy
   * 500-as hibát dobna a teljes listázás során.
   */
  private String safeDecrypt(String encrypted) {
    try {
      return cryptoService.decrypt(encrypted);
    } catch (Exception e) {
      return null;
    }
  }

  public record CreateSoftwareRequest(
      @NotBlank @Size(max = 255) String name, @NotBlank String licenseKey) {}

  public record UpdateSoftwareRequest(@Size(max = 255) String name, String licenseKey) {}
}
