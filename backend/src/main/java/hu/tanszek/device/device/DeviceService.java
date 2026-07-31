package hu.tanszek.device.device;

import hu.tanszek.device.assignment.entity.AssignmentStatus;
import hu.tanszek.device.assignment.entity.DeviceAssignment;
import hu.tanszek.device.assignment.repository.DeviceAssignmentRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import java.time.Instant;

/**
 * DeviceService — Device CRUD + assign/unassign business logic.
 *
 * <p>State machine (Task 3.3):
 * <pre>
 *   IN_STORAGE → PENDING_ASSIGNMENT (assign kérés) → ASSIGNED (admin approve) → PENDING_UNASSIGNMENT → IN_STORAGE
 * </pre>
 *
 * <p>Üzleti szabályok:
 * <ul>
 *   <li>Device NEM lehet MAINTENANCE vagy DISPOSED státuszú assignkor</li>
 *   <li>Location NEM lehet GROUP típusú (forrás ÉS cél is)</li>
 *   <li>A row-level filter (Task 3.2-ből) a service-szintű assertion-nel van kiegészítve</li>
 * </ul>
 *
 * <p>Approval flow (mostantól bevezetve):
 * <ul>
 *   <li>assign() → PENDING_ASSIGNMENT (kérés jön létre, nem aktív)</li>
 *   <li>approveAssignment() → ASSIGNED (admin/user approve-olja)</li>
 *   <li>unassign() → PENDING_UNASSIGNMENT (kérés)</li>
 *   <li>approveUnassignment() → IN_STORAGE (admin approve)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    /**
     * Eszköz státusz átmenetek state machine — operatív PATCH endpoint-hoz.
     *
     * <p><b>Fontos:</b> a {@code → ASSIGNED} átmenetek <b>NEM</b> szerepelnek itt,
     * mert azokat az assignment workflow ({@code requestAssignment} /
     * {@code approveAssignment}) kezeli — közvetlen PATCH-csel
     * "ASSIGNED" státuszba rakni az eszközt assignment rekord nélkül
     * inkonzisztens állapotot okozna.
     *
     * <p>Szabályok:
     * <ul>
     *   <li>PENDING → IN_STORAGE / MAINTENANCE (raktárba vagy karbantartásra)</li>
     *   <li>IN_STORAGE → MAINTENANCE / DISPOSED (karbantartásra vagy selejtezésre)</li>
     *   <li>ASSIGNED → IN_STORAGE / MAINTENANCE (manuális visszavétel vagy karbantartás)</li>
     *   <li>MAINTENANCE → IN_STORAGE / DISPOSED (karbantartás kész vagy selejtezés)</li>
     *   <li>DISPOSED → (végállapot, NINCS visszaút)</li>
     * </ul>
     */
    private static final Map<DeviceStatus, Set<DeviceStatus>> ALLOWED_TRANSITIONS =
            new EnumMap<>(DeviceStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(DeviceStatus.PENDING,
                EnumSet.of(DeviceStatus.IN_STORAGE, DeviceStatus.MAINTENANCE));
        ALLOWED_TRANSITIONS.put(DeviceStatus.IN_STORAGE,
                EnumSet.of(DeviceStatus.MAINTENANCE, DeviceStatus.DISPOSED));
        ALLOWED_TRANSITIONS.put(DeviceStatus.ASSIGNED,
                EnumSet.of(DeviceStatus.IN_STORAGE, DeviceStatus.MAINTENANCE));
        ALLOWED_TRANSITIONS.put(DeviceStatus.MAINTENANCE,
                EnumSet.of(DeviceStatus.IN_STORAGE, DeviceStatus.DISPOSED));
        ALLOWED_TRANSITIONS.put(DeviceStatus.DISPOSED,
                EnumSet.noneOf(DeviceStatus.class));
    }

    private final DeviceRepository deviceRepository;
    private final DeviceAssignmentRepository assignmentRepository;
    private final LocationRepository locationRepository;
    private final AppUserRepository userRepository;

    /**
     * Assign kérés létrehozása — PENDING_ASSIGNMENT státusszal.
     *
     * <p>A régi aktív assignment (ha van) inaktívvá válik, az új PENDING_ASSIGNMENT
     * státusszal jön létre. Egy admin/user hívja az approveAssignment()-t a
     * tényleges ASSIGNED-re váltáshoz.
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
            Long deviceId,
            Long targetLocationId,
            Long targetUserId,
            Long byUserId
    ) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));

        // Üzleti szabály 1: MAINTENANCE/DISPOSED device-ot nem lehet assignolni
        if (device.getStatus() == DeviceStatus.MAINTENANCE || device.getStatus() == DeviceStatus.DISPOSED) {
            throw new BusinessValidationException(
                    "deviceNotAssignable",
                    "Device is in " + device.getStatus() + " status, cannot be assigned"
            );
        }

        // Üzleti szabály 2: GROUP location-ra NEM lehet assignolni
        Location targetLocation = null;
        if (targetLocationId != null) {
            targetLocation = locationRepository.findById(targetLocationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + targetLocationId));
            if (targetLocation.getType() == LocationType.GROUP) {
                throw new BusinessValidationException(
                        "groupLocationNotAssignable",
                        "GROUP locations are not assignable as targets"
                );
            }
        }

        // Üzleti szabály 3: target user valid (ha megadva)
        AppUser targetUser = null;
        if (targetUserId != null) {
            targetUser = userRepository.findById(targetUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetUserId));
        }

        // Üzleti szabály 4: by user valid
        AppUser byUser = userRepository.findById(byUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + byUserId));

        // Régi aktív assignment inaktiválása (ha van)
        assignmentRepository.findByDeviceIdAndActiveTrue(deviceId).ifPresent(oldAssignment -> {
            oldAssignment.setActive(false);
            oldAssignment.setUnassignCreatedDate(Instant.now());
            assignmentRepository.save(oldAssignment);
        });

        // Új PENDING_ASSIGNMENT rekord létrehozása
        DeviceAssignment newAssignment = DeviceAssignment.builder()
                .device(device)
                .fromLocation(null) // Új, tehát fromLocation = null
                .toLocation(targetLocation)
                .fromUser(null)
                .toUser(targetUser)
                .createdByUser(byUser)
                .approvedBy(null) // Még nincs approve-olva
                .status(AssignmentStatus.PENDING_ASSIGNMENT)
                .active(false) // PENDING, nem aktív
                .createdDate(Instant.now())
                .build();

        DeviceAssignment saved = assignmentRepository.save(newAssignment);

        log.info("Assignment requested: device={}, target={}, by={}",
                deviceId, targetUserId != null ? targetUserId : targetLocationId, byUserId);
        return saved;
    }

    /**
     * Assign jóváhagyása — PENDING_ASSIGNMENT → ASSIGNED átmenet.
     *
     * <p>Csak a {@code byUser} (vagy admin) hívhatja. A device státuszát
     * ASSIGNED-re állítja, és az assignment active=true lesz.
     *
     * @param assignmentId az assignment ID-ja
     * @param approvedByUserId a jóváhagyó user ID-ja
     * @return a frissített assignment (active=true, ASSIGNED)
     */
    @AuditTarget(entityType = "DeviceAssignment", action = "approve_assign")
    @Transactional
    public DeviceAssignment approveAssignment(Long assignmentId, Long approvedByUserId) {
        DeviceAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found: " + assignmentId));

        if (assignment.getStatus() != AssignmentStatus.PENDING_ASSIGNMENT) {
            throw new BusinessValidationException(
                    "assignmentNotPending",
                    "Assignment is not in PENDING_ASSIGNMENT status"
            );
        }

        AppUser approver = userRepository.findById(approvedByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + approvedByUserId));

        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setActive(true);
        assignment.setApprovedBy(approver);
        assignment.setDateOfAssignment(Instant.now());

        // Device státusz frissítése
        Device device = assignment.getDevice();
        device.setStatus(DeviceStatus.ASSIGNED);
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
    public DeviceAssignment requestUnassignment(Long assignmentId, Long byUserId) {
        DeviceAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found: " + assignmentId));

        if (!assignment.isActive()) {
            throw new BusinessValidationException(
                    "assignmentNotActive",
                    "Assignment is not active, cannot be unassigned"
            );
        }

        AppUser byUser = userRepository.findById(byUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + byUserId));

        // Régi aktív inaktiválása
        assignment.setActive(false);
        assignment.setUnassignCreatedDate(Instant.now());
        assignmentRepository.save(assignment);

        // Új PENDING_UNASSIGNMENT rekord (history-szerű)
        DeviceAssignment unassignmentRequest = DeviceAssignment.builder()
                .device(assignment.getDevice())
                .fromLocation(assignment.getToLocation())
                .fromUser(assignment.getToUser())
                .toLocation(null)
                .toUser(null)
                .createdByUser(byUser)
                .approvedBy(null)
                .status(AssignmentStatus.PENDING_UNASSIGNMENT)
                .active(false)
                .createdDate(Instant.now())
                .build();

        DeviceAssignment saved = assignmentRepository.save(unassignmentRequest);

        log.info("Unassignment requested: assignment={}, by={}", assignmentId, byUserId);
        return saved;
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
        DeviceAssignment unassignment = assignmentRepository.findById(unassignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Unassignment not found: " + unassignmentId));

        if (unassignment.getStatus() != AssignmentStatus.PENDING_UNASSIGNMENT) {
            throw new BusinessValidationException(
                    "unassignmentNotPending",
                    "Unassignment is not in PENDING_UNASSIGNMENT status"
            );
        }

        AppUser approver = userRepository.findById(approvedByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + approvedByUserId));

        unassignment.setStatus(AssignmentStatus.IN_STORAGE);
        unassignment.setActive(true);
        unassignment.setApprovedBy(approver);
        unassignment.setUnassignApprovedBy(approver);
        unassignment.setDateOfAssignment(Instant.now());

        Device device = unassignment.getDevice();
        device.setStatus(DeviceStatus.IN_STORAGE);
        deviceRepository.save(device);

        DeviceAssignment saved = assignmentRepository.save(unassignment);

        log.info("Unassignment approved: id={}, by={}", unassignmentId, approvedByUserId);
        return saved;
    }

    /**
     * Eszköz státusz manuális átállítása (operatív/admin beavatkozás).
     *
     * <p>A state machine ({@link #ALLOWED_TRANSITIONS}) határozza meg, hogy
     * mely átmenetek engedélyezettek. DISPOSED végállapot — onnan nincs visszaút.
     *
     * <p>Ha az eszköznek van aktív assignmentje (ASSIGNED státuszban), a
     * státusz átállítása NEM inaktiválja automatikusan azt — az operátor
     * felelőssége, hogy a megfelelő flow-t válassza. Ha a célstátusz IN_STORAGE,
     * az aktív assignment inaktívvá válik (orphan assignment keletkezik, de
     * a history megmarad).
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
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));

        DeviceStatus currentStatus = device.getStatus();
        if (currentStatus == newStatus) {
            return device;
        }

        if (!ALLOWED_TRANSITIONS.getOrDefault(currentStatus, EnumSet.noneOf(DeviceStatus.class))
                .contains(newStatus)) {
            throw new BusinessValidationException(
                    "deviceStatusTransitionNotAllowed",
                    "Status transition not allowed: " + currentStatus + " → " + newStatus
            );
        }

        // Ha IN_STORAGE-ra váltunk és van aktív assignment, inaktiváljuk
        if (newStatus == DeviceStatus.IN_STORAGE) {
            assignmentRepository.findByDeviceIdAndActiveTrue(deviceId).ifPresent(activeAssignment -> {
                activeAssignment.setActive(false);
                activeAssignment.setUnassignCreatedDate(java.time.Instant.now());
                assignmentRepository.save(activeAssignment);
            });
        }

        device.setStatus(newStatus);
        Device saved = deviceRepository.save(device);

        log.info("Device {} status changed: {} → {}", deviceId, currentStatus, newStatus);
        return saved;
    }
}