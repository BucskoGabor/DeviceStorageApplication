package hu.tanszek.device.audit;

import java.util.HashMap;
import java.util.Map;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import hu.tanszek.device.audit.entity.AuditLog;
import hu.tanszek.device.audit.repository.AuditLogRepository;
import hu.tanszek.device.common.ScheduledJobMonitoring;
import hu.tanszek.device.crypto.CryptoService;
import hu.tanszek.device.device.entity.Device;
import hu.tanszek.device.user.repository.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tesztek az AuditAspect maszkoló és diff logikájához és az AOP @Around advice-hoz. */
class AuditAspectTest {

  private AuditAspect aspect;
  private AuditLogRepository auditLogRepository;
  private EntityTypeRegistry entityTypeRegistry;
  private ScheduledJobMonitoring jobMonitoring;
  private AppUserRepository userRepository;
  private CryptoService cryptoService;

  @BeforeEach
  void setUp() {
    auditLogRepository = mock(AuditLogRepository.class);
    entityTypeRegistry = mock(EntityTypeRegistry.class);
    jobMonitoring = mock(ScheduledJobMonitoring.class);
    userRepository = mock(AppUserRepository.class);
    cryptoService = mock(CryptoService.class);

    doAnswer(
            invocation -> {
              Runnable runnable = invocation.getArgument(1);
              runnable.run();
              return null;
            })
        .when(jobMonitoring)
        .run(eq("audit-log-write"), any(Runnable.class));

    aspect =
        new AuditAspect(
            auditLogRepository,
            entityTypeRegistry,
            new ObjectMapper(),
            jobMonitoring,
            userRepository,
            cryptoService);
  }

  @Test
  void maskSensitiveFieldsReplacesPassword() throws Exception {
    var method = AuditAspect.class.getDeclaredMethod("maskSensitiveFields", Map.class);
    method.setAccessible(true);

    Map<String, Object> source = new HashMap<>();
    source.put("email", "admin@tanszek.local");
    source.put("password", "secret123");
    source.put("passwordHash", "$argon2id$...");
    source.put("token", "eyJ...");
    source.put("licenseKey", "ABCD-1234");
    source.put("licenseKeyEncrypted", "encrypted...");

    @SuppressWarnings("unchecked")
    Map<String, Object> masked = (Map<String, Object>) method.invoke(aspect, source);

    assertThat(masked.get("email")).isEqualTo("admin@tanszek.local");
    assertThat(masked.get("password")).isEqualTo("***");
    assertThat(masked.get("passwordHash")).isEqualTo("***");
    assertThat(masked.get("token")).isEqualTo("***");
    assertThat(masked.get("licenseKey")).isEqualTo("***");
    assertThat(masked.get("licenseKeyEncrypted")).isEqualTo("***");
  }

  @Test
  void maskSensitiveFieldsIsCaseInsensitive() throws Exception {
    var method = AuditAspect.class.getDeclaredMethod("maskSensitiveFields", Map.class);
    method.setAccessible(true);

    Map<String, Object> source = new HashMap<>();
    source.put("Password", "secret");
    source.put("TOKEN_HASH", "hash");
    source.put("License_Key_Encrypted", "enc");

    @SuppressWarnings("unchecked")
    Map<String, Object> masked = (Map<String, Object>) method.invoke(aspect, source);

    assertThat(masked.get("Password")).isEqualTo("***");
    assertThat(masked.get("TOKEN_HASH")).isEqualTo("***");
    assertThat(masked.get("License_Key_Encrypted")).isEqualTo("***");
  }

  @Test
  void maskSensitiveFieldsKeepsNonSensitiveFields() throws Exception {
    var method = AuditAspect.class.getDeclaredMethod("maskSensitiveFields", Map.class);
    method.setAccessible(true);

    Map<String, Object> source = new HashMap<>();
    source.put("id", 1L);
    source.put("email", "test@x.com");
    source.put("active", true);
    source.put("name", "Tanterem 101");

    @SuppressWarnings("unchecked")
    Map<String, Object> masked = (Map<String, Object>) method.invoke(aspect, source);

    assertThat(masked.get("id")).isEqualTo(1L);
    assertThat(masked.get("email")).isEqualTo("test@x.com");
    assertThat(masked.get("active")).isEqualTo(true);
    assertThat(masked.get("name")).isEqualTo("Tanterem 101");
  }

  @Test
  void buildChangesJsonProducesBeforeAfterStructure() throws Exception {
    var method =
        AuditAspect.class.getDeclaredMethod("buildChangesJson", Object.class, Object.class);
    method.setAccessible(true);

    Map<String, Object> before = new HashMap<>();
    before.put("id", 1L);
    before.put("name", "Old Name");

    Map<String, Object> after = new HashMap<>();
    after.put("id", 1L);
    after.put("name", "New Name");

    String json = (String) method.invoke(aspect, before, after);

    assertThat(json).contains("\"before\"");
    assertThat(json).contains("\"after\"");
    assertThat(json).contains("Old Name");
    assertThat(json).contains("New Name");
  }

  @Test
  void buildChangesJsonReturnsNullWhenBeforeAndAfterIdentical() throws Exception {
    var method =
        AuditAspect.class.getDeclaredMethod("buildChangesJson", Object.class, Object.class);
    method.setAccessible(true);

    Map<String, Object> same = new HashMap<>();
    same.put("id", 1L);
    same.put("name", "Same");

    String json = (String) method.invoke(aspect, same, same);

    assertThat(json).isNull();
  }

  @Test
  void auditServiceMethod_success() throws Throwable {
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    MethodSignature signature = mock(MethodSignature.class);
    AuditTarget auditTarget = mock(AuditTarget.class);

    when(auditTarget.entityType()).thenReturn("Device");
    when(auditTarget.action()).thenReturn("update");

    Device device = Device.builder().id(1L).type("Laptop").build();
    when(joinPoint.getArgs()).thenReturn(new Object[] {1L, device});
    when(joinPoint.getSignature()).thenReturn(signature);
    when(signature.getParameterNames()).thenReturn(new String[] {"id", "device"});

    when(entityTypeRegistry.findById("Device", 1L)).thenReturn(device);
    when(entityTypeRegistry.toJsonMap(device)).thenReturn(Map.of("id", 1L, "type", "Laptop"));

    when(joinPoint.proceed()).thenReturn(device);

    Object result = aspect.auditServiceMethod(joinPoint, auditTarget);

    assertThat(result).isEqualTo(device);
    verify(auditLogRepository).save(any(AuditLog.class));
  }

  @Test
  void auditServiceMethod_throwsAndLogsFailure() throws Throwable {
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    MethodSignature signature = mock(MethodSignature.class);
    AuditTarget auditTarget = mock(AuditTarget.class);

    when(auditTarget.entityType()).thenReturn("Device");
    when(auditTarget.action()).thenReturn("delete");

    when(joinPoint.getArgs()).thenReturn(new Object[] {1L});
    when(joinPoint.getSignature()).thenReturn(signature);
    when(signature.getParameterNames()).thenReturn(new String[] {"id"});

    when(joinPoint.proceed()).thenThrow(new RuntimeException("DB error"));

    assertThatThrownBy(() -> aspect.auditServiceMethod(joinPoint, auditTarget))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("DB error");

    verify(auditLogRepository).save(any(AuditLog.class));
  }
}
