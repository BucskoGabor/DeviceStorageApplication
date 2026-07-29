package hu.tanszek.device.audit.controller;

import hu.tanszek.device.audit.entity.AuditLog;
import hu.tanszek.device.audit.repository.AuditLogRepository;
import hu.tanszek.device.auth.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AuditController — audit log listázás.
 * A rollback endpoint a Task 3.7-ből (AuditRollbackController) van külön.
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private static final int MAX_PAGE_SIZE = 50;

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    @RequirePermission("AUDIT_READ")
    public ResponseEntity<Map<String, Object>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String userEmail,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId
    ) {
        if (size > MAX_PAGE_SIZE) size = MAX_PAGE_SIZE;
        if (size < 1) size = 1;
        if (page < 0) page = 0;

        var pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());

        Page<AuditLog> result;
        if (userEmail != null && !userEmail.isBlank()) {
            result = auditLogRepository.findByUserEmailOrderByTimestampDesc(userEmail, pageable);
        } else if (entityType != null && entityId != null) {
            result = auditLogRepository.findByEntityTypeAndEntityId(entityType, entityId, pageable);
        } else {
            result = auditLogRepository.findAll(pageable);
        }

        return ResponseEntity.ok(Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "size", result.getSize(),
                "number", result.getNumber()
        ));
    }
}
