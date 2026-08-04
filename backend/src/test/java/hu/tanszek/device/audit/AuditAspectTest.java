package hu.tanszek.device.audit;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import hu.tanszek.device.audit.repository.AuditLogRepository;
import hu.tanszek.device.common.ScheduledJobMonitoring;

import hu.tanszek.device.crypto.CryptoService;
import hu.tanszek.device.user.repository.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tesztek az AuditAspect maszkoló és diff logikájához.
 *
 * <p>Az AOP @Around advice-t nem teszteljük unit szinten (integrációs tesztben lenne értelme, Task
 * 5.3). A maszkoló és diff segédmetódusokat közvetlenül hívjuk reflection-nel.
 */
class AuditAspectTest {

  private AuditAspect aspect;

  @BeforeEach
  void setUp() throws Exception {
    AuditAspect real =
        new AuditAspect(
            mock(AuditLogRepository.class),
            mock(EntityTypeRegistry.class),
            new ObjectMapper(),
            mock(ScheduledJobMonitoring.class),
            mock(AppUserRepository.class),
            mock(CryptoService.class));
    aspect = real;
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
}
