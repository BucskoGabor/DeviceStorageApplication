package hu.tanszek.device.crypto;

import hu.tanszek.device.user.entity.AppUser;

/**
 * CryptoService interface — titkosítási és hash műveletek.
 *
 * <p>A rendszer három fő kriptográfiai műveletet használ:
 * <ul>
 *   <li><b>AES-GCM 256</b> — {@code email_encrypted} és
 *       {@code license_key_encrypted} mezők tárolására (visszafejthető,
 *       admin megjelenítéshez)</li>
 *   <li><b>SHA-256</b> — {@code email_hash} és
 *       {@code refresh_tokens.token_hash} mezőkre (egyirányú, gyors
 *       kereséshez)</li>
 *   <li><b>Argon2id</b> — jelszavakhoz (Task 2.2-ben implementálva, a
 *       Spring Security {@code Argon2PasswordEncoder}-en keresztül)</li>
 * </ul>
 *
 * <p>Az {@link hu.tanszek.device.user.entity.AppUser} entitás használja az
 * encrypt(email) és sha256(email) metódusokat a mentéskor, a
 * {@link hu.tanszek.device.software.entity.Software} entitás pedig az
 * encrypt(licenseKey) hívást a {@code license_key_encrypted} mezőhöz.
 *
 * <p>Implementáció: {@link DefaultCryptoService}.
 *
 * @see DefaultCryptoService
 */
public interface CryptoService {

    /**
     * Szöveg AES-GCM 256 titkosítása base64 kódolással.
     *
     * <p>A visszaadott string formátuma: {@code <base64-iv>:<base64-ciphertext>}.
     * Az IV (Initialization Vector) random 12 byte, minden hívásban új,
     * így azonos plaintext különböző ciphertext-et eredményez.
     *
     * @param plainText a titkosítandó szöveg (UTF-8)
     * @return base64 kódolt {@code iv:ciphertext} string
     * @throws CryptoException ha a titkosítás sikertelen
     */
    String encrypt(String plainText) throws CryptoException;

    /**
     * AES-GCM 256 visszafejtés base64-ből.
     *
     * @param cipherText az {@code encrypt()} által visszaadott string
     * @return az eredeti szöveg
     * @throws CryptoException ha a visszafejtés sikertelen
     *         (pl. tampering, rossz kulcs)
     */
    String decrypt(String cipherText) throws CryptoException;

    /**
     * SHA-256 hash számítása, hex string formátumban (64 karakter).
     *
     * @param input a hashelendő szöveg
     * @return 64 karakter hosszú hex string (a SHA-256 32 byte-ja hex kódolva)
     */
    String sha256(String input);
}