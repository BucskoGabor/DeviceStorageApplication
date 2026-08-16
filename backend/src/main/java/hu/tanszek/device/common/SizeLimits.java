package hu.tanszek.device.common;

/**
 * SizeLimits — egységes méretkorlát konstansok a Bean Validation @Size annotációkhoz.
 *
 * <p>A háromszintű méretkorlát rendszer (Task 3.8):
 *
 * <ol>
 *   <li><b>Nginx</b> — frontend konténer nginx.conf {@code client_max_body_size 10M} (a /api/
 *       location block-ban, Task 1.1-ben beállítva)
 *   <li><b>Spring multipart</b> — application.yml {@code
 *       spring.servlet.multipart.max-file-size=10MB} (Task 1.2-ben beállítva)
 *   <li><b>Bean Validation</b> — DTO String mezők {@code @Size(max = ...)} (ez az interface)
 * </ol>
 *
 * <p>Használat a DTO-kban:
 *
 * <pre>
 *   public record UserDto(
 *       &#64;NotBlank &#64;Email String email,  // @Size nélkül, email validálja
 *       &#64;NotBlank &#64;Size(max = SizeLimits.NAME_MAX) String firstName,
 *       &#64;Size(max = SizeLimits.LONG_TEXT_MAX) String description
 *   ) {}
 * </pre>
 */
public final class SizeLimits {

  private SizeLimits() {}

  /** Rövid szövegek (nevek, típusok): max 100 karakter */
  public static final int SHORT_TEXT_MAX = 100;

  /** Közepes szövegek (címek, leírások): max 255 karakter */
  public static final int MEDIUM_TEXT_MAX = 255;

  /** Hosszú szövegek (kommentek, megjegyzések): max 500 karakter */
  public static final int LONG_TEXT_MAX = 500;

  /** Extra hosszú szövegek (audit log payload): max 10000 karakter */
  public static final int VERY_LONG_TEXT_MAX = 10000;

  /** Inventory number (device egyedi azonosító): max 50 karakter */
  public static final int INVENTORY_NUMBER_MAX = 50;

  /** Email cím: max 255 karakter (RFC 5321 szerinti) */
  public static final int EMAIL_MAX = 255;

  /** Password hash (Argon2id): max 255 karakter */
  public static final int PASSWORD_HASH_MAX = 255;

  /** URL: max 2048 karakter */
  public static final int URL_MAX = 2048;

  /** Audit log endpoint path: max 500 karakter */
  public static final int ENDPOINT_MAX = 500;

  /** i18n message key: max 100 karakter */
  public static final int MESSAGE_KEY_MAX = 100;

  /** File name: max 255 karakter */
  public static final int FILE_NAME_MAX = 255;

  /** MIME type: max 100 karakter */
  public static final int MIME_TYPE_MAX = 100;

  /** Storage path: max 500 karakter */
  public static final int STORAGE_PATH_MAX = 500;
}
