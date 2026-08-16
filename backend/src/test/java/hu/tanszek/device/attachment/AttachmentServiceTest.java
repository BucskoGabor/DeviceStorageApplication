package hu.tanszek.device.attachment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import hu.tanszek.device.attachment.entity.DeviceAttachment;
import hu.tanszek.device.attachment.repository.DeviceAttachmentRepository;
import hu.tanszek.device.common.BusinessValidationException;
import hu.tanszek.device.common.ResourceNotFoundException;
import hu.tanszek.device.device.entity.Device;
import hu.tanszek.device.device.repository.DeviceRepository;
import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

  @Mock private DeviceAttachmentRepository attachmentRepository;
  @Mock private DeviceRepository deviceRepository;
  @Mock private AppUserRepository userRepository;

  @InjectMocks private AttachmentService attachmentService;

  private Device device;
  private AppUser user;
  private Path tempDir;

  @BeforeEach
  void setUp() throws IOException {
    tempDir = Files.createTempDirectory("attachments-upload-test");
    ReflectionTestUtils.setField(attachmentService, "uploadBasePath", tempDir.toString());

    device = Device.builder().id(1L).inventoryNumber("INV-001").build();
    user = AppUser.builder().id(10L).emailHash("userHash").emailEncrypted("enc").build();
  }

  @Test
  void upload_success() {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "document.pdf", "application/pdf", "dummy pdf content".getBytes());

    when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
    when(userRepository.findById(10L)).thenReturn(Optional.of(user));
    when(attachmentRepository.countByDeviceId(1L)).thenReturn(2L);
    when(attachmentRepository.save(any(DeviceAttachment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    DeviceAttachment saved = attachmentService.upload(1L, file, 10L);

    assertThat(saved).isNotNull();
    assertThat(saved.getFileName()).isEqualTo("document.pdf");
    assertThat(saved.getMimeType()).isEqualTo("application/pdf");
    verify(attachmentRepository).save(any(DeviceAttachment.class));
  }

  @Test
  void upload_throwsWhenDeviceNotFound() {
    MockMultipartFile file =
        new MockMultipartFile("file", "test.pdf", "application/pdf", "data".getBytes());
    when(deviceRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> attachmentService.upload(1L, file, 10L))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void upload_throwsWhenUserNotFound() {
    MockMultipartFile file =
        new MockMultipartFile("file", "test.pdf", "application/pdf", "data".getBytes());
    when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
    when(userRepository.findById(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> attachmentService.upload(1L, file, 10L))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void upload_throwsWhenFileTooLarge() {
    byte[] largeBytes = new byte[6 * 1024 * 1024];
    MockMultipartFile file =
        new MockMultipartFile("file", "large.pdf", "application/pdf", largeBytes);
    when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
    when(userRepository.findById(10L)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> attachmentService.upload(1L, file, 10L))
        .isInstanceOf(BusinessValidationException.class)
        .satisfies(
            e ->
                assertThat(((BusinessValidationException) e).getMessageKey())
                    .isEqualTo("attachmentTooLarge"));
  }

  @Test
  void upload_throwsWhenInvalidMimeType() {
    MockMultipartFile file =
        new MockMultipartFile("file", "script.sh", "application/x-sh", "echo hi".getBytes());
    when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
    when(userRepository.findById(10L)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> attachmentService.upload(1L, file, 10L))
        .isInstanceOf(BusinessValidationException.class)
        .satisfies(
            e ->
                assertThat(((BusinessValidationException) e).getMessageKey())
                    .isEqualTo("attachmentInvalidMimeType"));
  }

  @Test
  void upload_throwsWhenMaxFilesReached() {
    MockMultipartFile file =
        new MockMultipartFile("file", "doc.pdf", "application/pdf", "data".getBytes());
    when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
    when(userRepository.findById(10L)).thenReturn(Optional.of(user));
    when(attachmentRepository.countByDeviceId(1L)).thenReturn(5L);

    assertThatThrownBy(() -> attachmentService.upload(1L, file, 10L))
        .isInstanceOf(BusinessValidationException.class)
        .satisfies(
            e ->
                assertThat(((BusinessValidationException) e).getMessageKey())
                    .isEqualTo("attachmentMaxPerDeviceExceeded"));
  }

  @Test
  void delete_success() throws IOException {
    Path filePath = tempDir.resolve("test-delete.txt");
    Files.writeString(filePath, "delete me");

    DeviceAttachment attachment =
        DeviceAttachment.builder().id(5L).storagePath(filePath.toString()).build();

    when(attachmentRepository.findById(5L)).thenReturn(Optional.of(attachment));

    attachmentService.delete(5L);

    verify(attachmentRepository).delete(attachment);
    assertThat(Files.exists(filePath)).isFalse();
  }

  @Test
  void delete_throwsWhenNotFound() {
    when(attachmentRepository.findById(5L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> attachmentService.delete(5L))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void deleteByDevice_success() throws IOException {
    Path filePath = tempDir.resolve("dev-file.txt");
    Files.writeString(filePath, "dev file");

    DeviceAttachment attachment =
        DeviceAttachment.builder().id(10L).storagePath(filePath.toString()).build();

    when(attachmentRepository.findByDeviceId(1L)).thenReturn(List.of(attachment));

    attachmentService.deleteByDevice(1L);

    verify(attachmentRepository).delete(attachment);
    assertThat(Files.exists(filePath)).isFalse();
  }

  @Test
  void findByDevice_success() {
    DeviceAttachment attachment = DeviceAttachment.builder().id(10L).build();
    when(attachmentRepository.findByDeviceId(1L)).thenReturn(List.of(attachment));

    List<DeviceAttachment> list = attachmentService.findByDevice(1L);
    assertThat(list).hasSize(1);
  }
}
