package hu.tanszek.device.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @RequirePermission — method-level permission check annotáció.
 *
 * <p>A controller method-okra alkalmazandó. Az aspektus ellenőrzi, hogy a bejelentkezett user
 * (SecurityContext-ből) rendelkezik-e a megadott permission-nel.
 *
 * <p>Használat:
 *
 * <pre>
 *   &#64;GetMapping("/api/devices")
 *   &#64;RequirePermission("DEVICE_READ")
 *   public List&lt;DeviceDto&gt; list() { ... }
 * </pre>
 *
 * <p>Ha a user nem rendelkezik a permission-nel, {@link
 * hu.tanszek.device.common.UnauthorizedActionException} dobódik {@code permissionDenied}
 * messageKey-jel, amit a {@code GlobalExceptionHandler} 403 Forbidden-ként kezel.
 *
 * <p>A row-level filter (lásd Task 3.2) nem itt fut — az service-szinten van, hogy a row-szintű
 * jogosultságot a permission check után ellenőrizze.
 *
 * @see hu.tanszek.device.auth.RequirePermissionAspect
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

  /**
   * A szükséges permission neve (pl. "DEVICE_READ", "USER_MANAGE").
   *
   * @return a permission neve
   */
  String value();
}
