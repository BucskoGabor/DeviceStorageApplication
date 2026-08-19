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
  private AsyncAuditLogService asyncAuditLogService;
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

    asyncAuditLogService =
        new AsyncAuditLogService(
            auditLogRepository, entityTypeRegistry, new ObjectMapper(), jobMonitoring);

    aspect =
        new AuditAspect(
            entityTypeRegistry,
            new ObjectMapper(),
            userRepository,
            cryptoService,
            asyncAuditLogService);
  }

  @Test
  void maskSensitiveFieldsReplacesPassword() {
    Map<String, Object> source = new HashMap<>();
    source.put("email", "admin@tanszek.local");
    source.put("password", "secret123");
    source.put("passwordHash", "$argon2id$...");
    source.put("token", "eyJ...");
    source.put("licenseKey", "ABCD-1234");
    source.put("licenseKeyEncrypted", "encrypted...");

    Map<String, Object> masked = asyncAuditLogService.maskSensitiveFields(source);

    assertThat(masked.get("email")).isEqualTo("admin@tanszek.local");
    assertThat(masked.get("password")).isEqualTo("***");
    assertThat(masked.get("passwordHash")).isEqualTo("***");
    assertThat(masked.get("token")).isEqualTo("***");
    assertThat(masked.get("licenseKey")).isEqualTo("***");
    assertThat(masked.get("licenseKeyEncrypted")).isEqualTo("***");
  }

  @Test
  void maskSensitiveFieldsIsCaseInsensitive() {
    Map<String, Object> source = new HashMap<>();
    source.put("Password", "secret");
    source.put("TOKEN_HASH", "hash");
    source.put("License_Key_Encrypted", "enc");

    Map<String, Object> masked = asyncAuditLogService.maskSensitiveFields(source);

    assertThat(masked.get("Password")).isEqualTo("***");
    assertThat(masked.get("TOKEN_HASH")).isEqualTo("***");
    assertThat(masked.get("License_Key_Encrypted")).isEqualTo("***");
  }

  @Test
  void maskSensitiveFieldsKeepsNonSensitiveFields() {
    Map<String, Object> source = new HashMap<>();
    source.put("id", 1L);
    source.put("email", "test@x.com");
    source.put("active", true);
    source.put("name", "Tanterem 101");

    Map<String, Object> masked = asyncAuditLogService.maskSensitiveFields(source);

    assertThat(masked.get("id")).isEqualTo(1L);
    assertThat(masked.get("email")).isEqualTo("test@x.com");
    assertThat(masked.get("active")).isEqualTo(true);
    assertThat(masked.get("name")).isEqualTo("Tanterem 101");
  }

  @Test
  void buildChangesJsonProducesBeforeAfterStructure() {
    Map<String, Object> before = new HashMap<>();
    before.put("id", 1L);
    before.put("name", "Old Name");

    Map<String, Object> after = new HashMap<>();
    after.put("id", 1L);
    after.put("name", "New Name");

    String json = asyncAuditLogService.buildChangesJson(before, after);

    assertThat(json).contains("\"before\"");
    assertThat(json).contains("\"after\"");
    assertThat(json).contains("Old Name");
    assertThat(json).contains("New Name");
  }

  @Test
  void buildChangesJsonReturnsNullWhenBeforeAndAfterIdentical() {
    Map<String, Object> same = new HashMap<>();
    same.put("id", 1L);
    same.put("name", "Same");

    String json = asyncAuditLogService.buildChangesJson(same, same);

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

  @Test
  void extractEntityId_extractsFromVariousParamConventions() throws Exception {
    var method =
        AuditAspect.class.getDeclaredMethod(
            "extractEntityId", ProceedingJoinPoint.class, String.class);
    method.setAccessible(true);

    // 1. "id" param with Role
    ProceedingJoinPoint jp1 = mock(ProceedingJoinPoint.class);
    MethodSignature sig1 = mock(MethodSignature.class);
    when(jp1.getSignature()).thenReturn(sig1);
    when(jp1.getArgs()).thenReturn(new Object[] {42L});
    when(sig1.getParameterNames()).thenReturn(new String[] {"id"});
    assertThat(method.invoke(aspect, jp1, "Role")).isEqualTo(42L);

    // 2. "userId" param with AppUser
    ProceedingJoinPoint jp2 = mock(ProceedingJoinPoint.class);
    MethodSignature sig2 = mock(MethodSignature.class);
    when(jp2.getSignature()).thenReturn(sig2);
    when(jp2.getArgs()).thenReturn(new Object[] {10L});
    when(sig2.getParameterNames()).thenReturn(new String[] {"userId"});
    assertThat(method.invoke(aspect, jp2, "AppUser")).isEqualTo(10L);

    // 3. "assignmentId" param with DeviceAssignment
    ProceedingJoinPoint jp3 = mock(ProceedingJoinPoint.class);
    MethodSignature sig3 = mock(MethodSignature.class);
    when(jp3.getSignature()).thenReturn(sig3);
    when(jp3.getArgs()).thenReturn(new Object[] {99L, 5L});
    when(sig3.getParameterNames()).thenReturn(new String[] {"assignmentId", "approvedByUserId"});
    assertThat(method.invoke(aspect, jp3, "DeviceAssignment")).isEqualTo(99L);

    // 4. "unassignmentId" param with DeviceAssignment
    ProceedingJoinPoint jp4 = mock(ProceedingJoinPoint.class);
    MethodSignature sig4 = mock(MethodSignature.class);
    when(jp4.getSignature()).thenReturn(sig4);
    when(jp4.getArgs()).thenReturn(new Object[] {77L, 5L});
    when(sig4.getParameterNames()).thenReturn(new String[] {"unassignmentId", "approvedByUserId"});
    assertThat(method.invoke(aspect, jp4, "DeviceAssignment")).isEqualTo(77L);

    // 5. "attachmentId" param with DeviceAttachment
    ProceedingJoinPoint jp5 = mock(ProceedingJoinPoint.class);
    MethodSignature sig5 = mock(MethodSignature.class);
    when(jp5.getSignature()).thenReturn(sig5);
    when(jp5.getArgs()).thenReturn(new Object[] {88L});
    when(sig5.getParameterNames()).thenReturn(new String[] {"attachmentId"});
    assertThat(method.invoke(aspect, jp5, "DeviceAttachment")).isEqualTo(88L);

    // 6. Excluded params should return null (e.g. upload attachment where deviceId is first arg but
    // entityType is DeviceAttachment)
    ProceedingJoinPoint jp6 = mock(ProceedingJoinPoint.class);
    MethodSignature sig6 = mock(MethodSignature.class);
    when(jp6.getSignature()).thenReturn(sig6);
    when(jp6.getArgs()).thenReturn(new Object[] {1L, 2L});
    when(sig6.getParameterNames()).thenReturn(new String[] {"deviceId", "uploadedByUserId"});
    assertThat(method.invoke(aspect, jp6, "DeviceAttachment")).isNull();
  }
}
