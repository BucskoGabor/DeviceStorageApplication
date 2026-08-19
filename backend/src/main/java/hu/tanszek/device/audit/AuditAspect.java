package hu.tanszek.device.audit;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

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

  private static final Set<String> EXCLUDED_PARAM_NAMES =
      Set.of(
          "byUserId",
          "uploadedByUserId",
          "approvedByUserId",
          "rejectedByUserId",
          "targetUserId",
          "targetLocationId");

  private final EntityTypeRegistry entityTypeRegistry;
  private final ObjectMapper objectMapper;
  private final AppUserRepository userRepository;
  private final CryptoService cryptoService;
  private final AsyncAuditLogService asyncAuditLogService;

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

    // A userEmail-et szinkronban, a SecurityContext-ből olvassuk ki — az @Async
    // szál nem örökli a ThreadLocal SecurityContext-et, így ha az async metódusban
    // hívnánk getCurrentUserEmail()-t, minden audit log "anonymous" lenne.
    String userEmail = getCurrentUserEmail();

    // 2. Service method futtatás
    String requestPayload = extractRequestPayload(joinPoint);
    Object result;
    try {
      result = joinPoint.proceed();
    } catch (Throwable t) {
      // Hiba esetén is logolunk (action='failed')
      asyncAuditLogService.saveAuditLog(
          entityType,
          entityId,
          beforeState,
          null,
          action + "_failed",
          requestPayload,
          t.getMessage(),
          userEmail);
      throw t;
    }

    // 3. AFTER state capture (return value vagy argumentum)
    Object afterState = captureAfterState(result, joinPoint.getArgs(), entityType);
    Object afterEntityId = entityId != null ? entityId : extractEntityIdFromState(afterState);

    // 4. Audit log mentés (csak ha van változás vagy fontos action)
    asyncAuditLogService.saveAuditLog(
        entityType,
        afterEntityId,
        beforeState,
        afterState,
        action,
        requestPayload,
        null,
        userEmail);

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
    org.aspectj.lang.reflect.MethodSignature sig = null;
    if (joinPoint.getSignature() instanceof org.aspectj.lang.reflect.MethodSignature s) {
      sig = s;
    }
    String[] paramNames = sig != null ? sig.getParameterNames() : null;
    Object[] args = joinPoint.getArgs();

    for (int i = 0; i < args.length; i++) {
      Object arg = args[i];
      if (arg == null) continue;

      // 1) Ha maga az entity a paraméter (pl. UserDto), azonos típusú class-simple-name
      //    alapján, és onnan olvassuk a getId-t.
      if (isEntityType(arg, entityType)) {
        try {
          return arg.getClass().getMethod("getId").invoke(arg);
        } catch (Exception e) {
          log.warn("Failed to extract entity ID from {}", arg.getClass(), e);
        }
      }

      // 2) Long paraméter esetén a paraméternév vizsgálata
      if (arg instanceof Long && paramNames != null && i < paramNames.length) {
        String paramName = paramNames[i];
        if (isMatchingEntityIdParam(paramName, entityType)) {
          return arg;
        }
      }
    }
    return null;
  }

  private boolean isMatchingEntityIdParam(String paramName, String entityType) {
    if (paramName == null || EXCLUDED_PARAM_NAMES.contains(paramName)) {
      return false;
    }
    if ("id".equalsIgnoreCase(paramName)) {
      return true;
    }
    if (entityType != null && !entityType.isEmpty()) {
      String expected =
          Character.toLowerCase(entityType.charAt(0)) + entityType.substring(1) + "Id";
      if (expected.equalsIgnoreCase(paramName)) {
        return true;
      }
      if (("AppUser".equalsIgnoreCase(entityType) || "User".equalsIgnoreCase(entityType))
          && "userId".equalsIgnoreCase(paramName)) {
        return true;
      }
      if (("DeviceAssignment".equalsIgnoreCase(entityType)
              || "Assignment".equalsIgnoreCase(entityType))
          && ("assignmentId".equalsIgnoreCase(paramName)
              || "unassignmentId".equalsIgnoreCase(paramName))) {
        return true;
      }
      if (("DeviceAttachment".equalsIgnoreCase(entityType)
              || "Attachment".equalsIgnoreCase(entityType))
          && "attachmentId".equalsIgnoreCase(paramName)) {
        return true;
      }
    }
    return false;
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

  /** Metódus argumentumok kinyerése requestPayload-ba (érzékeny adatok maszkolásával). */
  private String extractRequestPayload(ProceedingJoinPoint joinPoint) {
    try {
      if (joinPoint.getSignature() instanceof org.aspectj.lang.reflect.MethodSignature sig) {
        String[] names = sig.getParameterNames();
        Object[] args = joinPoint.getArgs();
        if (names != null && args != null && names.length == args.length) {
          Map<String, Object> map = new HashMap<>();
          for (int i = 0; i < names.length; i++) {
            String name = names[i];
            Object val = args[i];
            if (val == null) {
              continue;
            }
            boolean sensitive = false;
            for (String s : SENSITIVE_FIELDS) {
              if (name.toLowerCase().contains(s)) {
                sensitive = true;
                break;
              }
            }
            if (sensitive) {
              map.put(name, "***");
            } else if (val instanceof String
                || val instanceof Number
                || val instanceof Boolean
                || val instanceof Enum) {
              map.put(name, val);
            }
          }
          if (!map.isEmpty()) {
            return objectMapper.writeValueAsString(map);
          }
        }
      }
    } catch (Exception e) {
      log.debug("Failed to extract request payload: {}", e.getMessage());
    }
    return null;
  }

  /** Current user email lekérése a SecurityContext-ből. */
  private String getCurrentUserEmail() {
    org.springframework.security.core.Authentication authentication =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication();
    if (authentication != null
        && authentication.isAuthenticated()
        && authentication.getPrincipal() != null) {
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
