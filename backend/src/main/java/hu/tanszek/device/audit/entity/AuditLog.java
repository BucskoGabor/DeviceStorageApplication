package hu.tanszek.device.audit.entity;

import hu.tanszek.device.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Audit log entitás (rollback támogatással).
 *
 * <p>Minden írási művelet automatikusan naplózódik az AOP Audit Interceptor
 * által (lásd {@code AuditAspect}). A {@code changesJson} mező a rollback-hez
 * kell: {@code {"before": {...}, "after": {...}}}.
 *
 * <p>Rollback szabályok:
 * <ul>
 *   <li>Update rollback: {@code after} → {@code before}</li>
 *   <li>Create rollback: {@code after == null, before != null} → törölje az entitást</li>
 *   <li>Delete rollback: {@code before != null, after == null} → visszaállítja a törölt entitást</li>
 * </ul>
 *
 * <p>Retention policy: 1 év után archiválás, 5 év után végleges törlés
 * (lásd {@code AuditRetentionJob}).
 *
 * @see hu.tanszek.device.audit.repository.AuditLogRepository
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AuditLog extends BaseEntity<Long> {

    /** Az audit esemény időbélyege */
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    /** A műveletet végző user emailje (maszkolt vagy eredeti) */
    @Column(name = "user_email", nullable = false, length = 255)
    private String userEmail;

    /** A hívott HTTP endpoint (pl. /api/devices/123) */
    @Column(name = "endpoint", nullable = false, length = 500)
    private String endpoint;

    /** HTTP metódus (GET, POST, PUT, DELETE) */
    @Column(name = "method", nullable = false, length = 10)
    private String method;

    /** A request payload (érzékeny mezők maszkolva) */
    @Column(name = "request_payload", columnDefinition = "TEXT")
    private String requestPayload;

    /** Entitás before/after diff (rollback-hez) */
    @Column(name = "changes_json", columnDefinition = "TEXT")
    private String changesJson;

    /** HTTP válasz státusz kód */
    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    /** Az érintett entitás típusa (Device, User, stb.) */
    @Column(name = "entity_type", length = 100)
    private String entityType;

    /** Az érintett entitás ID-ja */
    @Column(name = "entity_id")
    private Long entityId;
}