package hu.tanszek.device.audit;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

import hu.tanszek.device.assignment.entity.DeviceAssignment;
import hu.tanszek.device.assignment.repository.DeviceAssignmentRepository;
import hu.tanszek.device.attachment.entity.DeviceAttachment;
import hu.tanszek.device.attachment.repository.DeviceAttachmentRepository;
import hu.tanszek.device.auth.entity.Permission;
import hu.tanszek.device.auth.entity.Role;
import hu.tanszek.device.auth.repository.PermissionRepository;
import hu.tanszek.device.auth.repository.RoleRepository;
import hu.tanszek.device.crypto.CryptoService;
import hu.tanszek.device.device.entity.Device;
import hu.tanszek.device.device.repository.DeviceRepository;
import hu.tanszek.device.location.entity.Location;
import hu.tanszek.device.location.repository.LocationRepository;
import hu.tanszek.device.software.entity.Software;
import hu.tanszek.device.software.repository.SoftwareRepository;
import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;

import lombok.RequiredArgsConstructor;

/**
 * EntityTypeRegistry — entity_type string → repository lookup helper.
 *
 * <p>A {@code audit_logs.entity_type} mező String ("Device", "User", stb.), és a rollback-hez tudni
 * kell, melyik repository-t kell használni az adott típus betöltéséhez. Ez az osztály centralizálja
 * a típus→repository mappinget.
 */
@Component
@RequiredArgsConstructor
public class EntityTypeRegistry {

  private final DeviceRepository deviceRepository;
  private final AppUserRepository userRepository;
  private final LocationRepository locationRepository;
  private final DeviceAssignmentRepository assignmentRepository;
  private final SoftwareRepository softwareRepository;
  private final DeviceAttachmentRepository attachmentRepository;
  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;
  private final CryptoService cryptoService;
  private final Argon2PasswordEncoder passwordEncoder;

  /**
   * Entity lookup az entity_type string alapján.
   *
   * @param entityType az entity típusa ("Device", "User", stb.)
   * @param entityId az entity ID-ja
   * @return az entity ha megtalálható, különben null
   */
  @SuppressWarnings("unchecked")
  public Object findById(String entityType, Long entityId) {
    Function<Long, java.util.Optional<?>> finder =
        (Function<Long, java.util.Optional<?>>) finders.get(entityType);
    if (finder == null) {
      throw new IllegalArgumentException("Unknown entity type: " + entityType);
    }
    return finder.apply(entityId).orElse(null);
  }

  /** Entity törlése az entity_type string alapján (CREATE rollback-hez). */
  public void deleteById(String entityType, Long entityId) {
    switch (entityType) {
      case "Device" -> deviceRepository.deleteById(entityId);
      case "User" -> userRepository.deleteById(entityId);
      case "Location" -> locationRepository.deleteById(entityId);
      case "Assignment" -> assignmentRepository.deleteById(entityId);
      case "Software" -> softwareRepository.deleteById(entityId);
      case "Attachment" -> attachmentRepository.deleteById(entityId);
      case "Role" -> roleRepository.deleteById(entityId);
      default ->
          throw new IllegalArgumentException("Cannot delete unknown entity type: " + entityType);
    }
  }

  /** Managed entity mentése (UPDATE rollback-hez, ha nem JPA dirty checking-gel megy). */
  public void saveEntity(Object entity) {
    if (entity instanceof Device d) {
      deviceRepository.save(d);
    } else if (entity instanceof AppUser u) {
      userRepository.save(u);
    } else if (entity instanceof Location l) {
      locationRepository.save(l);
    } else if (entity instanceof DeviceAssignment a) {
      assignmentRepository.save(a);
    } else if (entity instanceof Software s) {
      softwareRepository.save(s);
    } else if (entity instanceof DeviceAttachment att) {
      attachmentRepository.save(att);
    } else if (entity instanceof Role r) {
      roleRepository.save(r);
    }
  }

