package hu.tanszek.device.audit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import hu.tanszek.device.audit.entity.AuditLog;
import hu.tanszek.device.audit.repository.AuditLogRepository;
import hu.tanszek.device.common.ScheduledJobMonitoring;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditRetentionJobTest {

  @Mock private AuditLogRepository auditLogRepository;
  @Mock private ScheduledJobMonitoring jobMonitoring;
  private ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
          .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  @InjectMocks private AuditRetentionJob retentionJob;

  private Path tempArchiveDir;

  @BeforeEach
  void setUp() throws IOException {
    tempArchiveDir = Files.createTempDirectory("audit-retention-test");
    ReflectionTestUtils.setField(retentionJob, "archivePath", tempArchiveDir.toString());
    ReflectionTestUtils.setField(retentionJob, "retentionYears", 5);
    ReflectionTestUtils.setField(retentionJob, "objectMapper", objectMapper);
  }

  @Test
  void run_delegatesToJobMonitoring() {
    doAnswer(
            invocation -> {
              Runnable runnable = invocation.getArgument(1);
              runnable.run();
              return null;
            })
        .when(jobMonitoring)
        .run(eq("audit-retention-job"), any(Runnable.class));

    when(auditLogRepository.findByTimestampBefore(any(Instant.class))).thenReturn(List.of());
    when(auditLogRepository.deleteByTimestampBefore(any(Instant.class))).thenReturn(0);

    retentionJob.run();

    verify(jobMonitoring).run(eq("audit-retention-job"), any(Runnable.class));
  }

  @Test
  void executeRetention_archivesAndDeletes() {
    AuditLog oldLog =
        AuditLog.builder()
            .id(1L)
            .timestamp(Instant.now().minus(400, ChronoUnit.DAYS))
            .userEmail("admin@tanszek.local")
            .entityType("Device")
            .entityId(10L)
            .changesJson("{\"action\":\"test\"}")
            .build();

    when(auditLogRepository.findByTimestampBefore(any(Instant.class))).thenReturn(List.of(oldLog));
    when(auditLogRepository.deleteByTimestampBefore(any(Instant.class))).thenReturn(3);

    retentionJob.executeRetention();

    verify(auditLogRepository).findByTimestampBefore(any(Instant.class));
    verify(auditLogRepository).deleteByTimestampBefore(any(Instant.class));
  }
}
