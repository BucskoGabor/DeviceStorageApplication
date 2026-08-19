package hu.tanszek.device.audit;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import hu.tanszek.device.audit.entity.AuditLog;
import hu.tanszek.device.audit.repository.AuditLogRepository;
import hu.tanszek.device.common.ScheduledJobMonitoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AsyncAuditLogService — aszinkron audit log mentés dedicated Spring proxy service-ként.
 *
 * <p>Külön osztályba szervezve biztosítja, hogy az {@code @Async} annotáció Spring proxy-n
 * keresztül hívódjon meg (elkerülve a self-invocation bypass-t).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncAuditLogService {

  /** Érzékeny mezőnevek (case-insensitive substring match) */
  private static final String[] SENSITIVE_FIELDS = {
    "password", "secret", "token", "licensekey", "license_key"
  };

  private final AuditLogRepository auditLogRepository;
  private final EntityTypeRegistry entityTypeRegistry;
  private final ObjectMapper objectMapper;
  private final ScheduledJobMonitoring jobMonitoring;

  /** Aszinkron audit log mentés. */
  @Async
  public void saveAuditLog(
      String entityType,
      Object entityId,
      Object beforeState,
      Object afterState,
      String action,
      String requestPayload,
      String errorMessage,
      String userEmail) {
    jobMonitoring.run(
        "audit-log-write",
        () -> {
          try {
            // Diff JSON összeállítása
            String changesJson = buildChangesJson(beforeState, afterState);
            if (changesJson == null) {
              changesJson = "{}";
            }

            AuditLog auditLog =
                AuditLog.builder()
                    .timestamp(Instant.now())
                    .userEmail(userEmail != null ? userEmail : "anonymous")
                    .endpoint(action != null ? action : "unknown")
                    .method("INTERNAL")
                    .requestPayload(requestPayload)
                    .changesJson(changesJson)
                    .httpStatus(errorMessage != null ? 500 : 200)
                    .entityType(entityType)
                    .entityId(entityId != null ? ((Number) entityId).longValue() : null)
                    .build();

            auditLogRepository.save(auditLog);

            log.info(
                "Audit log saved: {} on {} (id={}) by {}", action, entityType, entityId, userEmail);
          } catch (Exception e) {
            log.error("Failed to save audit log for {} {} id={}", action, entityType, entityId, e);
            throw e; // A monitoring wrapper elkapja és alert-et küld
          }
        });
  }

  /**
   * changes_json összeállítása: {before: {...}, after: {...}}.
   *
   * <p>Az érzékeny mezőket maszkolja (case-insensitive substring match).
   */
  @SuppressWarnings("unchecked")
  public String buildChangesJson(Object beforeState, Object afterState) {
    try {
      Map<String, Object> beforeMap =
          beforeState instanceof Map
              ? (Map<String, Object>) beforeState
              : (beforeState != null ? entityTypeRegistry.toJsonMap(beforeState) : null);
      Map<String, Object> afterMap =
          afterState instanceof Map
              ? (Map<String, Object>) afterState
              : (afterState != null ? entityTypeRegistry.toJsonMap(afterState) : null);

      if (Objects.equals(beforeMap, afterMap)) {
        return null;
      }

      Map<String, Object> changes = new HashMap<>();
      changes.put("before", beforeMap != null ? maskSensitiveFields(beforeMap) : null);
      changes.put("after", afterMap != null ? maskSensitiveFields(afterMap) : null);

      return objectMapper.writeValueAsString(changes);
    } catch (Exception e) {
      log.warn("Failed to build changes_json", e);
      return null;
    }
  }

  /**
   * Érzékeny mezők maszkolása: ***-re cserélés case-insensitive substring match-csel.
   *
   * <p>Példák: password → ***, passwordHash → ***, tokenHash → ***, licenseKeyEncrypted → ***.
   */
  public Map<String, Object> maskSensitiveFields(Map<String, Object> source) {
    Map<String, Object> masked = new HashMap<>();
    for (Map.Entry<String, Object> entry : source.entrySet()) {
      String key = entry.getKey();
      String keyLower = key.toLowerCase().replace("_", "").replace("-", "");

      boolean isSensitive = false;
      if (!keyLower.contains("mustchangepassword")) {
        for (String sensitive : SENSITIVE_FIELDS) {
          if (keyLower.contains(sensitive.toLowerCase().replace("_", ""))) {
            isSensitive = true;
            break;
          }
        }
      }

      if (isSensitive) {
        masked.put(key, "***");
      } else {
        masked.put(key, entry.getValue());
      }
    }
    return masked;
  }
}
