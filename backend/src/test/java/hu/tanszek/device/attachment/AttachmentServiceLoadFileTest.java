package hu.tanszek.device.attachment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import hu.tanszek.device.attachment.entity.DeviceAttachment;
import hu.tanszek.device.attachment.repository.DeviceAttachmentRepository;
import hu.tanszek.device.common.BusinessValidationException;
import hu.tanszek.device.common.ResourceNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tesztek a {@link AttachmentService#loadFileBytes(Long)} metódushoz.
 *
 * <p>Teszteli:
 *
 * <ul>
 *   <li>Sikeres fájl olvasás (a fizikai fájl létezik)
 *   <li>Attachment rekord nem található → ResourceNotFoundException
 *   <li>Fizikai fájl hiányzik a lemezről → ResourceNotFoundException
 *   <li>Fájl olvasás IOException → BusinessValidationException
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AttachmentServiceLoadFileTest {

  @Mock private DeviceAttachmentRepository attachmentRepository;
  @Mock private hu.tanszek.device.device.repository.DeviceRepository deviceRepository;
  @Mock private hu.tanszek.device.user.repository.AppUserRepository userRepository;

  @InjectMocks private AttachmentService attachmentService;

  @Test
  void loadFileBytes_returnsContentWhenFileExists() throws IOException {
    // upload.base-path konfiguráció (ReflectionTestUtils — @Value inject)
    Path tempDir = Files.createTempDirectory("attachments-test");
    ReflectionTestUtils.setField(attachmentService, "uploadBasePath", tempDir.toString());

    Path deviceDir = Files.createDirectories(tempDir.resolve("devices").resolve("1"));
    Path filePath = deviceDir.resolve("test.txt");
    Files.writeString(filePath, "hello world");

    DeviceAttachment att =
        DeviceAttachment.builder()
            .id(42L)
            .fileName("test.txt")
            .mimeType("text/plain")
            .sizeBytes(11L)
            .storagePath(filePath.toString())
            .build();
    when(attachmentRepository.findById(42L)).thenReturn(Optional.of(att));

    byte[] result = attachmentService.loadFileBytes(42L);

    assertThat(result).isEqualTo("hello world".getBytes());

    // cleanup
    Files.deleteIfExists(filePath);
    Files.deleteIfExists(deviceDir);
    Files.deleteIfExists(tempDir.resolve("devices"));
    Files.deleteIfExists(tempDir);
  }

  @Test
  void loadFileBytes_throwsWhenAttachmentRecordNotFound() {
    when(attachmentRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> attachmentService.loadFileBytes(99L))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("99");
  }

  @Test
  void loadFileBytes_throwsWhenPhysicalFileMissing() {
    Path nonExistent = Path.of("/tmp/nonexistent-attachment-xyz/file.txt");
    DeviceAttachment att =
        DeviceAttachment.builder().id(42L).storagePath(nonExistent.toString()).build();
    when(attachmentRepository.findById(42L)).thenReturn(Optional.of(att));

    assertThatThrownBy(() -> attachmentService.loadFileBytes(42L))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("missing on disk");
  }

  @Test
  void loadFileBytes_throwsWhenDirectoryInsteadOfFile() throws IOException {
    Path tempDir = Files.createTempDirectory("attachments-test-dir");
    DeviceAttachment att =
        DeviceAttachment.builder().id(42L).storagePath(tempDir.toString()).build();
    when(attachmentRepository.findById(42L)).thenReturn(Optional.of(att));

    // Files.readAllBytes könyvtárra IOException-t dob
    assertThatThrownBy(() -> attachmentService.loadFileBytes(42L))
        .isInstanceOf(BusinessValidationException.class)
        .hasMessageContaining("attachmentReadError");

    Files.deleteIfExists(tempDir);
  }
}
