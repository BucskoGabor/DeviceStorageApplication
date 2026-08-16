package hu.tanszek.device.software;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import hu.tanszek.device.common.BusinessValidationException;
import hu.tanszek.device.common.ResourceNotFoundException;
import hu.tanszek.device.crypto.CryptoService;
import hu.tanszek.device.software.entity.Software;
import hu.tanszek.device.software.repository.SoftwareRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tesztek a {@link SoftwareService}-hez.
 *
 * <p>Teszteli:
 *
 * <ul>
 *   <li>{@code create}: sikeres, üres név tiltva, licence key titkosítva
 *   <li>{@code update}: partial update (csak name, csak licenseKey, mindkettő, egyik sem)
 *   <li>{@code update}: nem található szoftver esetén kivétel
 *   <li>{@code delete}: sikeres, nem található esetén kivétel
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SoftwareServiceTest {

  @Mock private SoftwareRepository softwareRepository;
  @Mock private CryptoService cryptoService;

  @InjectMocks private SoftwareService softwareService;

  @Test
  void create_encryptsLicenseKeyAndSaves() {
    when(cryptoService.encrypt("ABCD-1234-EFGH-5678")).thenReturn("encrypted-blob");
    when(softwareRepository.save(any(Software.class)))
        .thenAnswer(
            inv -> {
              Software sw = inv.getArgument(0);
              sw.setId(1L);
              return sw;
            });

    Software result = softwareService.create("AutoCAD", "ABCD-1234-EFGH-5678");

    assertThat(result.getId()).isEqualTo(1L);
    assertThat(result.getName()).isEqualTo("AutoCAD");
    assertThat(result.getLicenseKeyEncrypted()).isEqualTo("encrypted-blob");

    ArgumentCaptor<Software> captor = ArgumentCaptor.forClass(Software.class);
    verify(softwareRepository).save(captor.capture());
    assertThat(captor.getValue().getLicenseKeyEncrypted()).isEqualTo("encrypted-blob");
  }

  @Test
  void create_rejectsBlankName() {
    assertThatThrownBy(() -> softwareService.create("", "ABCD-1234"))
        .isInstanceOf(BusinessValidationException.class)
        .hasMessageContaining("cannot be blank");

    verify(softwareRepository, never()).save(any());
  }

  @Test
  void update_changesNameOnlyWhenLicenseKeyNull() {
    Software existing =
        Software.builder().name("OldName").licenseKeyEncrypted("old-encrypted").build();
    existing.setId(5L);
    when(softwareRepository.findById(5L)).thenReturn(Optional.of(existing));
    when(softwareRepository.save(any(Software.class))).thenAnswer(inv -> inv.getArgument(0));

    Software result = softwareService.update(5L, "NewName", null);

    assertThat(result.getName()).isEqualTo("NewName");
    assertThat(result.getLicenseKeyEncrypted()).isEqualTo("old-encrypted");
    verify(cryptoService, never()).encrypt(any());
  }

  @Test
  void update_reEncryptsLicenseKeyWhenChanged() {
    Software existing =
        Software.builder().name("AutoCAD").licenseKeyEncrypted("old-encrypted").build();
    existing.setId(5L);
    when(softwareRepository.findById(5L)).thenReturn(Optional.of(existing));
    when(cryptoService.encrypt("NEW-KEY-9999")).thenReturn("new-encrypted");
    when(softwareRepository.save(any(Software.class))).thenAnswer(inv -> inv.getArgument(0));

    Software result = softwareService.update(5L, null, "NEW-KEY-9999");

    assertThat(result.getName()).isEqualTo("AutoCAD");
    assertThat(result.getLicenseKeyEncrypted()).isEqualTo("new-encrypted");
  }

  @Test
  void update_ignoresBlankLicenseKey() {
    Software existing =
        Software.builder().name("AutoCAD").licenseKeyEncrypted("old-encrypted").build();
    existing.setId(5L);
    when(softwareRepository.findById(5L)).thenReturn(Optional.of(existing));
    when(softwareRepository.save(any(Software.class))).thenAnswer(inv -> inv.getArgument(0));

    Software result = softwareService.update(5L, null, "");

    assertThat(result.getLicenseKeyEncrypted()).isEqualTo("old-encrypted");
    verify(cryptoService, never()).encrypt(any());
  }

  @Test
  void update_throwsWhenSoftwareNotFound() {
    when(softwareRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> softwareService.update(99L, "NewName", null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("99");
  }

  @Test
  void delete_succeedsWhenSoftwareExists() {
    when(softwareRepository.existsById(5L)).thenReturn(true);

    softwareService.delete(5L);

    verify(softwareRepository).deleteById(5L);
  }

  @Test
  void delete_throwsWhenSoftwareNotFound() {
    when(softwareRepository.existsById(99L)).thenReturn(false);

    assertThatThrownBy(() -> softwareService.delete(99L))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(softwareRepository, never()).deleteById(any());
  }
}
