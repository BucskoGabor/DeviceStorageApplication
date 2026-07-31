package hu.tanszek.device.attachment.controller;

import hu.tanszek.device.attachment.AttachmentService;
import hu.tanszek.device.attachment.entity.DeviceAttachment;
import hu.tanszek.device.attachment.repository.DeviceAttachmentRepository;
import hu.tanszek.device.auth.RequirePermission;
import hu.tanszek.device.common.ResourceNotFoundException;
import hu.tanszek.device.device.repository.DeviceRepository;
import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * AttachmentController — REST endpointok a device-ök fájlainak kezeléséhez.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /api/devices/{deviceId}/attachments — lista</li>
 *   <li>POST /api/devices/{deviceId}/attachments — feltöltés (multipart)</li>
 *   <li>DELETE /api/attachments/{id} — törlés</li>
 *   <li>GET /api/attachments/{id}/file?inline=true|false — letöltés vagy inline preview</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Attachment", description = "Eszköz mellékletek (képek, PDF, Office fájlok) feltöltése, listázása, letöltése és törlése")
public class AttachmentController {

    private final DeviceAttachmentRepository attachmentRepository;
    private final DeviceRepository deviceRepository;
    private final AppUserRepository userRepository;
    private final AttachmentService attachmentService;

    @Operation(summary = "Eszköz mellékletei", description = "Visszaadja az adott eszközhöz tartozó összes attachment rekordot.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sikeres lista"),
            @ApiResponse(responseCode = "404", description = "Eszköz nem található"),
            @ApiResponse(responseCode = "403", description = "DEVICE_READ permission hiányzik")
    })
    @GetMapping("/api/devices/{deviceId}/attachments")
    @RequirePermission("DEVICE_READ")
    public ResponseEntity<List<DeviceAttachment>> findByDevice(
            @Parameter(description = "Eszköz azonosító") @PathVariable Long deviceId) {
        if (!deviceRepository.existsById(deviceId)) {
            throw new ResourceNotFoundException("Device not found: " + deviceId);
        }
        return ResponseEntity.ok(attachmentRepository.findByDeviceId(deviceId));
    }

    @Operation(summary = "Melléklet feltöltése", description = "Multipart/form-data upload. " +
            "Limits: max 5MB/fájl, max 5 fájl/device, mime whitelist (image/*, PDF, Office, text/plain).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Melléklet feltöltve"),
            @ApiResponse(responseCode = "400", description = "Fájl méret vagy mime type nem megfelelő"),
            @ApiResponse(responseCode = "403", description = "DEVICE_UPDATE permission hiányzik")
    })
    @PostMapping("/api/devices/{deviceId}/attachments")
    @RequirePermission("DEVICE_UPDATE")
    public ResponseEntity<DeviceAttachment> upload(
            @Parameter(description = "Eszköz azonosító") @PathVariable Long deviceId,
            @Parameter(description = "Fájl (max 5MB)") @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        String emailHash = authentication.getName();
        AppUser user = userRepository.findByEmailHash(emailHash)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + emailHash));
        DeviceAttachment attachment = attachmentService.upload(deviceId, file, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(attachment);
    }

    @Operation(summary = "Melléklet törlése", description = "DB rekord + fizikai fájl törlése. " +
            "Ha a fájlrendszeri törlés fail, a DB rekord akkor is törlődik (inkonzisztencia elkerülése).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Melléklet törölve"),
            @ApiResponse(responseCode = "404", description = "Melléklet nem található"),
            @ApiResponse(responseCode = "403", description = "DEVICE_UPDATE permission hiányzik")
    })
    @DeleteMapping("/api/attachments/{id}")
    @RequirePermission("DEVICE_UPDATE")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Melléklet azonosító") @PathVariable Long id) {
        attachmentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/attachments/{id}/file?inline=true|false
     *
     * <p>Fájl bináris letöltése vagy inline preview-ja.
     * <ul>
     *   <li>Ha {@code inline=false} (default): {@code Content-Disposition: attachment} —
     *       a böngésző letölti a fájlt az eredeti néven</li>
     *   <li>Ha {@code inline=true}: {@code Content-Disposition: inline} —
     *       a böngésző megpróbálja megjeleníteni (image, PDF iframe, stb.)</li>
     * </ul>
     *
     * <p>A {@code Content-Type} a tárolt {@code mime_type} mezőből jön.
     * A Content-Disposition header UTF-8 filename kódolást használ (RFC 5987).
     */
    @Operation(summary = "Melléklet letöltése / preview", description = "Visszaadja a fájl bináris tartalmát. " +
            "Ha inline=true, a böngésző megjeleníti (image, PDF); ha inline=false (default), letölti.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fájl tartalom"),
            @ApiResponse(responseCode = "404", description = "Melléklet vagy fizikai fájl nem található"),
            @ApiResponse(responseCode = "403", description = "DEVICE_READ permission hiányzik")
    })
    @GetMapping("/api/attachments/{id}/file")
    @RequirePermission("DEVICE_READ")
    public ResponseEntity<byte[]> downloadFile(
            @Parameter(description = "Melléklet azonosító") @PathVariable Long id,
            @Parameter(description = "Ha true, inline preview (Content-Disposition: inline); ha false, letöltés (attachment)")
            @RequestParam(defaultValue = "false") boolean inline
    ) {
        DeviceAttachment attachment = attachmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found: " + id));

        byte[] fileBytes = attachmentService.loadFileBytes(id);

        String dispositionType = inline ? "inline" : "attachment";
        String filename = attachment.getFileName();
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        dispositionType + "; filename=\"" + encodedFilename + "\"; " +
                                "filename*=UTF-8''" + encodedFilename)
                .contentLength(fileBytes.length)
                .body(fileBytes);
    }
}
