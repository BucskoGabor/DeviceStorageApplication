package hu.tanszek.device.audit;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import hu.tanszek.device.assignment.entity.AssignmentStatus;
import hu.tanszek.device.assignment.entity.DeviceAssignment;
import hu.tanszek.device.assignment.repository.DeviceAssignmentRepository;
import hu.tanszek.device.audit.entity.AuditLog;
import hu.tanszek.device.audit.repository.AuditLogRepository;
import hu.tanszek.device.common.BusinessValidationException;
import hu.tanszek.device.common.ResourceNotFoundException;
import hu.tanszek.device.device.entity.Device;
import hu.tanszek.device.device.entity.DeviceStatus;
import hu.tanszek.device.device.repository.DeviceRepository;
import hu.tanszek.device.location.entity.Location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditRollbackServiceTest {

  @Mock private AuditLogRepository auditLogRepository;
  @Mock private EntityTypeRegistry entityTypeRegistry;
  @Mock private DeviceRepository deviceRepository;
  @Mock private DeviceAssignmentRepository assignmentRepository;
  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks private AuditRollbackService auditRollbackService;

  private Device device;

  @BeforeEach
  void setUp() {
    device = Device.builder().id(1L).inventoryNumber("INV-001").type("Laptop").build();
  }

  @Test
  void rollback_update_success() {
    String changesJson =
        "{\"before\": {\"type\": \"OldLaptop\"}, \"after\": {\"type\": \"NewLaptop\"}}";
    AuditLog log =
        AuditLog.builder()
            .id(100L)
            .entityType("Device")
            .entityId(1L)
            .changesJson(changesJson)
            .build();

    when(auditLogRepository.findById(100L)).thenReturn(Optional.of(log));
    when(entityTypeRegistry.findById("Device", 1L)).thenReturn(device);
    when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

    AuditLog rollbackLog = auditRollbackService.rollback(100L);

    assertThat(rollbackLog).isNotNull();
    assertThat(rollbackLog.getMethod()).isEqualTo("ROLLBACK");
    verify(entityTypeRegistry).applyJsonMap(device, Map.of("type", "OldLaptop"));
    verify(entityTypeRegistry).saveEntity(device);
    verify(auditLogRepository).save(any(AuditLog.class));
  }

  @Test
  void rollback_create_deletesEntity() {
    String changesJson = "{\"before\": null, \"after\": {\"id\": 1, \"type\": \"NewLaptop\"}}";
    AuditLog log =
        AuditLog.builder()
            .id(101L)
            .entityType("Device")
            .entityId(1L)
            .changesJson(changesJson)
            .build();

    when(auditLogRepository.findById(101L)).thenReturn(Optional.of(log));
    when(entityTypeRegistry.findById("Device", 1L)).thenReturn(device);
    when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

    AuditLog rollbackLog = auditRollbackService.rollback(101L);

    assertThat(rollbackLog).isNotNull();
    verify(entityTypeRegistry).deleteById("Device", 1L);
  }

  @Test
  void rollback_delete_recreatesEntitySuccessfully() {
    String changesJson = "{\"before\": {\"id\": 1, \"type\": \"Laptop\"}, \"after\": null}";
    AuditLog log =
        AuditLog.builder()
            .id(102L)
            .entityType("Device")
            .entityId(1L)
            .changesJson(changesJson)
            .build();

    when(auditLogRepository.findById(102L)).thenReturn(Optional.of(log));
    when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

    AuditLog rollbackLog = auditRollbackService.rollback(102L);

    assertThat(rollbackLog).isNotNull();
    verify(entityTypeRegistry).recreateEntity(eq("Device"), eq(1L), any());
  }

  @Test
  void rollback_throwsWhenAuditLogNotFound() {
    when(auditLogRepository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> auditRollbackService.rollback(999L))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void rollback_throwsWhenEntityIdMissing() {
    String changesJson = "{\"before\": {}, \"after\": {}}";
    AuditLog log =
        AuditLog.builder()
            .id(103L)
            .entityType("Device")
            .entityId(null)
            .changesJson(changesJson)
            .build();

    when(auditLogRepository.findById(103L)).thenReturn(Optional.of(log));

    assertThatThrownBy(() -> auditRollbackService.rollback(103L))
        .isInstanceOf(BusinessValidationException.class);
  }

  @Test
  void rollback_DeviceAssignment_recomputesDeviceStatusToInStorageWhenNoActiveAssignment() {
    // After rolling back an approve_assign audit log for a DeviceAssignment, the device
    // should be re-derived to IN_STORAGE because there is no longer any active (ASSIGNED)
    // assignment for it. This guards against the silent state drift where the
    // DeviceAssignment row reverted but Device.status=ASSIGNED remained.
    Location storage = Location.builder().id(3L).name("Eszköz Raktár").build();
    device.setStatus(DeviceStatus.ASSIGNED);
    DeviceAssignment reverted =
        DeviceAssignment.builder()
            .id(7L)
            .device(device)
            .status(AssignmentStatus.PENDING_ASSIGNMENT)
            .build();
    String changesJson =
        "{\"before\": {\"status\": \"PENDING_ASSIGNMENT\"},"
            + " \"after\": {\"status\": \"ASSIGNED\"}}";
    AuditLog log =
        AuditLog.builder()
            .id(200L)
            .entityType("DeviceAssignment")
            .entityId(7L)
            .changesJson(changesJson)
            .build();

    when(auditLogRepository.findById(200L)).thenReturn(Optional.of(log));
    when(entityTypeRegistry.findById("DeviceAssignment", 7L)).thenReturn(reverted);
    when(assignmentRepository.findById(7L)).thenReturn(Optional.of(reverted));
    when(assignmentRepository.findFirstByDeviceIdAndStatus(1L, AssignmentStatus.ASSIGNED))
        .thenReturn(Optional.empty());
    when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

    auditRollbackService.rollback(200L);

    assertThat(device.getStatus()).isEqualTo(DeviceStatus.IN_STORAGE);
    verify(deviceRepository).save(device);
  }

  @Test
  void rollback_DeviceAssignment_recomputesDeviceStatusToAssignedWhenActiveAssignmentRemains() {
    // If another active (ASSIGNED) assignment still exists for the device, the device
    // status should remain ASSIGNED (no recompute-induced change).
    Location storage = Location.builder().id(3L).name("Eszköz Raktár").build();
    device.setStatus(DeviceStatus.ASSIGNED);
    DeviceAssignment reverted =
        DeviceAssignment.builder()
            .id(8L)
            .device(device)
            .status(AssignmentStatus.PENDING_UNASSIGNMENT)
            .build();
    String changesJson =
        "{\"before\": {\"status\": \"PENDING_UNASSIGNMENT\"},"
            + " \"after\": {\"status\": \"ASSIGNED\"}}";
    AuditLog log =
        AuditLog.builder()
            .id(201L)
            .entityType("DeviceAssignment")
            .entityId(8L)
            .changesJson(changesJson)
            .build();
    DeviceAssignment otherActive =
        DeviceAssignment.builder().id(9L).device(device).status(AssignmentStatus.ASSIGNED).build();

    when(auditLogRepository.findById(201L)).thenReturn(Optional.of(log));
    when(entityTypeRegistry.findById("DeviceAssignment", 8L)).thenReturn(reverted);
    when(assignmentRepository.findById(8L)).thenReturn(Optional.of(reverted));
    when(assignmentRepository.findFirstByDeviceIdAndStatus(1L, AssignmentStatus.ASSIGNED))
        .thenReturn(Optional.of(otherActive));
    when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

    auditRollbackService.rollback(201L);

    assertThat(device.getStatus()).isEqualTo(DeviceStatus.ASSIGNED);
    verify(deviceRepository, org.mockito.Mockito.never()).save(device);
  }

  @Test
  void rollback_DeviceAssignment_skipsRecomputeWhenDeviceInMaintenance() {
    // Maintenance state is independent of assignment rollback — must not be touched.
    device.setStatus(DeviceStatus.MAINTENANCE);
    DeviceAssignment reverted =
        DeviceAssignment.builder()
            .id(10L)
            .device(device)
            .status(AssignmentStatus.PENDING_ASSIGNMENT)
            .build();
    String changesJson =
        "{\"before\": {\"status\": \"PENDING_ASSIGNMENT\"},"
            + " \"after\": {\"status\": \"ASSIGNED\"}}";
    AuditLog log =
        AuditLog.builder()
            .id(202L)
            .entityType("DeviceAssignment")
            .entityId(10L)
            .changesJson(changesJson)
            .build();

    when(auditLogRepository.findById(202L)).thenReturn(Optional.of(log));
    when(entityTypeRegistry.findById("DeviceAssignment", 10L)).thenReturn(reverted);
    when(assignmentRepository.findById(10L)).thenReturn(Optional.of(reverted));
    when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

    auditRollbackService.rollback(202L);

    assertThat(device.getStatus()).isEqualTo(DeviceStatus.MAINTENANCE);
    verify(deviceRepository, org.mockito.Mockito.never()).save(device);
  }
}