  /** Az aktuális entity state JSON-ba konvertálása (rollback összehasonlításhoz). */
  public Map<String, Object> toJsonMap(Object entity) {
    if (entity == null) {
      return null;
    }

    Map<String, Object> map = new HashMap<>();

    if (entity instanceof Device device) {
      map.put("id", device.getId());
      map.put("type", device.getType());
      map.put("inventoryNumber", device.getInventoryNumber());
      map.put("status", device.getStatus());
      map.put(
          "currentLocationId",
          device.getCurrentLocation() != null ? device.getCurrentLocation().getId() : null);
    } else if (entity instanceof AppUser user) {
      map.put("id", user.getId());
      map.put("email", user.getEmail());
      map.put("active", user.isActive());
      map.put("mustChangePassword", user.isMustChangePassword());
      map.put("emailHash", user.getEmailHash());
      map.put("roleId", user.getRole() != null ? user.getRole().getId() : null);
      map.put(
          "officeLocationId",
          user.getOfficeLocation() != null ? user.getOfficeLocation().getId() : null);
      map.put(
          "permissions",
          user.getPermissions() != null
              ? user.getPermissions().stream().map(Permission::getName).sorted().toList()
              : List.of());
    } else if (entity instanceof Location location) {
      map.put("id", location.getId());
      map.put("name", location.getName());
      map.put("parentId", location.getParent() != null ? location.getParent().getId() : null);
      map.put("type", location.getType());
    } else if (entity instanceof DeviceAssignment assignment) {
      map.put("id", assignment.getId());
      map.put("deviceId", assignment.getDevice() != null ? assignment.getDevice().getId() : null);
      map.put(
          "fromLocationId",
          assignment.getFromLocation() != null ? assignment.getFromLocation().getId() : null);
      map.put(
          "toLocationId",
          assignment.getToLocation() != null ? assignment.getToLocation().getId() : null);
      map.put(
          "fromUserId", assignment.getFromUser() != null ? assignment.getFromUser().getId() : null);
      map.put("toUserId", assignment.getToUser() != null ? assignment.getToUser().getId() : null);
      map.put("status", assignment.getStatus());
    } else if (entity instanceof Software software) {
      map.put("id", software.getId());
      map.put("name", software.getName());
    } else if (entity instanceof DeviceAttachment attachment) {
      map.put("id", attachment.getId());
      map.put("deviceId", attachment.getDevice() != null ? attachment.getDevice().getId() : null);
      map.put("fileName", attachment.getFileName());
      map.put("mimeType", attachment.getMimeType());
      map.put("sizeBytes", attachment.getSizeBytes());
    } else if (entity instanceof Role role) {
      map.put("id", role.getId());
      map.put("name", role.getName());
      map.put(
          "permissions",
          role.getPermissions() != null
              ? role.getPermissions().stream().map(Permission::getName).sorted().toList()
              : List.of());
    }

    return map;
  }

