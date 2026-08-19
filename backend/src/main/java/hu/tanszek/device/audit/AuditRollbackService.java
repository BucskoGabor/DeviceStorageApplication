package hu.tanszek.device.audit;

import java.time.Instant;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import hu.tanszek.device.audit.entity.AuditLog;
import hu.tanszek.device.audit.repository.AuditLogRepository;
import hu.tanszek.device.common.BusinessValidationException;
import hu.tanszek.device.common.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AuditRollbackService — audit log bejegyzés alapján rollback.
 *
 * <p>A {@code changes_json} mező {before, after} formátumban tárolja a rollback információt. A
 * rollback logikája:
 *
 * <ul>
 *   <li>UPDATE: after → before (visszaállítás a régi értékre)
 *   <li>CREATE: after != null, before == null → töröld az entitást
 *   <li>DELETE: before != null, after == null → hozd létre újra az entitást
 * </ul>
 *
 * <p>Idempotencia-védelem: ha már rollback-eltük az adott audit logot, a második hívás {@code
 * BusinessValidationException}-t dob. Ez megakadályozza, hogy dupla rollback DELETE-re a
 * recreateEntity ismét hívódjon, ami vagy duplicate key hibát adna, vagy felülírná a jelenlegi
 * állapotot.
 *
 * <p>Típusbiztonságos cast: a changes Map-ből a before/after kiolvasásánál instanceof check-et
 * végzünk — ha a JSON struktúra nem megfelelő (pl. a before egy string), null-t adunk vissza
 * ClassCastException helyett.
 *
 * <p>@Transactional REQUIRED: az entitás visszaállítása + az új audit log bejegyzés egy
 * tranzakcióban. Ha bármi hiba, rollback az egész.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditRollbackService {

  private final AuditLogRepository auditLogRepository;
  private final EntityTypeRegistry entityTypeRegistry;
  private final ObjectMapper objectMapper;

  /**
   * Audit log ID alapján rollback.
   *
   * <p>Csak AUDIT_ROLLBACK permissionnel rendelkező user hívhatja (controller
   * szinten @RequirePermission ellenőrzi).
   *
   * @param auditLogId az audit log ID-ja
   * @return az újonnan létrehozott audit log bejegyzés (a rollback rögzítésére)
   */
  @Transactional
  public AuditLog rollback(Long auditLogId) {
    AuditLog auditLog =
        auditLogRepository
            .findById(auditLogId)
            .orElseThrow(() -> new ResourceNotFoundException("Audit log not found: " + auditLogId));

    // Idempotencia-védelem: ha már rollback-eltük ezt az audit logot, ne csináljuk újra.
    // Dupla rollback egy DELETE-re a recreateEntity-t hívná, ami vagy duplicate key hibát
    // ad, vagy felülírná a jelenlegi állapotot — egyik sem kívánatos.
    boolean alreadyRolledBack =
        auditLogRepository
            .findByEndpointAndRequestPayload("rollback", "auditLogId=" + auditLogId)
            .isPresent();
    if (alreadyRolledBack) {
      throw new BusinessValidationException(
          "auditLogAlreadyRolledBack", "Audit log " + auditLogId + " has already been rolled back");
    }

    // 1. A changes_json parse-olása
    Map<String, Object> changes = parseChanges(auditLog.getChangesJson());
    Map<String, Object> beforeState = extractMap(changes, "before");
    Map<String, Object> afterState = extractMap(changes, "after");

    String entityType = auditLog.getEntityType();
    Long entityId = auditLog.getEntityId();

    if (entityId == null && afterState != null && afterState.get("id") != null) {
      try {
        entityId = Long.valueOf(afterState.get("id").toString());
      } catch (NumberFormatException ignored) {
      }
    }
    if (entityId == null && beforeState != null && beforeState.get("id") != null) {
      try {
        entityId = Long.valueOf(beforeState.get("id").toString());
      } catch (NumberFormatException ignored) {
      }
    }

    if (entityId == null) {
      throw new BusinessValidationException(
          "rollbackEntityIdMissing", "Entity ID is missing for rollback");
    }

    // 2. Rollback típus meghatározása
    if (afterState != null && beforeState != null) {
      // UPDATE: after → before (visszaállítás)
      rollbackUpdate(entityType, entityId, beforeState);
    } else if (afterState != null && beforeState == null) {
      // CREATE: töröld az entitást
      rollbackCreate(entityType, entityId);
    } else if (beforeState != null && afterState == null) {
      // DELETE: hozd létre újra az entitást
      rollbackDelete(entityType, entityId, beforeState);
    } else {
      throw new BusinessValidationException(
          "invalidAuditChanges", "Audit log changes_json has no before or after state");
    }

    String changesJsonStr;
    try {
      Map<String, Object> rollbackMap = new java.util.HashMap<>();
      rollbackMap.put("rollbackOf", auditLogId);
      rollbackMap.put("before", beforeState);
      rollbackMap.put("after", afterState);
      changesJsonStr = objectMapper.writeValueAsString(rollbackMap);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      changesJsonStr = "{}";
    }

    // 3. Új audit log a rollback rögzítésére
    AuditLog rollbackLog =
        AuditLog.builder()
            .timestamp(Instant.now())
            .userEmail(getCurrentUserEmail())
            .endpoint("rollback")
            .method("ROLLBACK")
            .requestPayload("auditLogId=" + auditLogId)
            .changesJson(changesJsonStr)
            .httpStatus(200)
            .entityType(entityType)
            .entityId(entityId)
            .build();

    AuditLog saved = auditLogRepository.save(rollbackLog);

    log.info(
        "Rollback completed: auditLogId={}, entityType={}, entityId={}",
        auditLogId,
        entityType,
        entityId);
    return saved;
  }

  /** UPDATE rollback: az entitás visszaállítása a before state-re. */
  private void rollbackUpdate(String entityType, Long entityId, Map<String, Object> beforeState) {
    Object entity = entityTypeRegistry.findById(entityType, entityId);
    if (entity == null) {
      throw new ResourceNotFoundException(entityType + " not found: " + entityId);
    }

    entityTypeRegistry.applyJsonMap(entity, beforeState);
    entityTypeRegistry.saveEntity(entity);
    log.info("UPDATE rollback applied: entityType={}, entityId={}", entityType, entityId);
  }

  /** CREATE rollback: az entitás törlése. */
  private void rollbackCreate(String entityType, Long entityId) {
    Object entity = entityTypeRegistry.findById(entityType, entityId);
    if (entity == null) {
      log.warn(
          "Entity already deleted (idempotent): entityType={}, entityId={}", entityType, entityId);
      return;
    }

    entityTypeRegistry.deleteById(entityType, entityId);
    log.info(
        "CREATE rollback applied (entity deleted): entityType={}, entityId={}",
        entityType,
        entityId);
  }

  /** DELETE rollback: az entitás újra-létrehozása a before state-ből. */
  private void rollbackDelete(String entityType, Long entityId, Map<String, Object> beforeState) {
    entityTypeRegistry.recreateEntity(entityType, entityId, beforeState);
    log.info(
        "DELETE rollback applied (entity recreated): entityType={}, originalEntityId={}",
        entityType,
        entityId);
  }

  /** changes_json parse-olása Map-é. */
  private Map<String, Object> parseChanges(String changesJson) {
    if (changesJson == null || changesJson.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(changesJson, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      throw new BusinessValidationException(
          "invalidAuditChanges", "Failed to parse changes_json: " + e.getMessage());
    }
  }

  /** Current user email a SecurityContext-ből. */
  private String getCurrentUserEmail() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() != null) {
      return authentication.getName();
    }
    return "anonymous";
  }

  /**
   * Típusbiztonságos map-kiolvasás a changes Map-ből. Korábban az unchecked {@code (Map<String,
   * Object>) changes.get("before")} cast ClassCastException-t dobott, ha a JSON struktúra nem volt
   * megfelelő — most ellenőrzött kiolvasás, és {@code null}-t ad vissza, ha a kulcs nem Map típusú.
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> extractMap(Map<String, Object> source, String key) {
    if (source == null) {
      return null;
    }
    Object value = source.get(key);
    if (value == null) {
      return null;
    }
    if (value instanceof Map) {
      return (Map<String, Object>) value;
    }
    // Ha nem Map (pl. a JSON-ban string/array volt), inkább null-t adunk vissza,
    // mint hogy ClassCastException-t dobnánk — a rollback így is el tud dönteni a
    // hiányzó before/after alapján, hogy milyen típusú rollback legyen.
    log.warn(
        "changes[{}] is not a Map (actual type: {}); treating as null",
        key,
        value.getClass().getSimpleName());
    return null;
  }
}
