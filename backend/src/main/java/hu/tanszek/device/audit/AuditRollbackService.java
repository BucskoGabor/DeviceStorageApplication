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

    // 1. A changes_json parse-olása
    Map<String, Object> changes = parseChanges(auditLog.getChangesJson());
    Map<String, Object> beforeState = (Map<String, Object>) changes.get("before");
    Map<String, Object> afterState = (Map<String, Object>) changes.get("after");

    String entityType = auditLog.getEntityType();
        Long entityId = auditLog.getEntityId();

        if (entityId == null && afterState != null && afterState.get("id") != null) {
            try {
                entityId = Long.valueOf(afterState.get("id").toString());
            } catch (NumberFormatException ignored) {}
        }
        if (entityId == null && beforeState != null && beforeState.get("id") != null) {
            try {
                entityId = Long.valueOf(beforeState.get("id").toString());
            } catch (NumberFormatException ignored) {}
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
      changesJsonStr =
          objectMapper.writeValueAsString(
              Map.of(
                  "rollbackOf", auditLogId,
                  "before", beforeState,
                  "after", afterState));
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

  /**
   * DELETE rollback: az entitás újra-létrehozása a before state-ből.
   *
   * <p>FIGYELEM: törölt entitás újralétrehozása FK ütközés és ID konfliktus kockázatával jár. A
   * jelenlegi implementáció warning log-ot ír és admin manuális beavatkozást igényel.
   */
  private void rollbackDelete(String entityType, Long entityId, Map<String, Object> beforeState) {
    log.warn(
        "DELETE rollback requested: entityType={}, entityId={} — "
            + "automatic recreation is not supported due to FK/ID conflict risk. "
            + "Manual admin action required. Before state: {}",
        entityType,
        entityId,
        beforeState);
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
}