  /**
   * Entity JSON map-ből entitás apply-olása (rollback-hez).
   *
   * <p>A Role permission listáját is visszaállítja — korábban ez kimaradt, így a role-ból
   * eltávolított permission a rollback során NEM került vissza.
   */
  public void applyJsonMap(Object entity, Map<String, Object> fields) {
    if (entity == null || fields == null) {
      return;
    }

    if (entity instanceof Device device) {
      if (fields.get("type") instanceof String s && !"***".equals(s)) {
        device.setType(s);
      }
      if (fields.get("inventoryNumber") instanceof String s && !"***".equals(s)) {
        device.setInventoryNumber(s);
      }
      if (fields.get("status") != null && !"***".equals(fields.get("status"))) {
        device.setStatus(
            hu.tanszek.device.device.entity.DeviceStatus.valueOf(fields.get("status").toString()));
      }
      if (fields.containsKey("currentLocationId")) {
        Object locIdRaw = fields.get("currentLocationId");
        if (locIdRaw instanceof Number locIdNum) {
          locationRepository.findById(locIdNum.longValue()).ifPresent(device::setCurrentLocation);
        } else if (locIdRaw == null) {
          device.setCurrentLocation(null);
        }
      }
    } else if (entity instanceof AppUser user) {
      if (fields.containsKey("active") && fields.get("active") != null) {
        Object val = fields.get("active");
        if (val instanceof Boolean b) {
          user.setActive(b);
        } else if (val instanceof String s && !"***".equals(s)) {
          user.setActive(Boolean.parseBoolean(s));
        }
      }
      if (fields.containsKey("mustChangePassword") && fields.get("mustChangePassword") != null) {
        Object val = fields.get("mustChangePassword");
        if (val instanceof Boolean b) {
          user.setMustChangePassword(b);
        } else if (val instanceof String s && !"***".equals(s)) {
          user.setMustChangePassword(Boolean.parseBoolean(s));
        }
      }
      // A user role ID-ját is vissza kell állítani — korábban ez kimaradt, így a
      // user role-váltása (pl. tanár → diák) a rollback során NEM állt vissza.
      // A toJsonMap serializálja a roleId-t; itt a roleId alapján betöltjük a
      // Role entitást, és beállítjuk a user-re. Explicit null = role eltávolítása.
      if (fields.containsKey("roleId")) {
        Object roleIdRaw = fields.get("roleId");
        if (roleIdRaw instanceof Number roleIdNum) {
          roleRepository.findById(roleIdNum.longValue()).ifPresent(user::setRole);
        } else if (roleIdRaw == null) {
          user.setRole(null);
        }
      }
      // A user officeLocation ID-ját is vissza kell állítani
      if (fields.containsKey("officeLocationId")) {
        Object locIdRaw = fields.get("officeLocationId");
        if (locIdRaw instanceof Number locIdNum) {
          locationRepository.findById(locIdNum.longValue()).ifPresent(user::setOfficeLocation);
        } else if (locIdRaw == null) {
          user.setOfficeLocation(null);
        }
      }
      // A user direct permission-jeit is vissza kell állítani
      if (fields.containsKey("permissions")) {
        Object permsRaw = fields.get("permissions");
        if (permsRaw instanceof List<?> permsList) {
          Set<Permission> perms = new HashSet<>();
          for (Object p : permsList) {
            if (p instanceof String name && !name.isBlank()) {
              permissionRepository.findByName(name).ifPresent(perms::add);
            }
          }
          if (user.getPermissions() == null) {
            user.setPermissions(new HashSet<>());
          }
          user.getPermissions().clear();
          user.getPermissions().addAll(perms);
        } else if (permsRaw == null) {
          if (user.getPermissions() != null) {
            user.getPermissions().clear();
          }
        }
      }
    } else if (entity instanceof Location location) {
      if (fields.get("name") instanceof String s && !"***".equals(s)) {
        location.setName(s);
      }
      if (fields.get("type") != null && !"***".equals(fields.get("type"))) {
        location.setType(
            hu.tanszek.device.location.entity.LocationType.valueOf(fields.get("type").toString()));
      }
      if (fields.containsKey("parentId")
          && fields.get("parentId") != null
          && !"***".equals(fields.get("parentId"))) {
        Long parentId = ((Number) fields.get("parentId")).longValue();
        locationRepository.findById(parentId).ifPresent(location::setParent);
      } else if (fields.containsKey("parentId") && fields.get("parentId") == null) {
        location.setParent(null);
      }
    } else if (entity instanceof DeviceAssignment assignment) {
      if (fields.get("status") != null && !"***".equals(fields.get("status"))) {
        assignment.setStatus(
            hu.tanszek.device.assignment.entity.AssignmentStatus.valueOf(
                fields.get("status").toString()));
      }
      applyAssignmentReference(
          fields, "deviceId", deviceRepository::findById, assignment::setDevice);
      applyAssignmentReference(
          fields, "fromLocationId", locationRepository::findById, assignment::setFromLocation);
      applyAssignmentReference(
          fields, "toLocationId", locationRepository::findById, assignment::setToLocation);
      applyAssignmentReference(
          fields, "fromUserId", userRepository::findById, assignment::setFromUser);
      applyAssignmentReference(fields, "toUserId", userRepository::findById, assignment::setToUser);
    } else if (entity instanceof Software software) {
      if (fields.get("name") instanceof String s && !"***".equals(s)) {
        software.setName(s);
      }
    } else if (entity instanceof DeviceAttachment attachment) {
      if (fields.get("fileName") instanceof String s && !"***".equals(s)) {
        attachment.setFileName(s);
      }
      if (fields.get("mimeType") instanceof String s && !"***".equals(s)) {
        attachment.setMimeType(s);
      }
      if (fields.get("sizeBytes") instanceof Number num) {
        attachment.setSizeBytes(num.longValue());
      }
    } else if (entity instanceof Role role) {
      if (fields.get("name") instanceof String s && !"***".equals(s)) {
        role.setName(s);
      }
      Object permsRaw = fields.get("permissions");
      if (permsRaw instanceof List<?> permsList) {
        Set<Permission> perms = new HashSet<>();
        for (Object p : permsList) {
          if (p instanceof String name && !name.isBlank()) {
            permissionRepository.findByName(name).ifPresent(perms::add);
          }
        }
        role.getPermissions().clear();
        role.getPermissions().addAll(perms);
      } else if (permsRaw == null && fields.containsKey("permissions")) {
        role.getPermissions().clear();
      }
    }
  }

