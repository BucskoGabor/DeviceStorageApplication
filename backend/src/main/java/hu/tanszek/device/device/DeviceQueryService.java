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
  @Transactional(readOnly = true)
  public Page<Device> findAllForCurrentUser(AppUser currentUser, Pageable pageable) {
    Specification<Device> spec = buildSpecForUser(currentUser);
    return deviceRepository.findAll(spec, pageable);
  }

  /** Role-alapú Specification összeállítása. */
  private Specification<Device> buildSpecForUser(AppUser currentUser) {
    if (currentUser == null) {
      return DeviceSpecifications.hasAccess(null); // ADMIN → minden
    }

    String role = currentUser.getRole().getName();
    if ("ROLE_ADMIN".equals(role)) {
      return DeviceSpecifications.hasAccess(null);
    } else if ("ROLE_TEACHER".equals(role)) {
      return DeviceSpecifications.teacherAccess(currentUser.getId(), currentUser);
    } else {
      return DeviceSpecifications.hasAccess(currentUser.getId());
    }
  }
}
