package hu.tanszek.device.crypto;

/**
 * CryptoService műveletek során fellépő kivétel.
 *
 * <p>A {@code CryptoException} runtime exception, mivel a titkosítási hibák (pl. rossz kulcs, IV
 * tampering) általában programozási hibát jelentenek, nem runtime feltételeket. Az ilyen hibákat a
 * {@code GlobalExceptionHandler} 500 Internal Server Error-ként kezeli.
 *
 * @see CryptoService
 */
public class CryptoException extends RuntimeException {

  public CryptoException(String message) {
    super(message);
  }

  public CryptoException(String message, Throwable cause) {
    super(message, cause);
  }
}
