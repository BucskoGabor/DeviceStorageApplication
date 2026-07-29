package hu.tanszek.device.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.tanszek.device.audit.entity.AuditLog;
import hu.tanszek.device.audit.repository.AuditLogRepository;
import hu.tanszek.device.common.BaseEntity;
import hu.tanszek.device.common.ScheduledJobMonitoring;
import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * AuditAspect — AOP-based audit interceptor service-szinten.
 *
 * <p>A service metódusok ELŐTT (@Around):
 * <ol>
 *   <li>Azonosítja az entity-t (paraméter vagy return value)</li>
 *   <li>DB-ből query-zi az aktuális state-et (BEFORE)</li>
 *   <li>Lokálisan tárolja a BEFORE state-et</li>
 *   <li>A service metódus lefut</li>
 *   <li>Az új state-et (AFTER) serializálja</li>
 *   <li>AuditLog bejegyzést ír a DB-be</li>
 * </ol>
 *
 * <p>A rollback a changes_json mező alapján történik: {@code {"before": {...}, "after": {...}}}.
 * Ha azonos a before és after, nincs audit log (csak olvvasás).
 *
 * <p>Érzékeny mezők maszkolása case-insensitive substring match-csel:
 * password, secret, token, license_key (beleértve a *_encrypted suffix-es formákat).
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
    private final AppUserRepository appUserRepository;
    private final ObjectMapper objectMapper;
    private final ScheduledJobMonitoring jobMonitoring;

    /**
     * @Around advice — minden AuditTarget annotációval ellátott service metódusra.
     *
     * @param joinPoint a metódus invocation context
     * @param auditTarget az annotáció (entityType + action)
     * @return a service metódus return value (változatlanul továbbítva)
     */
    @Around("@annotation(auditTarget) && within(hu.tanszek.device..service..*)")
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
     * BEFORE state: DB-ből az aktuális entity lekérdezése.
     */
    private Object captureBeforeState(String entityType, Object entityId) {
        // TODO Task 2.7+: minden entity típusra repository alapján
        // Most egyszerűsített változat: csak AppUser-t támogatja
        if ("AppUser".equals(entityType) && entityId instanceof Long) {
            Optional<AppUser> user = appUserRepository.findById((Long) entityId);
            return user.orElse(null);
        }
        return null;
    }

    /**
     * AFTER state: return value vagy argumentum serializálása.
     */
    private Object captureAfterState(Object result, Object[] args, String entityType) {
        if (result == null) {
            // Argumentumból keresünk entity-t
            for (Object arg : args) {
                if (isEntityType(arg, entityType)) {
                    return arg;
                }
            }
        }
        return result;
    }

    /**
     * Entity ID kinyerése a joinPoint paramétereiből.
     */
    private Object extractEntityId(ProceedingJoinPoint joinPoint, String entityType) {
        for (Object arg : joinPoint.getArgs()) {
            if (isEntityType(arg, entityType)) {
                try {
                    return arg.getClass().getMethod("getId").invoke(arg);
                } catch (Exception e) {
                    log.warn("Failed to extract entity ID from {}", arg.getClass(), e);
                }
            }
        }
        return null;
    }

    /**
     * Entity ID kinyerése az AFTER state-ből (ha create esetén az ID még nincs paraméterben).
     */
    private Object extractEntityIdFromState(Object state) {
        if (state == null) return null;
        try {
            return state.getClass().getMethod("getId").invoke(state);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Ellenőrzi, hogy egy objektum az adott entity típus-e.
     */
    private boolean isEntityType(Object obj, String entityType) {
        return obj != null && obj.getClass().getSimpleName().equals(entityType);
    }

    /**
     * Audit log bejegyzés mentése @Async.
     */
    @Async
    public void saveAuditLog(
            String entityType,
            Object entityId,
            Object beforeState,
            Object afterState,
            String action,
            String errorMessage
    ) {
        jobMonitoring.run("audit-log-write", () -> {
            try {
                // Diff JSON összeállítása
                String changesJson = buildChangesJson(beforeState, afterState);

                // Ha nincs változás és csak olvvasás volt, skip
                if ((changesJson == null || changesJson.isEmpty()) && !"failed".endsWith(action)) {
                    return;
                }

                // User email lekérése a SecurityContext-ből
                String userEmail = getCurrentUserEmail();

                AuditLog auditLog = AuditLog.builder()
                        .timestamp(Instant.now())
                        .userEmail(userEmail)
                        .endpoint("service-method") // TODO: controller endpoint
                        .method("INTERNAL")
                        .requestPayload(null)
                        .changesJson(changesJson)
                        .httpStatus(errorMessage != null ? 500 : 200)
                        .entityType(entityType)
                        .entityId(entityId != null ? ((Number) entityId).longValue() : null)
                        .build();

                auditLogRepository.save(auditLog);

                log.debug("Audit log saved: {} on {} (id={})", action, entityType, entityId);
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
    private String buildChangesJson(Object beforeState, Object afterState) {
        try {
            Map<String, Object> changes = new HashMap<>();

            if (beforeState != null) {
                Map<String, Object> beforeMap = objectMapper.convertValue(beforeState, Map.class);
                changes.put("before", maskSensitiveFields(beforeMap));
            } else {
                changes.put("before", null);
            }

            if (afterState != null) {
                Map<String, Object> afterMap = objectMapper.convertValue(afterState, Map.class);
                changes.put("after", maskSensitiveFields(afterMap));
            } else {
                changes.put("after", null);
            }

            // Ha a before és after azonos, null-t adunk vissza (skip audit log)
            if (changes.get("before") != null && changes.get("after") != null
                    && changes.get("before").equals(changes.get("after"))) {
                return null;
            }

            return objectMapper.writeValueAsString(changes);
        } catch (Exception e) {
            log.warn("Failed to build changes_json", e);
            return null;
        }
    }

    /**
     * Érzékeny mezők maszkolása: ***-re cserélés case-insensitive substring match-csel.
     *
     * <p>Példák: password → ***, passwordHash → ***, tokenHash → ***,
     * licenseKeyEncrypted → ***.
     */
    private Map<String, Object> maskSensitiveFields(Map<String, Object> source) {
        Map<String, Object> masked = new HashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            String keyLower = key.toLowerCase().replace("_", "").replace("-", "");

            boolean isSensitive = false;
            for (String sensitive : SENSITIVE_FIELDS) {
                if (keyLower.contains(sensitive.toLowerCase().replace("_", ""))) {
                    isSensitive = true;
                    break;
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

    /**
     * Current user email lekérése a SecurityContext-ből.
     */
    private String getCurrentUserEmail() {
        org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof String) {
            // Principal = emailHash (LocalAuthProvider beállítja)
            String emailHash = (String) authentication.getPrincipal();
            return appUserRepository.findByEmailHash(emailHash)
                    .map(AppUser::getEmailEncrypted) // Visszafejtett email
                    .orElse(emailHash);
        }
        return "anonymous";
    }
}