  /**
   * Generikus helper a DeviceAssignment referencia mezőinek (deviceId, fromLocationId,
   * toLocationId, fromUserId, toUserId) visszaállításához a beforeState alapján.
   *
   * <p>Ha a fields tartalmazza a megadott kulcsot, megpróbálja betölteni az entitást a megadott
   * repository-n keresztül, és beállítani a setter-en. Ha a kulcs értéke explicit null, a setter
   * null-t állít be.
   */
  private <T> void applyAssignmentReference(
      Map<String, Object> fields,
      String key,
      java.util.function.Function<Long, java.util.Optional<T>> finder,
      java.util.function.Consumer<T> setter) {
    if (!fields.containsKey(key)) {
      return;
    }
    Object raw = fields.get(key);
    if (raw instanceof Number num) {
      finder.apply(num.longValue()).ifPresent(setter);
    } else if (raw == null) {
      setter.accept(null);
    }
  }

  /**
   * Törölt entitás újralétrehozása a beforeState alapján (DELETE rollback).
   *
   * @param entityType az entity típusa
   * @param entityId az eredeti entity ID-ja
   * @param fields a beforeState mezői
   * @return az újonnan létrehozott és perzisztált entitás
   */
  public Object recreateEntity(String entityType, Long entityId, Map<String, Object> fields) {
    if (fields == null) {
      throw new IllegalArgumentException("Cannot recreate entity with null fields");
    }

    switch (entityType) {
      case "Device" -> {
        String invNumber = (String) fields.get("inventoryNumber");
        if (invNumber != null && deviceRepository.findByInventoryNumber(invNumber).isPresent()) {
          throw new hu.tanszek.device.common.BusinessValidationException(
              "duplicateInventoryNumber",
              "Ezzel a leltári számmal már létezik eszköz: " + invNumber);
        }
        String type = (String) fields.get("type");
        if (type == null || "***".equals(type)) {
          type = "Unknown";
        }
        hu.tanszek.device.device.entity.DeviceStatus status =
            hu.tanszek.device.device.entity.DeviceStatus.IN_STORAGE;
        if (fields.get("status") != null && !"***".equals(fields.get("status"))) {
          try {
            status =
                hu.tanszek.device.device.entity.DeviceStatus.valueOf(
                    fields.get("status").toString());
          } catch (Exception ignored) {
          }
        }
        Device device =
            Device.builder()
                .type(type)
                .inventoryNumber(
                    invNumber != null && !"***".equals(invNumber)
                        ? invNumber
                        : "INV-RESTORED-" + System.currentTimeMillis())
                .status(status)
                .softwares(new HashSet<>())
                .build();

        if (fields.containsKey("currentLocationId")
            && fields.get("currentLocationId") instanceof Number locId) {
          locationRepository.findById(locId.longValue()).ifPresent(device::setCurrentLocation);
        } else if (fields.containsKey("locationId")
            && fields.get("locationId") instanceof Number locId) {
          locationRepository.findById(locId.longValue()).ifPresent(device::setCurrentLocation);
        }
        return deviceRepository.save(device);
      }
      case "Location" -> {
        String name = (String) fields.get("name");
        hu.tanszek.device.location.entity.LocationType type =
            hu.tanszek.device.location.entity.LocationType.CLASSROOM;
        if (fields.get("type") != null && !"***".equals(fields.get("type"))) {
          try {
            type =
                hu.tanszek.device.location.entity.LocationType.valueOf(
                    fields.get("type").toString());
          } catch (Exception ignored) {
          }
        }
        Location location =
            Location.builder()
                .name(name != null && !"***".equals(name) ? name : "Restored Location")
                .type(type)
                .build();
        if (fields.containsKey("parentId") && fields.get("parentId") instanceof Number parentId) {
          locationRepository.findById(parentId.longValue()).ifPresent(location::setParent);
        }
        return locationRepository.save(location);
      }
      case "Software" -> {
        String name = (String) fields.get("name");
        String rawKey = null;
        if (fields.get("licenseKey") != null) {
          rawKey = fields.get("licenseKey").toString();
        } else if (fields.get("licenseKeyEncrypted") != null) {
          rawKey = fields.get("licenseKeyEncrypted").toString();
        }
        if (rawKey == null || rawKey.isBlank() || rawKey.contains("***")) {
          rawKey = "RESTORED-KEY-" + (entityId != null ? entityId : System.currentTimeMillis());
        }
        String encryptedKey = cryptoService != null ? cryptoService.encrypt(rawKey) : rawKey;
        Software software =
            Software.builder()
                .name(name != null && !"***".equals(name) ? name : "Restored Software")
                .licenseKeyEncrypted(encryptedKey)
                .build();
        return softwareRepository.save(software);
      }
      case "Role" -> {
        String name = (String) fields.get("name");
        Role role =
            Role.builder()
                .name(name != null && !"***".equals(name) ? name : "ROLE_RESTORED")
                .permissions(new HashSet<>())
                .build();
        // Törölt role visszaállításakor is alkalmazzuk a permission listát.
        Object permsRaw = fields.get("permissions");
        if (permsRaw instanceof List<?> permsList) {
          Set<Permission> perms = new HashSet<>();
          for (Object p : permsList) {
            if (p instanceof String pname && !pname.isBlank()) {
              permissionRepository.findByName(pname).ifPresent(perms::add);
            }
          }
          role.getPermissions().addAll(perms);
        }
        return roleRepository.save(role);
      }
      case "User", "AppUser" -> {
        String emailHash = (String) fields.get("emailHash");
        if (emailHash != null && userRepository.findByEmailHash(emailHash).isPresent()) {
          throw new hu.tanszek.device.common.BusinessValidationException(
              "duplicateEmail", "Ezzel az email címmel már létezik felhasználó.");
        }
        String rawEmail =
            fields.get("email") != null
                ? fields.get("email").toString()
                : (fields.get("emailEncrypted") != null
                    ? fields.get("emailEncrypted").toString()
                    : "restored"
                        + (entityId != null ? entityId : System.currentTimeMillis())
                        + "@tanszek.local");
        String emailEncrypted = cryptoService != null ? cryptoService.encrypt(rawEmail) : rawEmail;
        String emailHashVal =
            emailHash != null
                ? emailHash
                : (cryptoService != null
                    ? cryptoService.sha256(rawEmail)
                    : "restored_" + System.currentTimeMillis());
        String randomPassword = UUID.randomUUID().toString();
        String passwordHash =
            passwordEncoder != null ? passwordEncoder.encode(randomPassword) : "$argon2id$restored";

        AppUser user =
            AppUser.builder()
                .emailHash(emailHashVal)
                .emailEncrypted(emailEncrypted)
                .passwordHash(passwordHash)
                .passwordChangedAt(Instant.now())
                .active(true)
                .mustChangePassword(true)
                .failedLoginCount(0)
                .permissions(new HashSet<>())
                .build();
        // Törölt user visszaállításakor a roleId alapján visszaállítjuk a role-t.
        // Korábban a role null maradt, ami azt eredményezte, hogy a rekreált
        // user nem kapta vissza a jogosultságait.
        Object roleIdRaw = fields.get("roleId");
        if (roleIdRaw instanceof Number roleIdNum) {
          roleRepository.findById(roleIdNum.longValue()).ifPresent(user::setRole);
        }
        if (fields.containsKey("officeLocationId")
            && fields.get("officeLocationId") instanceof Number locId) {
          locationRepository.findById(locId.longValue()).ifPresent(user::setOfficeLocation);
        }
        Object permsRaw = fields.get("permissions");
        if (permsRaw instanceof List<?> permsList) {
          for (Object p : permsList) {
            if (p instanceof String pname && !pname.isBlank()) {
              permissionRepository.findByName(pname).ifPresent(user.getPermissions()::add);
            }
          }
        }
        return userRepository.save(user);
      }
      default ->
          throw new IllegalArgumentException("Cannot recreate entity of type: " + entityType);
    }
  }

  /** Entity lookup finder-ek map-je: entity_type string → repository.findById(). */
  private final Map<String, Function<Long, ?>> finders = new HashMap<>();

  /** Init metódus — a finders map feltöltése a támogatott típusokkal. */
  @jakarta.annotation.PostConstruct
  @SuppressWarnings("unchecked")
  public void initFinders() {
    finders.put("Device", id -> deviceRepository.findById(id));
    finders.put("User", id -> userRepository.findById(id));
    finders.put("AppUser", id -> userRepository.findById(id));
    finders.put("Location", id -> locationRepository.findById(id));
    finders.put("Assignment", id -> assignmentRepository.findById(id));
    finders.put("DeviceAssignment", id -> assignmentRepository.findById(id));
    finders.put("Software", id -> softwareRepository.findById(id));
    finders.put("Attachment", id -> attachmentRepository.findById(id));
    finders.put("Role", id -> roleRepository.findByIdWithPermissions(id));
  }
}
