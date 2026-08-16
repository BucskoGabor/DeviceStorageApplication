package hu.tanszek.device.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @AuditTarget — service metódus szintű annotáció az AOP audit interceptor-hoz.
 *
 * <p>A service metódusokra alkalmazandó, amelyek módosítják az adatbázist. Az AOP audit interceptor
 * a metódus ELŐTT és UTÁN rögzíti az entity state-et, és a {@code changes_json} mezőben tárolja a
 * diff-et.
 *
 * <p>Használat:
 *
 * <pre>
 *   &#64;AuditTarget(entityType = "Device", action = "create")
 *   public DeviceDto createDevice(CreateDeviceDto dto) { ... }
 *
 *   &#64;AuditTarget(entityType = "Device", action = "update")
 *   public DeviceDto updateDevice(Long id, UpdateDeviceDto dto) { ... }
 * </pre>
 *
 * @see AuditAspect
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditTarget {

  /**
   * Az entity típusa (pl. "Device", "User", "Location").
   *
   * @return az entity osztály egyszerűsített neve
   */
  String entityType();

  /**
   * A művelet (pl. "create", "update", "delete", "assign").
   *
   * @return a művelet neve
   */
  String action();
}
