package hu.tanszek.device.location.entity;

/**
 * Location típusok.
 *
 * <p>Üzleti szabály: a {@link #GROUP} típusú location-ra NEM lehet eszközt assignolni (forrás ÉS
 * cél is tilos) — lásd {@code LocationService.assign()}.
 *
 * @see hu.tanszek.device.location.service.LocationService
 */
public enum LocationType {
  /** Tanterem (pl. Tanterem 101) */
  CLASSROOM,

  /** Iroda (pl. Tanszéki Iroda) */
  OFFICE,

  /** Raktár (eszköz tárolás) */
  STORAGE,

  /** Csoport (hallgatói csoport, NEM lehet eszközt assignolni) */
  GROUP
}
