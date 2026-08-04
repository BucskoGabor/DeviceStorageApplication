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
    String email,
    String emailMasked,
    String emailHash,
    boolean active,
    boolean mustChangePassword,
    Role role,
    java.util.Set<PermissionSummary> directPermissions,
    java.util.Set<String> effectivePermissions,
    OfficeLocationSummary officeLocationSummary,
    int failedLoginCount,
    Instant lockedUntil,
    Instant createdAt,
    Instant updatedAt) {
  public record OfficeLocationSummary(Long id, String name, String type) {}
  public record PermissionSummary(Long id, String name) {}

  public static UserResponseDto fromEntity(AppUser user, CryptoService cryptoService) {
    String email = null;
    String emailMasked = null;
    if (user.getEmailEncrypted() != null) {
      try {
        email = cryptoService.decrypt(user.getEmailEncrypted());
        int at = email.indexOf('@');
        if (at > 1) {
          emailMasked = email.charAt(0) + "***" + email.substring(at);
        } else {
          emailMasked = "***";
        }
      } catch (Exception e) {
        email = user.getEmailHash();
        emailMasked = "***";
      }
    }

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

    java.util.Set<PermissionSummary> directPerms = new java.util.HashSet<>();
    if (user.getPermissions() != null) {
      try {
        user.getPermissions().forEach(p -> directPerms.add(new PermissionSummary(p.getId(), p.getName())));
      } catch (Exception e) {
        // LAZY fetch fail
      }
    }

    java.util.Set<String> effectivePerms = new java.util.HashSet<>();
    if (user.getRole() != null && user.getRole().getPermissions() != null) {
      try {
        user.getRole().getPermissions().forEach(p -> effectivePerms.add(p.getName()));
      } catch (Exception e) {
        // LAZY fetch fail
      }
    }
    directPerms.forEach(p -> effectivePerms.add(p.name()));

    return new UserResponseDto(
        user.getId(),
        email,
        emailMasked,
        user.getEmailHash(),
        user.isActive(),
        user.isMustChangePassword(),
        user.getRole(),
        directPerms,
        effectivePerms,
        officeSummary,
        user.getFailedLoginCount(),
        user.getLockedUntil(),
        user.getCreatedAt(),
        user.getUpdatedAt());
  }
}
