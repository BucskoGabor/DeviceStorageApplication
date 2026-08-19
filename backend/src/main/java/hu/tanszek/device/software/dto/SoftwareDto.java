package hu.tanszek.device.software.dto;

import java.util.List;

import hu.tanszek.device.software.entity.Software;

/**
 * SoftwareDto — szoftver API válasz DTO.
 *
 * <p>A {@code licenseKey} csak akkor tartalmazza a visszafejtett értéket, ha a hívó user
 * rendelkezik {@code SOFTWARE_LICENSE_VIEW} permissionnel. Egyébként a {@code licenseKeyMasked}
 * mezőben kap maszkolt formátumot ({@code ****-****-****-<utolsó 4 karakter>}). A két mező közül
 * pontosan az egyik kitöltött — soha egyszerre mindkettő nem.
 *
 * <p>A licence key soha nem kerül a wire-ra titkosítatlan formában, ha a user nem jogosult — ez
 * defense-in-depth a DTO szintjén.
 */
public record SoftwareDto(
    Long id,
    String name,
    String licenseKey,
    String licenseKeyMasked,
    int installedDeviceCount,
    List<String> deviceInventoryNumbers) {
  /**
   * Maszkolt formátum előállítása a titkosított blob alapján. Az encrypted string utolsó 4
   * karakterét használja (base64 padding figyelmen kívül hagyva).
   */
  public static String maskFromEncrypted(String licenseKeyEncrypted) {
    if (licenseKeyEncrypted == null || licenseKeyEncrypted.length() < 4) {
      return "****-****-****-";
    }
    String tail = licenseKeyEncrypted.substring(licenseKeyEncrypted.length() - 4);
    return "****-****-****-" + tail;
  }

  public static SoftwareDto fromEntity(
      Software software,
      boolean canViewKey,
      String decryptedKey,
      int installedDeviceCount,
      List<String> deviceInventoryNumbers) {
    if (canViewKey) {
      return new SoftwareDto(
          software.getId(),
          software.getName(),
          decryptedKey,
          null,
          installedDeviceCount,
          deviceInventoryNumbers != null ? deviceInventoryNumbers : List.of());
    }
    return new SoftwareDto(
        software.getId(),
        software.getName(),
        null,
        maskFromEncrypted(software.getLicenseKeyEncrypted()),
        installedDeviceCount,
        deviceInventoryNumbers != null ? deviceInventoryNumbers : List.of());
  }

  public static SoftwareDto fromEntity(Software software, boolean canViewKey, String decryptedKey) {
    return fromEntity(software, canViewKey, decryptedKey, 0, List.of());
  }
}
