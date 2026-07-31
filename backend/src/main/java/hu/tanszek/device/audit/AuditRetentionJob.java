package hu.tanszek.device.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.tanszek.device.audit.entity.AuditLog;
import hu.tanszek.device.audit.repository.AuditLogRepository;
import hu.tanszek.device.common.ScheduledJobMonitoring;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * AuditRetentionJob — heti 1x (vasárnap 03:00) futó cleanup.
 *
 * <p>Retention policy:
 * <ul>
 *   <li><b>1+ éves rekordok</b> archiválása NDJSON.gz formátumban a
 *       /var/backups/archive/audit/YYYY/ mappába (audit_archive_data volume mount)</li>
 *   <li><b>5+ éves rekordok</b> végleges törlése a DB-ből</li>
 * </ul>
 *
 * <p>Threshold-ek az {@code audit.retention-years} (5) config-ból jönnek.
 *
 * <p>Email alert küldése a ScheduledJobMonitoring wrapper-en keresztül,
 * ha bármi hiba. A cleanup nem retry-ol automatikusan, a következő heti
 * futás újrapróbálkozik.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditRetentionJob {

    /** Threshold: 1 év (archiválás) */
    private static final Duration ARCHIVE_THRESHOLD = Duration.ofDays(365);

    @Value("${audit.archive-path:/var/backups/archive/audit}")
    private String archivePath;

    @Value("${audit.retention-years:5}")
    private int retentionYears;

    private final AuditLogRepository auditLogRepository;
    private final ScheduledJobMonitoring jobMonitoring;
    private final ObjectMapper objectMapper;

    /**
     * Heti futás vasárnap 03:00-kor.
     *
     * <p>Cron: {@code 0 0 3 ? * SUN} (másodperc, perc, óra, nap, hónap, hétkönap).
     */
    @Scheduled(cron = "0 0 3 ? * SUN")
    public void run() {
        jobMonitoring.run("audit-retention-job", this::executeRetention);
    }

    /**
     * Tényleges retention logika.
     *
     * <p>1. 1+ éves rekordok exportálása NDJSON.gz formátumban
     * 2. 5+ éves rekordok törlése
     */
    public void executeRetention() {
        Instant now = Instant.now();

        // 1. ARCHIVE: 1+ éves rekordok
        Instant archiveCutoff = now.minus(ARCHIVE_THRESHOLD);
        archiveOldRecords(archiveCutoff);

        // 2. DELETE: 5+ éves rekordok
        Instant deleteCutoff = now.minus(Duration.ofDays(retentionYears * 365L));
        int deletedCount = auditLogRepository.deleteByTimestampBefore(deleteCutoff);

        if (deletedCount > 0) {
            log.info("Audit retention deleted {} records older than {}", deletedCount, deleteCutoff);
        } else {
            log.debug("Audit retention found no records to delete older than {}", deleteCutoff);
        }
    }

    /**
     * Régi rekordok exportálása NDJSON.gz fájlba.
     *
     * <p>Fájl formátum: {archive_path}/YYYY/audit-YYYY-MM-DD.ndjson.gz
     * (a YYYY a retention év, az MM-DD a backup dátum).
     */
    private void archiveOldRecords(Instant cutoff) {
        List<AuditLog> recordsToArchive = auditLogRepository.findByTimestampBefore(cutoff);

        if (recordsToArchive.isEmpty()) {
            log.debug("No audit records to archive (cutoff: {})", cutoff);
            return;
        }

        // Csoportosítás évente (YYYY mappa struktúra)
        java.util.Map<Integer, List<AuditLog>> recordsByYear = new java.util.HashMap<>();
        for (AuditLog record : recordsToArchive) {
            int year = LocalDate.ofInstant(record.getTimestamp(), java.time.ZoneId.systemDefault()).getYear();
            recordsByYear.computeIfAbsent(year, k -> new java.util.ArrayList<>()).add(record);
        }

        // Fájlok kiírása
        for (java.util.Map.Entry<Integer, List<AuditLog>> entry : recordsByYear.entrySet()) {
            int year = entry.getKey();
            List<AuditLog> records = entry.getValue();

            try {
                Path archiveFile = writeArchiveFile(year, records);
                log.info("Archived {} audit records for year {} to {}", records.size(), year, archiveFile);
            } catch (IOException e) {
                log.error("Failed to write archive file for year {}", year, e);
                throw new RuntimeException("Archive write failed for year " + year, e);
            }
        }

        // Archiválás utáni DB törlés: az exportált rekordok eltávolítása
        int archivedCount = recordsToArchive.size();
        auditLogRepository.deleteAll(recordsToArchive);
        log.info("Archived and deleted {} audit records from DB (cutoff: {})", archivedCount, cutoff);
    }

    /**
     * NDJSON.gz fájl kiírása az archive mappába.
     */
    private Path writeArchiveFile(int year, List<AuditLog> records) throws IOException {
        String filename = "audit-" + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".ndjson.gz";
        Path targetPath = Paths.get(archivePath, String.valueOf(year), filename);
        Files.createDirectories(targetPath.getParent());

        try (OutputStream fileOut = Files.newOutputStream(targetPath);
             GZIPOutputStream gzipOut = new GZIPOutputStream(fileOut);
             BufferedOutputStream bufferedOut = new BufferedOutputStream(gzipOut)) {

            for (AuditLog record : records) {
                String line = objectMapper.writeValueAsString(record);
                bufferedOut.write(line.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                bufferedOut.write('\n');
            }
        }

        return targetPath;
    }

    /**
     * JSON string escape (escape: ", \, newline, carriage return).
     */
    private String escapeJson(String input) {
        if (input == null) return "";
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}