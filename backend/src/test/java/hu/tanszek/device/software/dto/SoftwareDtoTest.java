package hu.tanszek.device.software.dto;

import org.junit.jupiter.api.Test;

import hu.tanszek.device.software.entity.Software;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tesztek a {@link SoftwareDto}-hoz — különösen a licence maszkolás.
 *
 * <p>A maszkolás az encrypted blob utolsó 4 karakteréből készít {@code ****-****-****-XXXX}
 * formátumot. Ez biztonsági szempontból fontos: a frontend SOHA nem kapja meg a visszafejtett
 * kulcsot, ha a user nem rendelkezik SOFTWARE_LICENSE_VIEW permissionnel.
 */
class SoftwareDtoTest {

  @Test
  void maskFromEncrypted_returnsMaskedFormatWithLast4Chars() {
    String result = SoftwareDto.maskFromEncrypted("abcdefghij1234567890XYZ");
    assertThat(result).isEqualTo("****-****-****-9XYZ");
  }

  @Test
  void maskFromEncrypted_returnsEmptyMaskWhenTooShort() {
    assertThat(SoftwareDto.maskFromEncrypted("abc")).isEqualTo("****-****-****-");
  }

  @Test
  void maskFromEncrypted_returnsEmptyMaskWhenNull() {
    assertThat(SoftwareDto.maskFromEncrypted(null)).isEqualTo("****-****-****-");
  }

  @Test
  void fromEntity_withViewPermission_returnsDecryptedKey() {
    Software sw = software("AutoCAD", "encrypted-blob");

    SoftwareDto dto = SoftwareDto.fromEntity(sw, true, "DECRYPTED-KEY-1234");

    assertThat(dto.id()).isEqualTo(1L);
    assertThat(dto.name()).isEqualTo("AutoCAD");
    assertThat(dto.licenseKey()).isEqualTo("DECRYPTED-KEY-1234");
    assertThat(dto.licenseKeyMasked()).isNull();
  }

  @Test
  void fromEntity_withoutViewPermission_returnsMaskedOnly() {
    Software sw = software("AutoCAD", "encrypted-blob-1234567890ABCDEF");

    SoftwareDto dto = SoftwareDto.fromEntity(sw, false, null);

    assertThat(dto.id()).isEqualTo(1L);
    assertThat(dto.name()).isEqualTo("AutoCAD");
    assertThat(dto.licenseKey()).isNull();
    assertThat(dto.licenseKeyMasked()).isEqualTo("****-****-****-CDEF");
  }

  @Test
  void fromEntity_securityInvariant_neverReturnsBothFields() {
    Software sw = software("Test", "encrypted-blob-XXXX");

    SoftwareDto withPermission = SoftwareDto.fromEntity(sw, true, "secret-key");
    SoftwareDto withoutPermission = SoftwareDto.fromEntity(sw, false, null);

    // Defense-in-depth: pontosan az egyik mező kitöltött
    assertThat(withPermission.licenseKey()).isNotNull();
    assertThat(withPermission.licenseKeyMasked()).isNull();

    assertThat(withoutPermission.licenseKey()).isNull();
    assertThat(withoutPermission.licenseKeyMasked()).isNotNull();
  }

  private Software software(String name, String encrypted) {
    Software sw = Software.builder().name(name).licenseKeyEncrypted(encrypted).build();
    sw.setId(1L);
    return sw;
  }
}
