package hu.tanszek.device.import_.controller;

import hu.tanszek.device.import_.dto.ImportPreviewResponse;
import hu.tanszek.device.import_.dto.ImportResult;
import hu.tanszek.device.import_.ImportService;
import hu.tanszek.device.auth.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * ImportController — frontend hívja az Excel import-hoz.
 *
 * Endpoints:
 * - POST /api/import/preview (multipart file upload, validáció szárazon)
 * - POST /api/import/execute (JSON body: ImportPreviewResponse, tényleges import)
 */
@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    @PostMapping("/preview")
    @RequirePermission("USER_MANAGE")
    public ResponseEntity<ImportPreviewResponse> preview(
            @RequestParam("file") MultipartFile file
    ) {
        try {
            ImportPreviewResponse response = importService.preview(file);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PostMapping("/execute")
    @RequirePermission("USER_MANAGE")
    public ResponseEntity<ImportResult> execute(@RequestBody ImportPreviewResponse preview) {
        ImportResult result = importService.execute(preview);
        return ResponseEntity.ok(result);
    }
}
