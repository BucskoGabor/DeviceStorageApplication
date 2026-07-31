package hu.tanszek.device.user.dto;

import java.time.Instant;

import hu.tanszek.device.auth.entity.Role;
import hu.tanszek.device.crypto.CryptoService;
import hu.tanszek.device.user.entity.AppUser;

/**
 * UserResponseDto — a /api/users/{id} és /api/users (lista) response formátuma.
 *
 * <p>Az {@code emailEncrypted} mezőt SOHA nem adjuk vissza plaintext-ben — a DTO csak az {@code
 * emailMasked} (pl. {@code a***@tanszek.local}) formátumot küldi, amit a CryptoService.decrypt +
 * maszkolás generál.
 *
 * <p>A plain email NEM kerül a wire-ba — defense-in-depth elv: a DB-ben AES-GCM titkosítva van, és
 * a kliens csak maszkolt formátumban látja.
 *
 * <p>Az {@code officeLocationSummary} egy egyszerűsített nested DTO (id + name + type), nem a
 * teljes {@code Location} entitás — így a Hibernate LAZY proxy nem kerül szerializálásra (amit a
 * Jackson nem tud kezelni ByteBuddyInterceptor exception-nel).
 */
public record UserResponseDto(
    Long id,
    String emailMasked,
    String emailHash,
    boolean active,
    boolean mustChangePassword,
    Role role,
    OfficeLocationSummary officeLocationSummary,
    int failedLoginCount,
    Instant lockedUntil,
    Instant createdAt,
    Instant updatedAt) {
  public record OfficeLocationSummary(Long id, String name, String type) {}

  public static UserResponseDto fromEntity(AppUser user, CryptoService cryptoService) {
    String emailMasked = null;
    if (user.getEmailEncrypted() != null) {
      try {
        String decrypted = cryptoService.decrypt(user.getEmailEncrypted());
        int at = decrypted.indexOf('@');
        if (at > 1) {
          emailMasked = decrypted.charAt(0) + "***" + decrypted.substring(at);
        } else {
          emailMasked = "***";
        }
      } catch (Exception e) {
        emailMasked = "***";
      }
    }

    // Office location: LAZY proxy, ezért csak akkor olvassuk, ha a user
    // ténylegesen kéri (Transactional(readOnly = true) kell hozzá, amit a
    // controller biztosít). Ha nincs office location, null marad.
    OfficeLocationSummary officeSummary = null;
    if (user.getOfficeLocation() != null) {
      try {
        officeSummary =
            new OfficeLocationSummary(
                user.getOfficeLocation().getId(),
                user.getOfficeLocation().getName(),
                user.getOfficeLocation().getType().name());
      } catch (Exception e) {
        // LAZY fetch fail — null marad
      }
    }

    return new UserResponseDto(
        user.getId(),
        emailMasked,
        user.getEmailHash(),
        user.isActive(),
        user.isMustChangePassword(),
        user.getRole(),
        officeSummary,
        user.getFailedLoginCount(),
        user.getLockedUntil(),
        user.getCreatedAt(),
        user.getUpdatedAt());
  }
}
