package hu.tanszek.device.attachment.controller;

import hu.tanszek.device.attachment.entity.DeviceAttachment;
import hu.tanszek.device.attachment.repository.DeviceAttachmentRepository;
import hu.tanszek.device.attachment.AttachmentService;
import hu.tanszek.device.auth.RequirePermission;
import hu.tanszek.device.common.ResourceNotFoundException;
import hu.tanszek.device.device.entity.Device;
import hu.tanszek.device.device.repository.DeviceRepository;
import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * AttachmentController — REST endpointok a device-ök fájlainak kezeléséhez.
 *
 * Endpoints:
 * - GET /api/devices/{deviceId}/attachments
 * - POST /api/devices/{deviceId}/attachments (multipart/form-data)
 * - DELETE /api/attachments/{id}
 */
@RestController
@RequiredArgsConstructor
public class AttachmentController {

    private final DeviceAttachmentRepository attachmentRepository;
    private final DeviceRepository deviceRepository;
    private final AppUserRepository userRepository;
    private final AttachmentService attachmentService;

    @GetMapping("/api/devices/{deviceId}/attachments")
    @RequirePermission("DEVICE_READ")
    public ResponseEntity<List<DeviceAttachment>> findByDevice(@PathVariable Long deviceId) {
        if (!deviceRepository.existsById(deviceId)) {
            throw new ResourceNotFoundException("Device not found: " + deviceId);
        }
        return ResponseEntity.ok(attachmentRepository.findByDeviceId(deviceId));
    }

    @PostMapping("/api/devices/{deviceId}/attachments")
    @RequirePermission("DEVICE_UPDATE")
    public ResponseEntity<DeviceAttachment> upload(
            @PathVariable Long deviceId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        // A Spring Security context-ből kinyerjük az email_hash-t (CustomUserDetailsService állítja be)
        String emailHash = authentication.getName();

        // User lekérés
        AppUser user = userRepository.findByEmailHash(emailHash)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + emailHash));

        DeviceAttachment attachment = attachmentService.upload(deviceId, file, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(attachment);
    }

    @DeleteMapping("/api/attachments/{id}")
    @RequirePermission("DEVICE_UPDATE")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        attachmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
