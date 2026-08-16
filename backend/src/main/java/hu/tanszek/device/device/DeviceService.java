package hu.tanszek.device.device;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hu.tanszek.device.assignment.entity.AssignmentStatus;
import hu.tanszek.device.assignment.entity.DeviceAssignment;
import hu.tanszek.device.assignment.repository.DeviceAssignmentRepository;
import hu.tanszek.device.attachment.AttachmentService;
import hu.tanszek.device.audit.AuditTarget;
import hu.tanszek.device.common.BusinessValidationException;
import hu.tanszek.device.common.ResourceNotFoundException;
import hu.tanszek.device.device.entity.Device;
import hu.tanszek.device.device.entity.DeviceStatus;
import hu.tanszek.device.device.repository.DeviceRepository;
import hu.tanszek.device.location.entity.Location;
import hu.tanszek.device.location.entity.LocationType;
import hu.tanszek.device.location.repository.LocationRepository;
import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * DeviceService — Device CRUD + assign/unassign business logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

  private static final Map<DeviceStatus, Set<DeviceStatus>> ALLOWED_TRANSITIONS =
      new EnumMap<>(DeviceStatus.class);

  static {
    ALLOWED_TRANSITIONS.put(
        DeviceStatus.PENDING,
        EnumSet.of(
            DeviceStatus.IN_STORAGE, DeviceStatus.PENDING_MAINTENANCE, DeviceStatus.MAINTENANCE));
    ALLOWED_TRANSITIONS.put(
        DeviceStatus.IN_STORAGE,
        EnumSet.of(
            DeviceStatus.PENDING_MAINTENANCE,
            DeviceStatus.MAINTENANCE,
            DeviceStatus.PENDING_DISPOSAL,
            DeviceStatus.DISPOSED));
    ALLOWED_TRANSITIONS.put(
        DeviceStatus.ASSIGNED,
        EnumSet.of(
            DeviceStatus.IN_STORAGE, DeviceStatus.PENDING_MAINTENANCE, DeviceStatus.MAINTENANCE));
    ALLOWED_TRANSITIONS.put(
        DeviceStatus.PENDING_MAINTENANCE,
        EnumSet.of(DeviceStatus.MAINTENANCE, DeviceStatus.IN_STORAGE, DeviceStatus.ASSIGNED));
    ALLOWED_TRANSITIONS.put(
        DeviceStatus.MAINTENANCE,
        EnumSet.of(DeviceStatus.IN_STORAGE, DeviceStatus.PENDING_DISPOSAL, DeviceStatus.DISPOSED));
    ALLOWED_TRANSITIONS.put(
        DeviceStatus.PENDING_DISPOSAL,
        EnumSet.of(DeviceStatus.DISPOSED, DeviceStatus.IN_STORAGE, DeviceStatus.MAINTENANCE));
    ALLOWED_TRANSITIONS.put(DeviceStatus.DISPOSED, EnumSet.noneOf(DeviceStatus.class));
  }

  private final DeviceRepository deviceRepository;
  private final DeviceAssignmentRepository assignmentRepository;
  private final LocationRepository locationRepository;
  private final AppUserRepository userRepository;
  private final AttachmentService attachmentService;

  /**
   * Assign kérés létrehozása — PENDING_ASSIGNMENT státusszal.
   *
   * <p>A régi aktív assignment (ha van) inaktívvá válik, az új PENDING_ASSIGNMENT státusszal jön
   * létre. Egy admin/user hívja az approveAssignment()-t a tényleges ASSIGNED-re váltáshoz.
   *
   * @param deviceId az eszköz ID-ja
   * @param targetLocationId a cél location ID-ja (vagy null = kiveszik location-ból)
   * @param targetUserId a cél user ID-ja (vagy null = kiveszik user-től)
   * @param byUserId a műveletet végző user ID-ja (authenticated)
   * @return az új DeviceAssignment (PENDING_ASSIGNMENT státusszal)
   * @throws ResourceNotFoundException ha device/location/user nem található
   * @throws BusinessValidationException ha device MAINTENANCE/DISPOSED, location GROUP, stb.
   */
  @AuditTarget(entityType = "DeviceAssignment", action = "assign")
  @Transactional
  public DeviceAssignment requestAssignment(
      Long deviceId, Long targetLocationId, Long targetUserId, Long byUserId) {
    Device device =
        deviceRepository
            .findById(deviceId)
            .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));

    // Üzleti szabály 1: Csak IN_STORAGE státuszú eszközt lehet hozzárendelni
    if (device.getStatus() != DeviceStatus.IN_STORAGE) {
      throw new BusinessValidationException(
          "deviceNotAssignable",
          "Only devices in IN_STORAGE status can be assigned. Current status: "
              + device.getStatus());
    }

    // Üzleti szabály 2: Vagy location, vagy user megadása kötelező, egyszerre mindkettő vagy egyik
    // sem tilos
    if ((targetLocationId == null && targetUserId == null)
        || (targetLocationId != null && targetUserId != null)) {
      throw new BusinessValidationException(
          "assignmentTargetExclusive",
          "Must specify either targetLocationId or targetUserId, but not both");
    }

    // Üzleti szabály 3: GROUP location-ra NEM lehet assignolni
    Location targetLocation = null;
    if (targetLocationId != null) {
      targetLocation =
          locationRepository
              .findById(targetLocationId)
              .orElseThrow(
                  () -> new ResourceNotFoundException("Location not found: " + targetLocationId));
      if (targetLocation.getType() == LocationType.GROUP) {
        throw new BusinessValidationException(
            "groupLocationNotAssignable", "GROUP locations are not assignable as targets");
      }
    }

    // Üzleti szabály 4: target user valid (ha megadva)
    AppUser targetUser = null;
    if (targetUserId != null) {
      targetUser =
          userRepository
              .findById(targetUserId)
              .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetUserId));
    }

    // Üzleti szabály 5: by user valid
    AppUser byUser =
        userRepository
            .findById(byUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + byUserId));

    // Régi aktív assignment lekérése a fromLocation / fromUser kitöltéséhez
    Optional<DeviceAssignment> oldAssignmentOpt =
        assignmentRepository.findFirstByDeviceIdAndStatus(deviceId, AssignmentStatus.ASSIGNED);
    Location fromLocation = oldAssignmentOpt.map(DeviceAssignment::getToLocation).orElse(null);
    AppUser fromUser = oldAssignmentOpt.map(DeviceAssignment::getToUser).orElse(null);

    oldAssignmentOpt.ifPresent(
        oldAssignment -> {
          oldAssignment.setStatus(AssignmentStatus.PENDING_UNASSIGNMENT);
          oldAssignment.setUnassignCreatedDate(Instant.now());
          assignmentRepository.save(oldAssignment);
        });

    // Új PENDING_ASSIGNMENT rekord létrehozása
    DeviceAssignment newAssignment =
        DeviceAssignment.builder()
            .device(device)
            .fromLocation(fromLocation)
            .toLocation(targetLocation)
            .fromUser(fromUser)
            .toUser(targetUser)
            .createdByUser(byUser)
            .approvedBy(null) // Még nincs approve-olva
            .status(AssignmentStatus.PENDING_ASSIGNMENT)
            .createdDate(Instant.now())
            .build();

    DeviceAssignment saved = assignmentRepository.save(newAssignment);

    log.info(
        "Assignment requested: device={}, target={}, by={}",
        deviceId,
        targetUserId != null ? targetUserId : targetLocationId,
        byUserId);
    return saved;
  }

  /**
   * Assign jóváhagyása — PENDING_ASSIGNMENT → ASSIGNED átmenet.
   *
   * <p>Csak a {@code byUser} (vagy admin) hívhatja. A device státuszát ASSIGNED-re állítja, és az
   * assignment active=true lesz.
   *
   * @param assignmentId az assignment ID-ja
   * @param approvedByUserId a jóváhagyó user ID-ja
   * @return a frissített assignment (active=true, ASSIGNED)
   */
  @AuditTarget(entityType = "DeviceAssignment", action = "approve_assign")
  @Transactional
  public DeviceAssignment approveAssignment(Long assignmentId, Long approvedByUserId) {
    DeviceAssignment assignment =
        assignmentRepository
            .findById(assignmentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Assignment not found: " + assignmentId));

    if (assignment.getStatus() != AssignmentStatus.PENDING_ASSIGNMENT) {
      throw new BusinessValidationException(
          "assignmentNotPending", "Assignment is not in PENDING_ASSIGNMENT status");
    }

    AppUser approver =
        userRepository
            .findById(approvedByUserId)
            .orElseThrow(
                () -> new ResourceNotFoundException("User not found: " + approvedByUserId));

    assignment.setStatus(AssignmentStatus.ASSIGNED);
    assignment.setApprovedBy(approver);
    assignment.setDateOfAssignment(Instant.now());

    // Device státusz és helyszín frissítése
    Device device = assignment.getDevice();
    device.setStatus(DeviceStatus.ASSIGNED);
    device.setCurrentLocation(assignment.getToLocation());
    deviceRepository.save(device);

    DeviceAssignment saved = assignmentRepository.save(assignment);

    log.info("Assignment approved: id={}, by={}", assignmentId, approvedByUserId);
    return saved;
  }

  /**
   * Unassign kérés létrehozása — PENDING_UNASSIGNMENT státusszal.
   *
   * @param assignmentId az aktív assignment ID-ja
   * @param byUserId a műveletet végző user ID-ja
   * @return az új PENDING_UNASSIGNMENT rekord (history-szerű)
   */
  @AuditTarget(entityType = "DeviceAssignment", action = "unassign")
  @Transactional
  public DeviceAssignment requestUnassignment(
      Long assignmentId, Long targetLocationId, Long byUserId) {
    DeviceAssignment assignment =
        assignmentRepository
            .findById(assignmentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Assignment not found: " + assignmentId));

    if (assignment.getStatus() != AssignmentStatus.ASSIGNED) {
      throw new BusinessValidationException(
          "assignmentNotActive", "Assignment not active, cannot unassigned");
    }

    Location targetLocation = null;
    if (targetLocationId != null) {
      targetLocation =
          locationRepository
              .findById(targetLocationId)
              .orElseThrow(
                  () -> new ResourceNotFoundException("Location not found: " + targetLocationId));
      if (targetLocation.getType() != LocationType.STORAGE) {
        throw new BusinessValidationException(
            "unassignTargetMustBeStorage", "Target location must be STORAGE type");
      }
    }

    AppUser byUser =
        userRepository
            .findById(byUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + byUserId));

    // Régi aktív inaktiválása
    assignment.setUnassignCreatedDate(Instant.now());
    assignmentRepository.save(assignment);

    // Új PENDING_UNASSIGNMENT rekord (history-szerű)
    DeviceAssignment unassignmentRequest =
        DeviceAssignment.builder()
            .device(assignment.getDevice())
            .fromLocation(assignment.getToLocation())
            .fromUser(assignment.getToUser())
            .toLocation(targetLocation)
            .toUser(null)
            .createdByUser(byUser)
            .approvedBy(null)
            .status(AssignmentStatus.PENDING_UNASSIGNMENT)
            .createdDate(Instant.now())
            .build();

    DeviceAssignment saved = assignmentRepository.save(unassignmentRequest);

    log.info(
        "Unassignment requested: assignment={}, targetLocation={}, by={}",
        assignmentId,
        targetLocationId,
        byUserId);
    return saved;
  }

  public DeviceAssignment requestUnassignment(Long assignmentId, Long byUserId) {
    return requestUnassignment(assignmentId, null, byUserId);
  }

  /**
   * Unassign jóváhagyása — PENDING_UNASSIGNMENT → IN_STORAGE átmenet.
   *
   * @param unassignmentId a PENDING_UNASSIGNMENT assignment ID-ja
   * @param approvedByUserId a jóváhagyó user ID-ja
   */
  @AuditTarget(entityType = "DeviceAssignment", action = "approve_unassign")
  @Transactional
  public DeviceAssignment approveUnassignment(Long unassignmentId, Long approvedByUserId) {
    DeviceAssignment unassignment =
        assignmentRepository
            .findById(unassignmentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Unassignment not found: " + unassignmentId));

    if (unassignment.getStatus() != AssignmentStatus.PENDING_UNASSIGNMENT) {
      throw new BusinessValidationException(
          "unassignmentNotPending", "Unassignment is not in PENDING_UNASSIGNMENT status");
    }

    AppUser approver =
        userRepository
            .findById(approvedByUserId)
            .orElseThrow(
                () -> new ResourceNotFoundException("User not found: " + approvedByUserId));

    unassignment.setStatus(AssignmentStatus.IN_STORAGE);
    unassignment.setApprovedBy(approver);
    unassignment.setUnassignApprovedBy(approver);
    unassignment.setDateOfAssignment(Instant.now());

    Device device = unassignment.getDevice();
    device.setStatus(DeviceStatus.IN_STORAGE);
    device.setCurrentLocation(unassignment.getToLocation());
    deviceRepository.save(device);

    DeviceAssignment saved = assignmentRepository.save(unassignment);

    log.info("Unassignment approved: id={}, by={}", unassignmentId, approvedByUserId);
    return saved;
  }

  /**
   * Assignment kérelem elutasítása (PENDING_ASSIGNMENT vagy PENDING_UNASSIGNMENT).
   *
   * @param assignmentId az elutasítandó assignment ID-ja
   * @param rejectedByUserId az elutasító user ID-ja
   * @return a frissített assignment (REJECTED státusszal)
   */
  @AuditTarget(entityType = "DeviceAssignment", action = "reject_assignment")
  @Transactional
  public DeviceAssignment rejectAssignment(Long assignmentId, Long rejectedByUserId) {
    DeviceAssignment assignment =
        assignmentRepository
            .findById(assignmentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Assignment not found: " + assignmentId));

    if (assignment.getStatus() != AssignmentStatus.PENDING_ASSIGNMENT
        && assignment.getStatus() != AssignmentStatus.PENDING_UNASSIGNMENT) {
      throw new BusinessValidationException(
          "assignmentNotPending", "Assignment is not in pending status");
    }

    AppUser rejecter =
        userRepository
            .findById(rejectedByUserId)
            .orElseThrow(
                () -> new ResourceNotFoundException("User not found: " + rejectedByUserId));

    AssignmentStatus previousStatus = assignment.getStatus();
    assignment.setStatus(AssignmentStatus.REJECTED);
    assignment.setApprovedBy(rejecter);

    Device device = assignment.getDevice();
    if (previousStatus == AssignmentStatus.PENDING_ASSIGNMENT) {
      device.setStatus(DeviceStatus.IN_STORAGE);
      // Restore previous active assignment (undo the new-assignment request)
      assignmentRepository
          .findFirstByDeviceIdAndStatusOrderByCreatedDateDesc(
              device.getId(), AssignmentStatus.ASSIGNED)
          .ifPresent(
              prev -> {
                assignmentRepository.save(prev);
              });
    } else if (previousStatus == AssignmentStatus.PENDING_UNASSIGNMENT) {
      device.setStatus(DeviceStatus.ASSIGNED);
    }
    DeviceAssignment saved = assignmentRepository.save(assignment);
    log.info("Assignment rejected: id={}, by={}", assignmentId, rejectedByUserId);
    return saved;
  }

  /**
   * Eszköz státusz manuális átállítása (operatív/admin beavatkozás).
   *
   * <p>A state machine ({@link #ALLOWED_TRANSITIONS}) határozza meg, hogy mely átmenetek
   * engedélyezettek. DISPOSED végállapot — onnan nincs visszaút.
   *
   * <p>Ha az eszköznek van aktív assignmentje (ASSIGNED státuszban), a státusz átállítása NEM
   * inaktiválja automatikusan azt — az operátor felelőssége, hogy a megfelelő flow-t válassza. Ha a
   * célstátusz IN_STORAGE, az aktív assignment inaktívvá válik (orphan assignment keletkezik, de a
   * history megmarad).
   *
   * @param deviceId az eszköz azonosítója
   * @param newStatus az új státusz
   * @return a frissített device
   * @throws ResourceNotFoundException ha a device nem található
   * @throws BusinessValidationException ha az átmenet nem megengedett
   */
  @AuditTarget(entityType = "Device", action = "change_status")
  @Transactional
  public Device changeStatus(Long deviceId, DeviceStatus newStatus) {
    Device device =
        deviceRepository
            .findById(deviceId)
            .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));

    DeviceStatus currentStatus = device.getStatus();
    if (currentStatus == newStatus) {
      return device;
    }

    if (!ALLOWED_TRANSITIONS
        .getOrDefault(currentStatus, EnumSet.noneOf(DeviceStatus.class))
        .contains(newStatus)) {
      throw new BusinessValidationException(
          "deviceStatusTransitionNotAllowed",
          "Status transition not allowed: " + currentStatus + " → " + newStatus);
    }

    // Ha IN_STORAGE-ra váltunk és van aktív assignment, inaktiváljuk
    if (newStatus == DeviceStatus.IN_STORAGE) {
      assignmentRepository
          .findFirstByDeviceIdAndStatus(deviceId, AssignmentStatus.ASSIGNED)
          .ifPresent(
              activeAssignment -> {
                activeAssignment.setUnassignCreatedDate(java.time.Instant.now());
                assignmentRepository.save(activeAssignment);
              });
    }

    if (newStatus == DeviceStatus.DISPOSED) {
      device.setCurrentLocation(null);
    }

    device.setStatus(newStatus);
    Device saved = deviceRepository.save(device);

    log.info("Device {} status changed: {} → {}", deviceId, currentStatus, newStatus);
    return saved;
  }

  /**
   * Eszköz karbantartásba küldésének KÉRÉSE (IN_STORAGE vagy ASSIGNED státuszból →
   * PENDING_MAINTENANCE).
   */
  @AuditTarget(entityType = "Device", action = "request_maintenance")
  @Transactional
  public Device requestMaintenance(Long deviceId, String reason, Long byUserId) {
    Device device =
        deviceRepository
            .findById(deviceId)
            .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));

    if (device.getStatus() != DeviceStatus.IN_STORAGE
        && device.getStatus() != DeviceStatus.ASSIGNED) {
      throw new BusinessValidationException(
          "deviceStatusTransitionNotAllowed",
          "Cannot request maintenance for device in status: " + device.getStatus());
    }

    device.setPreviousStatus(device.getStatus());
    device.setStatus(DeviceStatus.PENDING_MAINTENANCE);
    device.setStatusReason(reason);
    Device saved = deviceRepository.save(device);
    log.info(
        "Maintenance requested for device {} by user {}. Reason: {}", deviceId, byUserId, reason);
    return saved;
  }

  /** Karbantartási kérelem JÓVÁHAGYÁSA (PENDING_MAINTENANCE → MAINTENANCE). */
  @AuditTarget(entityType = "Device", action = "approve_maintenance")
  @Transactional
  public Device approveMaintenance(Long deviceId, Long byUserId) {
    Device device =
        deviceRepository
            .findById(deviceId)
            .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));

    if (device.getStatus() != DeviceStatus.PENDING_MAINTENANCE) {
      throw new BusinessValidationException(
          "deviceNotPendingMaintenance", "Device is not in PENDING_MAINTENANCE status");
    }

    // Ha az eszköz korábban hozzá volt rendelve valakihez/valahova, az aktív assignmentet lezárjuk
    if (device.getPreviousStatus() == DeviceStatus.ASSIGNED) {
      assignmentRepository
          .findFirstByDeviceIdAndStatus(deviceId, AssignmentStatus.ASSIGNED)
          .ifPresent(
              activeAssignment -> {
                activeAssignment.setUnassignCreatedDate(Instant.now());
                assignmentRepository.save(activeAssignment);
              });
    }

    device.setStatus(DeviceStatus.MAINTENANCE);
    device.setPreviousStatus(null);
    Device saved = deviceRepository.save(device);
    log.info("Maintenance approved for device {} by user {}", deviceId, byUserId);
    return saved;
  }

  /** Karbantartási kérelem ELUTASÍTÁSA (PENDING_MAINTENANCE → previousStatus). */
  @AuditTarget(entityType = "Device", action = "reject_maintenance")
  @Transactional
  public Device rejectMaintenance(Long deviceId, Long byUserId) {
    Device device =
        deviceRepository
            .findById(deviceId)
            .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));

    if (device.getStatus() != DeviceStatus.PENDING_MAINTENANCE) {
      throw new BusinessValidationException(
          "deviceNotPendingMaintenance", "Device is not in PENDING_MAINTENANCE status");
    }

    DeviceStatus revertStatus =
        device.getPreviousStatus() != null ? device.getPreviousStatus() : DeviceStatus.IN_STORAGE;
    device.setStatus(revertStatus);
    device.setStatusReason(null);
    device.setPreviousStatus(null);
    Device saved = deviceRepository.save(device);
    log.info(
        "Maintenance rejected for device {} by user {}, reverted to {}",
        deviceId,
        byUserId,
        revertStatus);
    return saved;
  }

  /** Eszköz visszavétele karbantartásból (MAINTENANCE → IN_STORAGE). */
  @AuditTarget(entityType = "Device", action = "return_from_maintenance")
  @Transactional
  public Device returnFromMaintenance(Long deviceId, Long byUserId) {
    Device device =
        deviceRepository
            .findById(deviceId)
            .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));

    if (device.getStatus() != DeviceStatus.MAINTENANCE) {
      throw new BusinessValidationException(
          "deviceNotInMaintenance", "Device is not in MAINTENANCE status");
    }

    device.setStatus(DeviceStatus.IN_STORAGE);
    device.setStatusReason(null);
    device.setPreviousStatus(null);
    Device saved = deviceRepository.save(device);
    log.info("Device {} returned from maintenance to IN_STORAGE by user {}", deviceId, byUserId);
    return saved;
  }

  /** Eszköz selejtezésének KÉRÉSE (IN_STORAGE vagy MAINTENANCE státuszból → PENDING_DISPOSAL). */
  @AuditTarget(entityType = "Device", action = "request_disposal")
  @Transactional
  public Device requestDisposal(Long deviceId, String reason, Long byUserId) {
    Device device =
        deviceRepository
            .findById(deviceId)
            .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));

    if (device.getStatus() == DeviceStatus.DISPOSED
        || device.getStatus() == DeviceStatus.PENDING_DISPOSAL) {
      throw new BusinessValidationException(
          "deviceAlreadyDisposed", "Device is already DISPOSED or PENDING_DISPOSAL");
    }

    if (device.getStatus() == DeviceStatus.ASSIGNED) {
      throw new BusinessValidationException(
          "assignedDeviceCannotBeDisposed",
          "Assigned devices must be unassigned or sent to maintenance before disposal");
    }

    device.setPreviousStatus(device.getStatus());
    device.setStatus(DeviceStatus.PENDING_DISPOSAL);
    device.setStatusReason(reason);
    Device saved = deviceRepository.save(device);
    log.info("Disposal requested for device {} by user {}. Reason: {}", deviceId, byUserId, reason);
    return saved;
  }

  /** Selejtezési kérelem JÓVÁHAGYÁSA (PENDING_DISPOSAL → DISPOSED végállapot). */
  @AuditTarget(entityType = "Device", action = "approve_disposal")
  @Transactional
  public Device approveDisposal(Long deviceId, Long byUserId) {
    Device device =
        deviceRepository
            .findById(deviceId)
            .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));

    if (device.getStatus() != DeviceStatus.PENDING_DISPOSAL) {
      throw new BusinessValidationException(
          "deviceNotPendingDisposal", "Device is not in PENDING_DISPOSAL status");
    }

    device.setStatus(DeviceStatus.DISPOSED);
    device.setCurrentLocation(null);
    device.setPreviousStatus(null);
    Device saved = deviceRepository.save(device);
    log.info("Disposal approved for device {} by user {}", deviceId, byUserId);
    return saved;
  }

  /** Selejtezési kérelem ELUTASÍTÁSA (PENDING_DISPOSAL → previousStatus). */
  @AuditTarget(entityType = "Device", action = "reject_disposal")
  @Transactional
  public Device rejectDisposal(Long deviceId, Long byUserId) {
    Device device =
        deviceRepository
            .findById(deviceId)
            .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));

    if (device.getStatus() != DeviceStatus.PENDING_DISPOSAL) {
      throw new BusinessValidationException(
          "deviceNotPendingDisposal", "Device is not in PENDING_DISPOSAL status");
    }

    DeviceStatus revertStatus =
        device.getPreviousStatus() != null ? device.getPreviousStatus() : DeviceStatus.IN_STORAGE;
    device.setStatus(revertStatus);
    device.setStatusReason(null);
    device.setPreviousStatus(null);
    Device saved = deviceRepository.save(device);
    log.info(
        "Disposal rejected for device {} by user {}, reverted to {}",
        deviceId,
        byUserId,
        revertStatus);
    return saved;
  }

  /** Függőben lévő karbantartási kérelmek listája. */
  @Transactional(readOnly = true)
  public java.util.List<Device> findPendingMaintenanceDevices() {
    return deviceRepository.findByStatusOrderByCreatedAtDesc(DeviceStatus.PENDING_MAINTENANCE);
  }

  /** Függőben lévő selejtezési kérelmek listája. */
  @Transactional(readOnly = true)
  public java.util.List<Device> findPendingDisposalDevices() {
    return deviceRepository.findByStatusOrderByCreatedAtDesc(DeviceStatus.PENDING_DISPOSAL);
  }

  /**
   * Device végleges törlése.
   *
   * <p>Audit és integritási szabály:
   * <ul>
   *   <li>Ha az eszközhöz már tartoztak hozzárendelési előzmények (DeviceAssignment rekordok),
   *       az eszköz NEM törölhető véglegesen az auditálhatóság és elszámoltathatóság megőrzése érdekében.
   *       Ilyenkor a lezárás hivatalos módja a selejtezés (DISPOSED státusz).
   *   <li>Ha az eszközhöz nem tartozik semmilyen hozzárendelés (pl. téves felvitel), akkor a fizikai
   *       törlés engedélyezett (csatolmányok és join tábla törlésével).
   * </ul>
   *
   * @param id az eszköz azonosítója
   */
  @AuditTarget(entityType = "Device", action = "delete")
  @Transactional
  public void delete(Long id) {
    Device device =
        deviceRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + id));

    // 1. Audit védelem: ellenőrizzük, van-e hozzárendelési előzmény
    List<DeviceAssignment> assignments =
        assignmentRepository.findByDeviceIdOrderByCreatedDateDesc(id);
    if (!assignments.isEmpty()) {
      throw new BusinessValidationException(
          "deviceHasAssignmentHistory",
          "Az eszköz nem törölhető véglegesen, mert hozzárendelési előzményekkel rendelkezik. Az életciklus lezárásához használja a selejtezést (DISPOSED státusz).");
    }

    // 2. Kapcsolódó csatolmányok és fizikai fájlok törlése
    if (attachmentService != null) {
      attachmentService.deleteByDevice(id);
    }

    // 3. Szoftver kapcsolatok bontása (join tábla)
    if (device.getSoftwares() != null) {
      device.getSoftwares().clear();
    }

    // 4. Eszköz törlése
    deviceRepository.delete(device);
    log.info("Device permanently deleted: id={}, inventoryNumber={}", id, device.getInventoryNumber());
  }
}
