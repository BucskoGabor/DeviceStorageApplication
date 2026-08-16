package hu.tanszek.device.common;

/**
 * Általános üzleti logikai hiba — a felhasználó inputja érvénytelen, vagy a művelet megsértene egy
 * üzleti szabályt.
 *
 * <p>Példák: hibás current password, GROUP típusú location-ra való assign, MAINTENANCE státuszú
 * device törlése.
 *
 * <p>A {@code GlobalExceptionHandler} {@code 400 Bad Request}-ként kezeli (vagy a messageKey
 * alapján más státuszkódot).
 *
 * @see GlobalExceptionHandler
 */
public class BusinessValidationException extends RuntimeException {

  /** A hiba üzleti azonosítója (i18n fordításhoz) */
  private final String messageKey;

  public BusinessValidationException(String messageKey, String message) {
    super(message);
    this.messageKey = messageKey;
  }

  public BusinessValidationException(String messageKey, String message, Throwable cause) {
    super(message, cause);
    this.messageKey = messageKey;
  }

  public String getMessageKey() {
    return messageKey;
  }
}
