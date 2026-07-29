package hu.tanszek.device.crypto;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * DefaultCryptoService — AES-GCM 256 + SHA-256 implementáció.
 *
 * <p>Az AES-256 kulcs az alkalmazás indításakor töltődik be a
 * {@code CRYPTO_AES_KEY} env var-ból (base64 kódolás). A {@link PostConstruct}
 * fail-fast viselkedéssel rendelkezik: ha a kulcs hossza nem 32 byte
 * (256 bit), az alkalmazás indítása sikertelen.
 *
 * <p>A {@link #encrypt(String)} metódus minden hívásban új random 12-byte
 * IV-t generál (SecureRandom-ból), és a IV-t a ciphertext prefix-eként
 * tárolja ({@code iv:ciphertext} base64 formátumban). Ez biztosítja, hogy
 * azonos plaintext különböző ciphertext-et eredményez (IND-CPA biztonság).
 *
 * <p>A GCM mód egy 128-bit authentication tag-et is generál, amelyet a
 * ciphertext végéhez fűz — ez biztosítja az integritás-védelmet (tampering
 * esetén a {@link #decrypt(String)} {@link CryptoException}-t dob).
 */
@Slf4j
@Service
public class DefaultCryptoService implements CryptoService {

    /** AES-GCM IV hossza (12 byte = 96 bit, NIST ajánlás) */
    private static final int GCM_IV_LENGTH = 12;

    /** AES-GCM authentication tag hossza (128 bit) */
    private static final int GCM_TAG_LENGTH = 128;

    /** AES kulcs hossza (32 byte = 256 bit) */
    private static final int AES_KEY_LENGTH = 32;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Konstruktor — a kulcsot az env var-ból olvassa, dekódolja, és SecretKey-ként tárolja.
     *
     * @param aesKeyBase64 base64 kódolt 256-bit AES kulcs (32 byte dekódolva)
     * @throws IllegalStateException ha a kulcs érvénytelen (nem 32 byte)
     */
    public DefaultCryptoService(@Value("${crypto.aes-key}") String aesKeyBase64) {
        if (aesKeyBase64 == null || aesKeyBase64.isBlank()) {
            throw new IllegalStateException(
                    "CRYPTO_AES_KEY env var is missing. Run scripts/bootstrap.sh to generate one."
            );
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(aesKeyBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("CRYPTO_AES_KEY is not valid base64", e);
        }

        if (keyBytes.length != AES_KEY_LENGTH) {
            throw new IllegalStateException(
                    "CRYPTO_AES_KEY must be 32 bytes (256 bits) after base64 decode, got " + keyBytes.length
            );
        }

        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        log.info("DefaultCryptoService initialized with 256-bit AES-GCM key");
    }

    @Override
    public String encrypt(String plainText) throws CryptoException {
        if (plainText == null) {
            throw new CryptoException("plainText is null");
        }

        try {
            // Random IV generálása
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Cipher inicializálás GCM módban
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            // Titkosítás
            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // IV + ciphertext összefűzése (iv prefix)
            byte[] ivPlusCipher = new byte[GCM_IV_LENGTH + cipherBytes.length];
            System.arraycopy(iv, 0, ivPlusCipher, 0, GCM_IV_LENGTH);
            System.arraycopy(cipherBytes, 0, ivPlusCipher, GCM_IV_LENGTH, cipherBytes.length);

            // Base64 kódolás
            return Base64.getEncoder().encodeToString(ivPlusCipher);
        } catch (Exception e) {
            throw new CryptoException("Encryption failed", e);
        }
    }

    @Override
    public String decrypt(String cipherText) throws CryptoException {
        if (cipherText == null || cipherText.isBlank()) {
            throw new CryptoException("cipherText is null or empty");
        }

        try {
            // Base64 dekódolás
            byte[] ivPlusCipher = Base64.getDecoder().decode(cipherText);

            if (ivPlusCipher.length < GCM_IV_LENGTH + 16) {
                throw new CryptoException("Ciphertext too short");
            }

            // IV és ciphertext szétválasztása
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherBytes = new byte[ivPlusCipher.length - GCM_IV_LENGTH];
            System.arraycopy(ivPlusCipher, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(ivPlusCipher, GCM_IV_LENGTH, cipherBytes, 0, cipherBytes.length);

            // Cipher inicializálás GCM visszafejtéshez
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            // Visszafejtés (a GCM integritás-védelme miatt sikertelen, ha a ciphertext módosítva lett)
            byte[] plainBytes = cipher.doFinal(cipherBytes);

            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new CryptoException("Decryption failed (tampering or wrong key)", e);
        }
    }

    @Override
    public String sha256(String input) {
        if (input == null) {
            throw new IllegalArgumentException("input is null");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}