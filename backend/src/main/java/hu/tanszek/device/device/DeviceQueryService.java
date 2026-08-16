package hu.tanszek.device.device;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hu.tanszek.device.device.entity.Device;
import hu.tanszek.device.device.repository.DeviceRepository;
import hu.tanszek.device.user.entity.AppUser;

import lombok.RequiredArgsConstructor;

/**
 * DeviceQueryService — Device entity olvasási műveletek JpaSpecificationExecutor-ral.
 *
 * <p>A row-level filter a bejelentkezett user role-ja alapján szűr:
 *
 * <ul>
 *   <li>ADMIN (null user): minden device
 *   <li>TEACHER: saját device-ok (active assignment) + irodai device-ok
 *   <li>STUDENT: csak saját device-ok (active assignment to currentUser)
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DeviceQueryService {

  private final DeviceRepository deviceRepository;

  /**
   * Device lista lekérdezése row-level filterrel.
   *
   * @param currentUser a bejelentkezett user (null = ADMIN, minden device)
   * @return a row-level filter által engedélyezett device-ok listája
   */
  @Transactional(readOnly = true)
  public List<Device> findAllForCurrentUser(AppUser currentUser) {
    Specification<Device> spec = buildSpecForUser(currentUser);
    return deviceRepository.findAll(spec);
  }

  /**
   * Device lista lekérdezése row-level filterrel, lapozással.
   *
   * @param currentUser a bejelentkezett user (null = ADMIN)
   * @param pageable lapozási paraméterek
   * @return lapozott device lista
   */
  /**
   * Device lista lekérdezése row-level filterrel, egyedi szűréssel és lapozással.
   *
   * @param currentUser a bejelentkezett user (null = ADMIN / full access)
   * @param additionalSpec kiegészítő specifikáció (pl. státusz, típus, leltári szám)
   * @param pageable lapozási paraméterek
   * @return lapozott device lista
   */
  @Transactional(readOnly = true)
  public Page<Device> findAllForCurrentUser(
      AppUser currentUser, Specification<Device> additionalSpec, Pageable pageable) {
    Specification<Device> spec = buildSpecForUser(currentUser);
    if (additionalSpec != null) {
      spec = spec.and(additionalSpec);
    }
    return deviceRepository.findAll(spec, pageable);
  }

  /** Jogosultság-alapú (Permission-Driven) Specification összeállítása. */
  public Specification<Device> buildSpecForUser(AppUser currentUser) {
    if (currentUser == null) {
      return DeviceSpecifications.hasAccess(null); // No user context → minden eszköz
    }

    // Vezetői / eszközmenedzsment jogosultságokkal rendelkező felhasználók: teljes tanszéki hozzáférés
    boolean hasGlobalDeviceAccess =
        currentUser.hasPermission("DEVICE_CREATE")
            || currentUser.hasPermission("DEVICE_DELETE")
            || currentUser.hasPermission("USER_MANAGE");

    if (hasGlobalDeviceAccess) {
      return DeviceSpecifications.hasAccess(null);
    }

    // Hozzárendelési joggal és/vagy irodai beosztással rendelkező felhasználók: saját és irodai eszközök
    boolean canAssignOrHasOffice =
        currentUser.hasPermission("DEVICE_ASSIGN")
            || currentUser.hasPermission("DEVICE_UNASSIGN")
            || currentUser.getOfficeLocation() != null;

    if (canAssignOrHasOffice) {
      return DeviceSpecifications.teacherAccess(currentUser.getId(), currentUser);
    }

    // Csak olvasási (hallgatói) joggal rendelkező felhasználók: kizárólag a számukra aktívan kiadott eszközök
    return DeviceSpecifications.hasAccess(currentUser.getId());
  }
}
