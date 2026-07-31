package hu.tanszek.device.assignment.controller;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import hu.tanszek.device.assignment.entity.DeviceAssignment;
import hu.tanszek.device.assignment.repository.DeviceAssignmentRepository;
import hu.tanszek.device.auth.RequirePermission;
import hu.tanszek.device.common.ResourceNotFoundException;
import hu.tanszek.device.device.DeviceService;
import hu.tanszek.device.device.repository.DeviceRepository;
import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/**
 * AssignmentController — REST endpointok a DeviceAssignment state machine műveleteihez.
 *
 * <p>State machine:
 *
 * <pre>
 *   IN_STORAGE → PENDING_ASSIGNMENT (assign kérés)
 *             → ASSIGNED           (approve)
 *             → PENDING_UNASSIGNMENT (unassign kérés)
 *             → IN_STORAGE         (approve unassign)
 * </pre>
 *
 * <p>Permissionek:
 *
 * <ul>
 *   <li>DEVICE_ASSIGN: assign kérés + approve + history olvasás
 *   <li>DEVICE_UNASSIGN: unassign kérés + approve
 *   <li>AUDIT_READ: pending queue (jóváhagyásra váró lista)
 * </ul>
 */
@RestController
@RequiredArgsConstructor
@Tag(
    name = "Assignment",
    description =
        "Eszköz hozzárendelés state machine: IN_STORAGE → PENDING_ASSIGNMENT → ASSIGNED → PENDING_UNASSIGNMENT → IN_STORAGE")
public class AssignmentController {

  private static final int MAX_PAGE_SIZE = 50;

  private final DeviceService deviceService;
  private final DeviceAssignmentRepository assignmentRepository;
  private final DeviceRepository deviceRepository;
  private final AppUserRepository userRepository;

