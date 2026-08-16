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

import hu.tanszek.device.audit.entity.AuditLog;
import hu.tanszek.device.audit.repository.AuditLogRepository;
import hu.tanszek.device.common.BusinessValidationException;
import hu.tanszek.device.common.ResourceNotFoundException;
import hu.tanszek.device.device.entity.Device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditRollbackServiceTest {

  @Mock private AuditLogRepository auditLogRepository;
  @Mock private EntityTypeRegistry entityTypeRegistry;
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
  void rollback_delete_logsWarningAndDoesNotThrow() {
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
}
