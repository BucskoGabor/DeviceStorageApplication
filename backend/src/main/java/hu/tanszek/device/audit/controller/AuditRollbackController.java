package hu.tanszek.device.audit.controller;

import hu.tanszek.device.audit.AuditRollbackService;
import hu.tanszek.device.audit.entity.AuditLog;
import hu.tanszek.device.auth.RequirePermission;
import hu.tanszek.device.audit.controller.AuditController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AuditRollbackController — POST /api/audit/rollback/{id}.
 *
 * <p>Külön controller (nem az {@link AuditController}-ba van), mert a rollback
 * művelet más authorizációt és side-effectet igényel, mint a lista.
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditRollbackController {

    private final AuditRollbackService rollbackService;

    @PostMapping("/rollback/{id}")
    @RequirePermission("AUDIT_ROLLBACK")
    public ResponseEntity<AuditLog> rollback(@PathVariable Long id) {
        AuditLog rollbackLog = rollbackService.rollback(id);
        return ResponseEntity.ok(rollbackLog);
    }
}
