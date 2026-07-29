package hu.tanszek.device.audit.repository;

import hu.tanszek.device.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Audit log repository.
 *
 * @see AuditLog
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Audit log-ok listája entity alapján.
     */
    List<AuditLog> findByEntityTypeAndEntityId(String entityType, Long entityId);

    /**
     * Retention cleanup: 1+ éves rekordok archiválás előtt.
     */
    List<AuditLog> findByTimestampBefore(Instant cutoff);

    /**
     * Retention cleanup: 5+ éves rekordok végleges törlése.
     */
    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.timestamp < :cutoff")
    int deleteByTimestampBefore(@Param("cutoff") Instant cutoff);

    /**
     * User audit history.
     */
    List<AuditLog> findByUserEmailOrderByTimestampDesc(String userEmail);
}