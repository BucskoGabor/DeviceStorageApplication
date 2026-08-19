package hu.tanszek.device.audit.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import hu.tanszek.device.audit.entity.AuditLog;

/**
 * Audit log repository.
 *
 * @see AuditLog
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

  /** Audit log-ok listája entity alapján (lapozás nélkül). */
  List<AuditLog> findByEntityTypeAndEntityId(String entityType, Long entityId);

  /** Audit log-ok lapozható listája entity alapján (controller használja). */
  Page<AuditLog> findByEntityTypeAndEntityId(String entityType, Long entityId, Pageable pageable);

  /** Retention cleanup: 1+ éves rekordok archiválás előtt. */
  List<AuditLog> findByTimestampBefore(Instant cutoff);

  /** Retention cleanup: 5+ éves rekordok végleges törlése. */
  @Modifying
  @Query("DELETE FROM AuditLog a WHERE a.timestamp < :cutoff")
  int deleteByTimestampBefore(@Param("cutoff") Instant cutoff);

  /** User audit history (lapozás nélkül). */
  List<AuditLog> findByUserEmailOrderByTimestampDesc(String userEmail);

  /** User audit history (lapozható, controller használja). */
  Page<AuditLog> findByUserEmailOrderByTimestampDesc(String userEmail, Pageable pageable);

  /**
   * Rollback idempotencia-védelem: van-e már rollback bejegyzés erre az audit log ID-ra.
   *
   * <p>Az AuditRollbackService a requestPayload mezőbe "auditLogId={id}" formátumban írja az
   * eredeti audit log azonosítóját, így a keresés konkrétan az adott audit logra szűr — nem
   * globálisan az összes rollback-re.
   */
  java.util.Optional<AuditLog> findByEndpointAndRequestPayload(
      String endpoint, String requestPayload);
}
