package hu.tanszek.device.device.entity;

/**
 * Eszköz státuszok.
 *
 * <p>A {@code LocationService.assign()} és {@code DeviceService.delete()} metódusok ellenőrzik a
 * státuszt — a {@link #MAINTENANCE} és {@link #DISPOSED} állapotú eszközökre NEM lehet assignolni
 * vagy törölni.
 *
 * @see hu.tanszek.device.device.service.DeviceService
 */
public enum DeviceStatus {
  /** Új eszköz, még nincs hozzárendelve vagy raktározva */
  PENDING,

  /** Aktívan hozzá van rendelve egy user-hez/location-höz */
  ASSIGNED,

  /** Raktárban van, nincs hozzárendelve */
  IN_STORAGE,

  /** Karbantartás alatt (nem lehet assignolni/törölni) */
  MAINTENANCE,

  /** Selejtezve (nem lehet assignolni/törölni) */
  DISPOSED
}
