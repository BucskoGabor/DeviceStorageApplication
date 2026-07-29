package hu.tanszek.device.import_;

import hu.tanszek.device.auth.RequirePermission;
import hu.tanszek.device.import_.dto.ImportPreviewResponse;
import hu.tanszek.device.import_.dto.ImportResult;
import hu.tanszek.device.common.UnauthorizedActionException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * ImportController — Excel import endpointok.
 *
 * <p>Endpointok:
 * <ul>
 *   <li>POST /api/import/preview — Excel feltöltés, validáció szárazon</li>
 *   <li>POST /api/import/execute — Preview response visszaküldése, tényleges import</li>
 * </ul>
 *
 * <p>Mindkét endpoint USER_MANAGE permissiont igényel (csak admin tölthet fel).
 */
@Slf4j
@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    /**
     * Preview — feltöltött Excel fájl validációja szárazon.
     *
     * <p>USER_MANAGE permission szükséges.
     */
    @PostMapping("/preview")
    @RequirePermission("USER_MANAGE")
    public ResponseEntity<ImportPreviewResponse> preview(
            @RequestParam("file") MultipartFile file
    ) {
        log.info("Excel import preview: filename={}, size={}", file.getOriginalFilename(), file.getSize());
        ImportPreviewResponse response = importService.preview(file);
        return ResponseEntity.ok(response);
    }

    /**
     * Execute — preview response tényleges importálása.
     *
     * <p>USER_MANAGE permission szükséges.
     */
    @PostMapping("/execute")
    @RequirePermission("USER_MANAGE")
    public ResponseEntity<ImportResult> execute(
            @Valid @RequestBody ImportPreviewResponse preview
    ) {
        log.info("Excel import execute: users={}, devices={}",
                preview.validUsers().size(), preview.validDevices().size());
        ImportResult result = importService.execute(preview);
        log.info("Import complete: {}", result);
        return ResponseEntity.ok(result);
    }
}