  /** POST /api/devices/{deviceId}/assignments Assign kérés létrehozása (PENDING_ASSIGNMENT). */
  @Operation(
      summary = "Hozzárendelés kérése",
      description =
          "Létrehoz egy PENDING_ASSIGNMENT státuszú rekordot. "
              + "Üzleti szabályok: device NEM lehet MAINTENANCE/DISPOSED; location NEM lehet GROUP típusú. "
              + "Audit log bejegyzés generálódik.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Hozzárendelési kérés létrehozva"),
    @ApiResponse(
        responseCode = "400",
        description =
            "Validációs hiba vagy üzleti szabály megszegése (GROUP location, MAINTENANCE/DISPOSED device)"),
    @ApiResponse(
        responseCode = "404",
        description = "Eszköz, helyszín vagy felhasználó nem található"),
    @ApiResponse(responseCode = "403", description = "DEVICE_ASSIGN permission hiányzik")
  })
  @PostMapping("/api/devices/{deviceId}/assignments")
  @RequirePermission("DEVICE_ASSIGN")
  public ResponseEntity<DeviceAssignment> requestAssignment(
      @Parameter(description = "Eszköz azonosító") @PathVariable Long deviceId,
      @Valid @RequestBody CreateAssignmentRequest request,
      Authentication authentication) {
    Long byUserId = resolveUserId(authentication);
    DeviceAssignment saved =
        deviceService.requestAssignment(
            deviceId, request.targetLocationId(), request.targetUserId(), byUserId);
    return ResponseEntity.status(HttpStatus.CREATED).body(saved);
  }

  /**
   * POST /api/devices/assignments/{assignmentId}/approve PENDING_ASSIGNMENT → ASSIGNED átmenet
   * jóváhagyása.
   */
  @Operation(
      summary = "Hozzárendelés jóváhagyása",
      description =
          "PENDING_ASSIGNMENT → ASSIGNED átmenet. "
              + "A device státusza ASSIGNED-re vált. Audit log bejegyzés generálódik.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Hozzárendelés jóváhagyva"),
    @ApiResponse(
        responseCode = "400",
        description = "Az assignment nem PENDING_ASSIGNMENT státuszú"),
    @ApiResponse(responseCode = "404", description = "Assignment nem található"),
    @ApiResponse(responseCode = "403", description = "DEVICE_ASSIGN permission hiányzik")
  })
  @PostMapping("/api/devices/assignments/{assignmentId}/approve")
  @RequirePermission("DEVICE_ASSIGN")
  public ResponseEntity<DeviceAssignment> approveAssignment(
      @Parameter(description = "Assignment azonosító") @PathVariable Long assignmentId,
      Authentication authentication) {
    Long approvedByUserId = resolveUserId(authentication);
    DeviceAssignment approved = deviceService.approveAssignment(assignmentId, approvedByUserId);
    return ResponseEntity.ok(approved);
  }

  /**
   * POST /api/devices/assignments/{assignmentId}/unassign Aktív assignment visszavételi kérése
   * (PENDING_UNASSIGNMENT).
   */
  @Operation(
      summary = "Visszavétel kérése",
      description =
          "Aktív ASSIGNED assignment → PENDING_UNASSIGNMENT átmenet kérése. "
              + "Az eredeti rekord inaktívvá válik, új PENDING_UNASSIGNMENT rekord jön létre. Audit log bejegyzés generálódik.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Visszavételi kérés létrehozva"),
    @ApiResponse(responseCode = "400", description = "Az assignment nem aktív"),
    @ApiResponse(responseCode = "404", description = "Assignment nem található"),
    @ApiResponse(responseCode = "403", description = "DEVICE_UNASSIGN permission hiányzik")
  })
  @PostMapping("/api/devices/assignments/{assignmentId}/unassign")
  @RequirePermission("DEVICE_UNASSIGN")
  public ResponseEntity<DeviceAssignment> requestUnassignment(
      @Parameter(description = "Aktív assignment azonosító") @PathVariable Long assignmentId,
      Authentication authentication) {
    Long byUserId = resolveUserId(authentication);
    DeviceAssignment unassignRequest = deviceService.requestUnassignment(assignmentId, byUserId);
    return ResponseEntity.status(HttpStatus.CREATED).body(unassignRequest);
  }

  /**
   * POST /api/devices/assignments/{unassignmentId}/approve-unassign PENDING_UNASSIGNMENT →
   * IN_STORAGE átmenet jóváhagyása.
   */
  @Operation(
      summary = "Visszavétel jóváhagyása",
      description =
          "PENDING_UNASSIGNMENT → IN_STORAGE átmenet. "
              + "A device státusza IN_STORAGE-ra vált. Audit log bejegyzés generálódik.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Visszavétel jóváhagyva"),
    @ApiResponse(
        responseCode = "400",
        description = "Az assignment nem PENDING_UNASSIGNMENT státuszú"),
    @ApiResponse(responseCode = "404", description = "Assignment nem található"),
    @ApiResponse(responseCode = "403", description = "DEVICE_UNASSIGN permission hiányzik")
  })
  @PostMapping("/api/devices/assignments/{unassignmentId}/approve-unassign")
  @RequirePermission("DEVICE_UNASSIGN")
  public ResponseEntity<DeviceAssignment> approveUnassignment(
      @Parameter(description = "PENDING_UNASSIGNMENT assignment azonosító") @PathVariable
          Long unassignmentId,
      Authentication authentication) {
    Long approvedByUserId = resolveUserId(authentication);
    DeviceAssignment approved = deviceService.approveUnassignment(unassignmentId, approvedByUserId);
    return ResponseEntity.ok(approved);
  }

  /**
   * GET /api/devices/{deviceId}/assignments Egy device összes assignment history-ja (lapozva,
   * createdDate desc).
   */
  @Operation(
      summary = "Eszköz assignment history",
      description =
          "Visszaadja az eszköz összes assignment rekordját "
              + "(createdDate descending), beleértve a lezárt és függőben lévő kérelmeket is.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Sikeres lista"),
    @ApiResponse(responseCode = "404", description = "Eszköz nem található"),
    @ApiResponse(responseCode = "403", description = "DEVICE_READ permission hiányzik")
  })
  @GetMapping("/api/devices/{deviceId}/assignments")
  @RequirePermission("DEVICE_READ")
  public ResponseEntity<Map<String, Object>> findByDevice(
      @Parameter(description = "Eszköz azonosító") @PathVariable Long deviceId,
      @Parameter(description = "Oldalszám (0-tól)") @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "Elemek száma (max 50)") @RequestParam(defaultValue = "20")
          int size) {
    if (!deviceRepository.existsById(deviceId)) {
      throw new ResourceNotFoundException("Device not found: " + deviceId);
    }
    if (size > MAX_PAGE_SIZE) size = MAX_PAGE_SIZE;
    if (size < 1) size = 1;
    if (page < 0) page = 0;

    Page<DeviceAssignment> result =
        assignmentRepository.findAll(
            (root, query, cb) -> cb.equal(root.get("device").get("id"), deviceId),
            PageRequest.of(page, size, Sort.by("createdDate").descending()));

    return ResponseEntity.ok(
        Map.of(
            "content", result.getContent(),
            "totalElements", result.getTotalElements(),
            "totalPages", result.getTotalPages(),
            "size", result.getSize(),
            "number", result.getNumber()));
  }

  /**
   * GET /api/assignments/pending Jóváhagyásra váró assignment-ek listája (PENDING_ASSIGNMENT +
   * PENDING_UNASSIGNMENT). DEVICE_ASSIGN permission — admin/teacher (akik assign-t is tudnak
   * kezdeményezni) látják.
   */
  @Operation(
      summary = "Jóváhagyási sor",
      description =
          "Visszaadja az összes PENDING_ASSIGNMENT és PENDING_UNASSIGNMENT "
              + "státuszú assignment rekordot (createdDate asc — legrégebbi elöl).")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Sikeres lista"),
    @ApiResponse(responseCode = "403", description = "DEVICE_ASSIGN permission hiányzik")
  })
  @GetMapping("/api/assignments/pending")
  @RequirePermission("DEVICE_ASSIGN")
  public ResponseEntity<List<DeviceAssignment>> findPending() {
    List<DeviceAssignment> pending =
        assignmentRepository.findAll(
            (root, query, cb) ->
                root.get("status")
                    .in(
                        hu.tanszek.device.assignment.entity.AssignmentStatus.PENDING_ASSIGNMENT,
                        hu.tanszek.device.assignment.entity.AssignmentStatus.PENDING_UNASSIGNMENT),
            Sort.by("createdDate").ascending());
    return ResponseEntity.ok(pending);
  }

  private Long resolveUserId(Authentication authentication) {
    String emailHash = authentication.getName();
    AppUser user =
        userRepository
            .findByEmailHash(emailHash)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + emailHash));
    return user.getId();
  }

  public record CreateAssignmentRequest(@NotNull Long targetLocationId, Long targetUserId) {}
}
