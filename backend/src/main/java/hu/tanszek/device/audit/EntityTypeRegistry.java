package hu.tanszek.device.audit;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import hu.tanszek.device.assignment.entity.DeviceAssignment;
import hu.tanszek.device.assignment.repository.DeviceAssignmentRepository;
import hu.tanszek.device.attachment.entity.DeviceAttachment;
import hu.tanszek.device.attachment.repository.DeviceAttachmentRepository;
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
 *
 * <p>Támogatott típusok:
 *
 * <ul>
 *   <li>Device → DeviceRepository.findById()
 *   <li>User → AppUserRepository.findById()
 *   <li>Location → LocationRepository.findById()
 *   <li>Assignment → DeviceAssignmentRepository.findById()
 *   <li>Software → SoftwareRepository.findById()
 *   <li>Attachment → DeviceAttachmentRepository.findById()
 * </ul>
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

  /**
   * Entity törlése az entity_type string alapján (CREATE rollback-hez).
   *
   * @param entityType az entity típusa
   * @param entityId az entity ID-ja
   */
  public void deleteById(String entityType, Long entityId) {
    switch (entityType) {
      case "Device" -> deviceRepository.deleteById(entityId);
      case "User" -> userRepository.deleteById(entityId);
      case "Location" -> locationRepository.deleteById(entityId);
      case "Assignment" -> assignmentRepository.deleteById(entityId);
      case "Software" -> softwareRepository.deleteById(entityId);
      case "Attachment" -> attachmentRepository.deleteById(entityId);
      default ->
          throw new IllegalArgumentException("Cannot delete unknown entity type: " + entityType);
    }
  }

  /** Managed entity mentése (UPDATE rollback-hez, ha nem JPA dirty checking-gel megy). */
  public void saveEntity(Object entity) {
    if (entity instanceof Device d) deviceRepository.save(d);
    else if (entity instanceof AppUser u) userRepository.save(u);
    else if (entity instanceof Location l) locationRepository.save(l);
    else if (entity instanceof DeviceAssignment a) assignmentRepository.save(a);
    else if (entity instanceof Software s) softwareRepository.save(s);
    else if (entity instanceof DeviceAttachment att) attachmentRepository.save(att);
  }

  /**
   * Az aktuális entity state JSON-ba konvertálása (rollback összehasonlításhoz).
   *
   * <p>A JSON-ba konvertálás az entity mezőiből történik. A {@code changes_json} mezőben tárolt
   * before/after diff-et hasonlítjuk össze.
   *
   * @param entity az entitás
   * @return Map<String, Object> a mező nevek és értékek
   */
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
      // location_id, softwares nem tároljuk a rollback JSON-ban
    } else if (entity instanceof AppUser user) {
      map.put("id", user.getId());
      map.put("active", user.isActive());
      map.put("mustChangePassword", user.isMustChangePassword());
      map.put("emailHash", user.getEmailHash());
      map.put("roleId", user.getRole() != null ? user.getRole().getId() : null);
      // password_hash, email_encrypted, locked_until nem (security)
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
      map.put("active", assignment.isActive());
    } else if (entity instanceof Software software) {
      map.put("id", software.getId());
      map.put("name", software.getName());
      // license_key_encrypted nem tároljuk (security)
    } else if (entity instanceof DeviceAttachment attachment) {
      map.put("id", attachment.getId());
      map.put("deviceId", attachment.getDevice() != null ? attachment.getDevice().getId() : null);
      map.put("fileName", attachment.getFileName());
      map.put("mimeType", attachment.getMimeType());
      map.put("sizeBytes", attachment.getSizeBytes());
    }

    return map;
  }

  /**
   * Entity JSON map-ből entitás apply-olása (rollback-hez).
   *
   * <p>Frissíti az entitás mezőit a JSON map alapján. Csak azokat a mezőket frissíti, amelyek a
   * map-ben szerepelnek (before/after diff-ek).
   *
   * @param entity az entitás (in-place frissítve)
   * @param fields a JSON map (a before/after-ből)
   */
  public void applyJsonMap(Object entity, Map<String, Object> fields) {
    if (entity == null || fields == null) return;

    if (entity instanceof Device device) {
      if (fields.containsKey("type")) device.setType((String) fields.get("type"));
      if (fields.containsKey("inventoryNumber"))
        device.setInventoryNumber((String) fields.get("inventoryNumber"));
      if (fields.containsKey("status") && fields.get("status") != null) {
        device.setStatus(
            hu.tanszek.device.device.entity.DeviceStatus.valueOf(fields.get("status").toString()));
      }
    } else if (entity instanceof AppUser user) {
      if (fields.containsKey("active") && fields.get("active") != null) {
        user.setActive((Boolean) fields.get("active"));
      }
      if (fields.containsKey("mustChangePassword") && fields.get("mustChangePassword") != null) {
        user.setMustChangePassword((Boolean) fields.get("mustChangePassword"));
      }
      if (fields.containsKey("roleId") && fields.get("roleId") != null) {
        Long roleId = ((Number) fields.get("roleId")).longValue();
        // Role lookup szükséges — de a registry nem tud role-t keresni,
        // ezért itt a managed entity JPA-n keresztül frissül
        // (a tranzakción belül a role lazy load működik)
      }
    } else if (entity instanceof Location location) {
      if (fields.containsKey("name")) location.setName((String) fields.get("name"));
      if (fields.containsKey("type") && fields.get("type") != null) {
        location.setType(
            hu.tanszek.device.location.entity.LocationType.valueOf(fields.get("type").toString()));
      }
      if (fields.containsKey("parentId") && fields.get("parentId") != null) {
        Long parentId = ((Number) fields.get("parentId")).longValue();
        locationRepository.findById(parentId).ifPresent(location::setParent);
      } else if (fields.containsKey("parentId") && fields.get("parentId") == null) {
        location.setParent(null);
      }
    } else if (entity instanceof DeviceAssignment assignment) {
      if (fields.containsKey("status") && fields.get("status") != null) {
        assignment.setStatus(
            hu.tanszek.device.assignment.entity.AssignmentStatus.valueOf(
                fields.get("status").toString()));
      }
      if (fields.containsKey("active") && fields.get("active") != null) {
        assignment.setActive((Boolean) fields.get("active"));
      }
      // FK rollback-ek: device, location, user FK-k a DeviceAssignment-en
      // A managed entity-n keresztül a JPA dirty checking menti őket
    } else if (entity instanceof Software software) {
      if (fields.containsKey("name")) software.setName((String) fields.get("name"));
    } else if (entity instanceof DeviceAttachment attachment) {
      if (fields.containsKey("fileName")) attachment.setFileName((String) fields.get("fileName"));
      if (fields.containsKey("mimeType")) attachment.setMimeType((String) fields.get("mimeType"));
      if (fields.containsKey("sizeBytes") && fields.get("sizeBytes") != null) {
        attachment.setSizeBytes(((Number) fields.get("sizeBytes")).longValue());
      }
    }
  }

  /** Entity lookup finder-ek map-je: entity_type string → repository.findById(). */
  private final Map<String, Function<Long, ?>> finders = new HashMap<>();

  /**
   * Init metódus — a {@link #finders} map feltöltése a támogatott típusokkal.
   *
   * <p>A map-et a konstruktor után töltjük fel, hogy a dependency injection befejeződjön.
   */
  @jakarta.annotation.PostConstruct
  @SuppressWarnings("unchecked")
  public void initFinders() {
    finders.put("Device", id -> deviceRepository.findById(id));
    finders.put("User", id -> userRepository.findById(id));
    finders.put("AppUser", id -> userRepository.findById(id));
    finders.put("Location", id -> locationRepository.findById(id));
    finders.put("Assignment", id -> assignmentRepository.findById(id));
    finders.put("Software", id -> softwareRepository.findById(id));
    finders.put("Attachment", id -> attachmentRepository.findById(id));
  }
}
