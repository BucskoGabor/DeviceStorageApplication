package hu.tanszek.device.crypto;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tesztek a {@link DefaultCryptoService}-hez.
 *
 * <p>Teszteli:
 *
 * <ul>
 *   <li>encrypt → decrypt round-trip (helyes visszafejtés)
 *   <li>Különböző plaintext-ek különböző ciphertext-eket adnak (random IV)
 *   <li>Ugyanaz a plaintext más-más IV-vel → más ciphertext (IND-CPA)
 *   <li>sha256 konzisztens és 64 karakter hosszú
 *   <li>Tampering (módosított ciphertext) → CryptoException
 *   <li>Üres / null input → CryptoException / IllegalArgumentException
 * </ul>
 */
class DefaultCryptoServiceTest {

  private static final String TEST_KEY_BASE64 =
      Base64.getEncoder().encodeToString(new byte[32]); // 32 zero bytes

  private DefaultCryptoService cryptoService;

  @BeforeEach
  void setUp() {
    cryptoService = new DefaultCryptoService(TEST_KEY_BASE64);
  }

  @Test
  void encryptThenDecryptRoundTrip() {
    String plainText = "admin@tanszek.local";
    String cipherText = cryptoService.encrypt(plainText);
    String decrypted = cryptoService.decrypt(cipherText);

    assertThat(decrypted).isEqualTo(plainText);
  }

  @Test
  void differentCipherTextsForSamePlainText() {
    // IND-CPA: azonos plaintext → különböző ciphertext (random IV)
    String plainText = "license-key-12345";
    Set<String> cipherTexts = new HashSet<>();

    for (int i = 0; i < 10; i++) {
      cipherTexts.add(cryptoService.encrypt(plainText));
    }

    // 10 encrypt hívás → 10 különböző ciphertext (random IV)
    assertThat(cipherTexts).hasSize(10);
  }

  @Test
  void differentCipherTextsForDifferentPlainTexts() {
    String ct1 = cryptoService.encrypt("alice");
    String ct2 = cryptoService.encrypt("bob");

    assertThat(ct1).isNotEqualTo(ct2);
  }

  @Test
  void sha256ConsistentAnd64Chars() {
    String hash = cryptoService.sha256("admin@tanszek.local");

    // SHA-256 hex = 64 karakter
    assertThat(hash).hasSize(64);
    // Ugyanaz a bemenet → ugyanaz a hash
    assertThat(cryptoService.sha256("admin@tanszek.local")).isEqualTo(hash);
    // Különböző bemenet → különböző hash
    assertThat(cryptoService.sha256("admin@tanszek.hu")).isNotEqualTo(hash);
  }

  @Test
  void decryptTamperedCipherTextThrowsCryptoException() {
    String plainText = "secret-message";
    String cipherText = cryptoService.encrypt(plainText);

    // Utolsó karakter módosítása (ciphertext tampering)
    char lastChar = cipherText.charAt(cipherText.length() - 1);
    char tamperedChar = (lastChar == 'A') ? 'B' : 'A';
    String tampered = cipherText.substring(0, cipherText.length() - 1) + tamperedChar;

    assertThatThrownBy(() -> cryptoService.decrypt(tampered)).isInstanceOf(CryptoException.class);
  }

  @Test
  void encryptNullThrowsCryptoException() {
    assertThatThrownBy(() -> cryptoService.encrypt(null)).isInstanceOf(CryptoException.class);
  }

  @Test
  void decryptNullOrEmptyThrowsCryptoException() {
    assertThatThrownBy(() -> cryptoService.decrypt(null)).isInstanceOf(CryptoException.class);
    assertThatThrownBy(() -> cryptoService.decrypt("")).isInstanceOf(CryptoException.class);
    assertThatThrownBy(() -> cryptoService.decrypt("   ")).isInstanceOf(CryptoException.class);
  }

  @Test
  void sha256NullThrowsIllegalArgumentException() {
    assertThatThrownBy(() -> cryptoService.sha256(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void invalidKeyLengthThrowsIllegalStateException() {
    String shortKey = Base64.getEncoder().encodeToString(new byte[16]); // 16 byte, túl rövid

    assertThatThrownBy(() -> new DefaultCryptoService(shortKey))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("32 bytes");
  }

  @Test
  void missingKeyThrowsIllegalStateException() {
    assertThatThrownBy(() -> new DefaultCryptoService(""))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("missing");
  }
}
