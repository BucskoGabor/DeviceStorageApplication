package hu.tanszek.device.attachment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import hu.tanszek.device.attachment.entity.DeviceAttachment;
import hu.tanszek.device.attachment.repository.DeviceAttachmentRepository;
import hu.tanszek.device.audit.AuditTarget;
import hu.tanszek.device.common.BusinessValidationException;
import hu.tanszek.device.common.ResourceNotFoundException;
import hu.tanszek.device.device.entity.Device;
import hu.tanszek.device.device.repository.DeviceRepository;
import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AttachmentService — device-ökhöz csatolt fájlok kezelése.
 *
 * <p>Fájl tárolás: {@code ./uploads/devices/{device_id}/{uuid}.{ext}} lokális volume mount-on
 * ({@code uploads_data:/var/uploads}). A DB csak a {@code storage_path} mezőben tárolja az
 * útvonalat.
 *
 * <p>Limits:
 *
 * <ul>
 *   <li>Max 5MB/fájl (validálás upload előtt)
 *   <li>Max 5 fájl/device (countByDeviceId check)
 *   <li>Mime type whitelist: image/*, application/pdf, application/msword,
 *       application/vnd.openxmlformats-officedocument.*
 * </ul>
 *
 * <p>Cascade policy: device törlésekor az attachment rekordok ÉS a fizikai fájlok is törlődnek
 * (lásd {@link #deleteByDevice}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentService {

  /** Max fájlméret (5 MB) */
  private static final long MAX_FILE_SIZE = 5L * 1024L * 1024L;

  /** Max fájlok száma device-onként */
  private static final int MAX_FILES_PER_DEVICE = 5;

  /** Whitelist mime type-ok */
  private static final Set<String> ALLOWED_MIME_TYPES =
      Set.of(
          // Képek
          "image/jpeg",
          "image/png",
          "image/gif",
          "image/webp",
          // PDF
          "application/pdf",
          // Office dokumentumok
          "application/msword",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // .xlsx
          // Szöveges fájlok
          "text/plain");

  private final DeviceAttachmentRepository attachmentRepository;
  private final DeviceRepository deviceRepository;
  private final AppUserRepository userRepository;

  @Value("${upload.base-path:/var/uploads}")
  private String uploadBasePath;

  /**
   * Fájl feltöltése egy device-hoz.
   *
   * <p>Validáció:
   *
   * <ul>
   *   <li>Device létezik
   *   <li>User létezik (uploader)
   *   <li>Fájl méret <= 5MB
   *   <li>Fájlok száma a device-on < 5
   *   <li>Mime type a whitelist-ben
   * </ul>
   *
   * @param deviceId a device ID-ja
   * @param file a feltöltött fájl
   * @param uploadedByUserId a feltöltő user ID-ja
   * @return az új DeviceAttachment
   */
  @AuditTarget(entityType = "DeviceAttachment", action = "upload")
  @Transactional
  public DeviceAttachment upload(Long deviceId, MultipartFile file, Long uploadedByUserId) {
    // 1. Validáció: device + user létezik
    Device device =
        deviceRepository
            .findById(deviceId)
            .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));
    AppUser uploadedBy =
        userRepository
            .findById(uploadedByUserId)
            .orElseThrow(
                () -> new ResourceNotFoundException("User not found: " + uploadedByUserId));

    // 2. Fájlméret limit
    if (file.getSize() > MAX_FILE_SIZE) {
      throw new BusinessValidationException(
          "attachmentTooLarge", "File size exceeds 5MB limit (got " + file.getSize() + " bytes)");
    }

    // 3. Mime type whitelist
    String mimeType = file.getContentType();
    if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType)) {
      throw new BusinessValidationException(
          "attachmentInvalidMimeType",
          "Mime type not allowed: " + mimeType + " (allowed: " + ALLOWED_MIME_TYPES + ")");
    }

    // 4. Device-onkénti fájlszám limit
    long currentCount = attachmentRepository.countByDeviceId(deviceId);
    if (currentCount >= MAX_FILES_PER_DEVICE) {
      throw new BusinessValidationException(
          "attachmentMaxPerDeviceExceeded",
          "Device already has " + currentCount + " attachments (max " + MAX_FILES_PER_DEVICE + ")");
    }

    // 5. Fájl fizikai tárolása
    String originalFilename = file.getOriginalFilename();
    String extension = extractExtension(originalFilename);
    String uuidFilename = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
    Path targetPath = Paths.get(uploadBasePath, "devices", String.valueOf(deviceId), uuidFilename);

    try {
      Files.createDirectories(targetPath.getParent());
      file.transferTo(targetPath);
    } catch (IOException e) {
      log.error("Failed to store attachment file: {}", targetPath, e);
      throw new BusinessValidationException(
          "attachmentStorageError", "Failed to store file: " + e.getMessage());
    }

    // 6. DB rekord
    DeviceAttachment attachment =
        DeviceAttachment.builder()
            .device(device)
            .fileName(originalFilename != null ? originalFilename : uuidFilename)
            .mimeType(mimeType)
            .sizeBytes(file.getSize())
            .uploadedAt(Instant.now())
            .uploadedBy(uploadedBy)
            .storagePath(targetPath.toString())
            .build();

    DeviceAttachment saved = attachmentRepository.save(attachment);

    log.info(
        "Attachment uploaded: device={}, file={}, size={}",
        deviceId,
        originalFilename,
        file.getSize());
    return saved;
  }

  /**
   * Fájl törlése ID alapján.
   *
   * @param attachmentId az attachment ID-ja
   */
  @AuditTarget(entityType = "DeviceAttachment", action = "delete")
  @Transactional
  public void delete(Long attachmentId) {
    DeviceAttachment attachment =
        attachmentRepository
            .findById(attachmentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Attachment not found: " + attachmentId));

    // Fájl törlése a fájlrendszerből
    try {
      Files.deleteIfExists(Paths.get(attachment.getStoragePath()));
    } catch (IOException e) {
      log.warn("Failed to delete attachment file: {}", attachment.getStoragePath(), e);
      // A DB rekordot akkor is töröljük, hogy az inkonzisztencia ne maradjon fenn
    }

    attachmentRepository.delete(attachment);

    log.info("Attachment deleted: id={}", attachmentId);
  }

  /**
   * Összes attachment törlése egy device-ról (device törléskor hívódik).
   *
   * <p>A fizikai fájlok is törlődnek.
   */
  @Transactional
  public void deleteByDevice(Long deviceId) {
    List<DeviceAttachment> attachments = attachmentRepository.findByDeviceId(deviceId);
    for (DeviceAttachment attachment : attachments) {
      try {
        Files.deleteIfExists(Paths.get(attachment.getStoragePath()));
      } catch (IOException e) {
        log.warn("Failed to delete attachment file: {}", attachment.getStoragePath(), e);
      }
      attachmentRepository.delete(attachment);
    }
    log.info("All attachments deleted for device: {} (count: {})", deviceId, attachments.size());
  }

  /** Device összes attachment listája. */
  @Transactional(readOnly = true)
  public List<DeviceAttachment> findByDevice(Long deviceId) {
    return attachmentRepository.findByDeviceId(deviceId);
  }

  /**
   * Fájl bináris tartalmának betöltése.
   *
   * <p>A fizikai fájl a {@code storagePath} mezőben tárolt útvonalon található. A hívó fél a {@code
   * Content-Type}-ot a {@link DeviceAttachment#getMimeType()} mezőből tudja beállítani.
   *
   * @param attachmentId az attachment azonosítója
   * @return a fájl bináris tartalma (byte[])
   * @throws ResourceNotFoundException ha az attachment vagy a fizikai fájl nem található
   */
  @Transactional(readOnly = true)
  public byte[] loadFileBytes(Long attachmentId) {
    DeviceAttachment attachment =
        attachmentRepository
            .findById(attachmentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Attachment not found: " + attachmentId));

    Path path = Paths.get(attachment.getStoragePath());
    if (!Files.exists(path)) {
      log.error("Attachment {} physical file missing: {}", attachmentId, path);
      throw new ResourceNotFoundException("Attachment file missing on disk: " + attachmentId);
    }

    try {
      return Files.readAllBytes(path);
    } catch (IOException e) {
      log.error("Failed to read attachment file: {}", path, e);
      throw new BusinessValidationException(
          "attachmentReadError", "Failed to read attachment file: " + e.getMessage());
    }
  }

  /** Fájl kiterjesztés kinyerése (pl. "report.pdf" → "pdf"). */
  private String extractExtension(String filename) {
    if (filename == null) return "";
    int dotIndex = filename.lastIndexOf('.');
    if (dotIndex == -1 || dotIndex == filename.length() - 1) return "";
    return filename.substring(dotIndex + 1).toLowerCase();
  }
}
