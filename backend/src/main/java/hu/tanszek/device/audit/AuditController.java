package hu.tanszek.device.audit;

import hu.tanszek.device.audit.entity.AuditLog;
import hu.tanszek.device.auth.RequirePermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AuditController — audit log lekérdezés és rollback endpointok.
 *
 * <p>Endpointok:
 * <ul>
 *   <li>POST /api/audit/rollback/{id} — audit log alapján rollback</li>
 * </ul>
 *
 * <p>Csak AUDIT_ROLLBACK permissionnel rendelkező user hívhatja (admin).
 */
@Slf4j
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditRollbackService rollbackService;

    /**
     * Audit log alapján rollback.
     *
     * <p>A {@code changes_json} {before, after} diff-et használja. A rollback
     * típustól függően:
     * <ul>
     *   <li>UPDATE: after → before (visszaállítás)</li>
     *   <li>CREATE: törli az entitást</li>
     *   <li>DELETE: újra létrehozza az entitást</li>
     * </ul>
     *
     * @param id az audit log ID-ja
     * @return az újonnan létrehozott rollback audit log
     */
    @PostMapping("/rollback/{id}")
    @RequirePermission("AUDIT_ROLLBACK")
    public ResponseEntity<AuditLog> rollback(@PathVariable Long id) {
        log.info("Audit rollback requested for audit log id={}", id);
        AuditLog rollbackLog = rollbackService.rollback(id);
        return ResponseEntity.ok(rollbackLog);
    }
}