package hu.tanszek.device.common;

/**
 * Hitelesítési vagy jogosultsági hiba — a user nem jogosult a műveletre.
 *
 * <p>Példák: nincs bejelentkezve, lejárt token, nincs megfelelő permission.
 *
 * <p>A {@code GlobalExceptionHandler} {@code 401 Unauthorized} vagy
 * {@code 403 Forbidden}-ként kezeli (a messageKey alapján).
 *
 * @see GlobalExceptionHandler
 */
public class UnauthorizedActionException extends RuntimeException {

    /** A hiba üzleti azonosítója (i18n fordításhoz) */
    private final String messageKey;

    public UnauthorizedActionException(String messageKey, String message) {
        super(message);
        this.messageKey = messageKey;
    }

    public UnauthorizedActionException(String messageKey, String message, Throwable cause) {
        super(message, cause);
        this.messageKey = messageKey;
    }

    public String getMessageKey() {
        return messageKey;
    }
}