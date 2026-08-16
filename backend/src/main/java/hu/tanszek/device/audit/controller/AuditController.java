package hu.tanszek.device.audit.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import hu.tanszek.device.audit.entity.AuditLog;
import hu.tanszek.device.audit.repository.AuditLogRepository;
import hu.tanszek.device.auth.RequirePermission;
import hu.tanszek.device.crypto.CryptoService;
import hu.tanszek.device.user.repository.AppUserRepository;

import lombok.RequiredArgsConstructor;

/** AuditController — audit log listázás. */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

  private static final int MAX_PAGE_SIZE = 50;

  private final AuditLogRepository auditLogRepository;
  private final AppUserRepository userRepository;
  private final CryptoService cryptoService;

  public record AuditLogDto(
      Long id,
      Instant timestamp,
      String userEmail,
      String endpoint,
      String method,
      String requestPayload,
      String changesJson,
      int httpStatus,
      String entityType,
      Long entityId,
      Instant createdAt,
      Instant updatedAt) {

    public static AuditLogDto fromEntity(
        AuditLog log, AppUserRepository userRepository, CryptoService cryptoService) {
      String resolvedEmail = log.getUserEmail();
      if (resolvedEmail != null && resolvedEmail.length() == 64 && !resolvedEmail.contains("@")) {
        resolvedEmail =
            userRepository
                .findByEmailHash(resolvedEmail)
                .map(
                    u -> {
                      try {
                        return cryptoService.decrypt(u.getEmailEncrypted());
                      } catch (Exception e) {
                        return log.getUserEmail();
                      }
                    })
                .orElse(log.getUserEmail());
      }
      return new AuditLogDto(
          log.getId(),
          log.getTimestamp(),
          resolvedEmail,
          log.getEndpoint(),
          log.getMethod(),
          log.getRequestPayload(),
          log.getChangesJson(),
          log.getHttpStatus(),
          log.getEntityType(),
          log.getEntityId(),
          log.getCreatedAt(),
          log.getUpdatedAt());
    }
  }

  @GetMapping
  @RequirePermission("AUDIT_READ")
  public ResponseEntity<Map<String, Object>> findAll(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String userEmail,
      @RequestParam(required = false) String entityType,
      @RequestParam(required = false) Long entityId) {
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

    var content =
        result.getContent().stream()
            .map(log -> AuditLogDto.fromEntity(log, userRepository, cryptoService))
            .toList();

    return ResponseEntity.ok(
        Map.of(
            "content", content,
            "totalElements", result.getTotalElements(),
            "totalPages", result.getTotalPages(),
            "size", result.getSize(),
            "number", result.getNumber()));
  }
}
