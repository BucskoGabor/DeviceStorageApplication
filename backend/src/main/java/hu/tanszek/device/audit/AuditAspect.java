package hu.tanszek.device.audit;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import hu.tanszek.device.audit.entity.AuditLog;
import hu.tanszek.device.audit.repository.AuditLogRepository;
import hu.tanszek.device.common.ScheduledJobMonitoring;
import hu.tanszek.device.crypto.CryptoService;
import hu.tanszek.device.user.repository.AppUserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AuditAspect — AOP-based audit interceptor service-szinten.
 *
 * <p>A service metódusok ELŐTT (@Around):
 *
 * <ol>
 *   <li>Azonosítja az entity-t (paraméter vagy return value)
 *   <li>DB-ből query-zi az aktuális state-et (BEFORE)
 *   <li>Lokálisan tárolja a BEFORE state-et
 *   <li>A service metódus lefut
 *   <li>Az új state-et (AFTER) serializálja
 *   <li>AuditLog bejegyzést ír a DB-be
 * </ol>
 *
 * <p>A rollback a changes_json mező alapján történik: {@code {"before": {...}, "after": {...}}}. Ha
 * azonos a before és after, nincs audit log (csak olvvasás).
 *
 * <p>Érzékeny mezők maszkolása case-insensitive substring match-csel: password, secret, token,
 * license_key (beleértve a *_encrypted suffix-es formákat).
 *
 * <p>Az audit log írás @Async — nem blockolja a service metódus futását.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

  /** Érzékeny mezőnevek (case-insensitive substring match) */
  private static final String[] SENSITIVE_FIELDS = {
    "password", "secret", "token", "licensekey", "license_key"
  };

  private final AuditLogRepository auditLogRepository;
  private final EntityTypeRegistry entityTypeRegistry;
  private final ObjectMapper objectMapper;
  private final ScheduledJobMonitoring jobMonitoring;
  private final AppUserRepository userRepository;
  private final CryptoService cryptoService;

  /**
   * @Around advice — minden AuditTarget annotációval ellátott service metódusra.
   *
   * @param joinPoint a metódus invocation context
   * @param auditTarget az annotáció (entityType + action)
   * @return a service metódus return value (változatlanul továbbítva)
   */
  @Around("@annotation(auditTarget)")
  public Object auditServiceMethod(ProceedingJoinPoint joinPoint, AuditTarget auditTarget)
      throws Throwable {

    String entityType = auditTarget.entityType();
    String action = auditTarget.action();

    // 1. BEFORE state capture (DB query az entity ID alapján)
    Object beforeState = null;
    Object entityId = extractEntityId(joinPoint, entityType);
    if (entityId != null) {
      beforeState = captureBeforeState(entityType, entityId);
    }

    // 2. Service method futtatás
    Object result;
    try {
      result = joinPoint.proceed();
    } catch (Throwable t) {
      // Hiba esetén is logolunk (action='failed')
      saveAuditLog(entityType, entityId, beforeState, null, action + "_failed", t.getMessage());
      throw t;
    }

    // 3. AFTER state capture (return value vagy argumentum)
    Object afterState = captureAfterState(result, joinPoint.getArgs(), entityType);
    Object afterEntityId = entityId != null ? entityId : extractEntityIdFromState(afterState);

    // 4. Audit log mentés (csak ha van változás vagy fontos action)
    saveAuditLog(entityType, afterEntityId, beforeState, afterState, action, null);

    return result;
  }

  /**
   * BEFORE state: DB-ből az aktuális entity lekérdezése és azonnali JSON-map pillanatkép készítése.
   *
   * <p>A pillanatkép-készítés azonnal megtörténik a service metódus futása ELŐTT, így a metódus
   * által végrehajtott in-place mutációk nem módosítják a 'before' állapotot.
   */
  private Map<String, Object> captureBeforeState(String entityType, Object entityId) {
    if (entityId == null || !(entityId instanceof Long)) {
      return null;
    }
    try {
      Object entity = entityTypeRegistry.findById(entityType, (Long) entityId);
      return entityTypeRegistry.toJsonMap(entity);
    } catch (IllegalArgumentException e) {
      log.warn("Unknown entity type for BEFORE state capture: {}", entityType);
      return null;
    }
  }

  /** AFTER state: return value vagy argumentum serializálása JSON-map pillanatképpé. */
  private Map<String, Object> captureAfterState(Object result, Object[] args, String entityType) {
    Object entity = result;
    if (entity == null) {
      for (Object arg : args) {
        if (isEntityType(arg, entityType)) {
          entity = arg;
          break;
        }
      }
    }
    return entityTypeRegistry.toJsonMap(entity);
  }

  /** Entity ID kinyerése a joinPoint paramétereiből. */
  private Object extractEntityId(ProceedingJoinPoint joinPoint, String entityType) {
    for (Object arg : joinPoint.getArgs()) {
      if (arg == null) continue;
      if (isEntityType(arg, entityType)) {
        try {
          return arg.getClass().getMethod("getId").invoke(arg);
        } catch (Exception e) {
          log.warn("Failed to extract entity ID from {}", arg.getClass(), e);
        }
      } else if (arg instanceof Long) {
        return arg;
      }
    }
    return null;
  }

  /** Entity ID kinyerése az AFTER state-ből (ha create esetén az ID még nincs paraméterben). */
  private Object extractEntityIdFromState(Object state) {
    if (state == null) return null;
    try {
      return state.getClass().getMethod("getId").invoke(state);
    } catch (Exception e) {
      return null;
    }
  }

  /** Ellenőrzi, hogy egy objektum az adott entity típus-e. */
  private boolean isEntityType(Object obj, String entityType) {
    return obj != null && obj.getClass().getSimpleName().equals(entityType);
  }

  /** Audit log bejegyzés mentése @Async. */
  @Async
  public void saveAuditLog(
      String entityType,
      Object entityId,
      Object beforeState,
      Object afterState,
      String action,
      String errorMessage) {
    jobMonitoring.run(
        "audit-log-write",
        () -> {
          try {
            // Diff JSON összeállítása
            String changesJson = buildChangesJson(beforeState, afterState);
            if (changesJson == null) {
              changesJson = "{}";
            }

            // User email lekérése a SecurityContext-ből
            String userEmail = getCurrentUserEmail();

            AuditLog auditLog =
                AuditLog.builder()
                    .timestamp(Instant.now())
                    .userEmail(userEmail)
                    .endpoint(action != null ? action : "unknown")
                    .method("INTERNAL")
                    .requestPayload(null)
                    .changesJson(changesJson)
                    .httpStatus(errorMessage != null ? 500 : 200)
                    .entityType(entityType)
                    .entityId(entityId != null ? ((Number) entityId).longValue() : null)
                    .build();

            auditLogRepository.save(auditLog);

            log.info("Audit log saved: {} on {} (id={})", action, entityType, entityId);
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
  private String buildChangesJson(Object beforeState, Object afterState) {
    try {
      Map<String, Object> changes = new HashMap<>();

      Map<String, Object> beforeMap =
          beforeState instanceof Map
              ? (Map<String, Object>) beforeState
              : entityTypeRegistry.toJsonMap(beforeState);
      changes.put("before", beforeMap != null ? maskSensitiveFields(beforeMap) : null);

      Map<String, Object> afterMap =
          afterState instanceof Map
              ? (Map<String, Object>) afterState
              : entityTypeRegistry.toJsonMap(afterState);
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
  private Map<String, Object> maskSensitiveFields(Map<String, Object> source) {
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

  /** Current user email lekérése a SecurityContext-ből. */
  private String getCurrentUserEmail() {
    org.springframework.security.core.Authentication authentication =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication();
    if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() != null) {
      String name = authentication.getName();
      if ("anonymousUser".equals(name)) return "anonymous";
      return userRepository
          .findByEmailHash(name)
          .map(
              user -> {
                try {
                  return cryptoService.decrypt(user.getEmailEncrypted());
                } catch (Exception e) {
                  return name;
                }
              })
          .orElse(name);
    }
    return "anonymous";
  }
}
