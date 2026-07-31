package hu.tanszek.device.common;

/**
 * Resource nem található hiba — a kért entitás (user, device, location, stb.) nem található az
 * adatbázisban a megadott ID-val.
 *
 * <p>A {@code GlobalExceptionHandler} {@code 404 Not Found}-ként kezeli.
 *
 * @see GlobalExceptionHandler
 */
public class ResourceNotFoundException extends RuntimeException {

  public ResourceNotFoundException(String message) {
    super(message);
  }

  public ResourceNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
