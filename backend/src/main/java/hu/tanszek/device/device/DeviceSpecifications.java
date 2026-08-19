package hu.tanszek.device.device;

import org.springframework.data.jpa.domain.Specification;

import hu.tanszek.device.assignment.entity.AssignmentStatus;
import hu.tanszek.device.assignment.entity.DeviceAssignment;
import hu.tanszek.device.device.entity.Device;
import hu.tanszek.device.user.entity.AppUser;

import jakarta.persistence.criteria.Subquery;

/**
 * DeviceSpecifications — Specification builder a Device row-level filterhez.
 *
 * <p>A Specification interface használata (lásd {@link Specification}) lehetővé teszi a dinamikus
 * Predicate összeállítást a JpaSpecificationExecutor-ral.
 *
 * <p>A row-level filter három role-t különböztet meg:
 *
 * <ul>
 *   <li>ADMIN: minden device (nincs szűrés, a spec üres)
 *   <li>TEACHER: saját + irodai device-ok (office_location_id match)
 *   <li>STUDENT: csak saját device-ok (aktív assignment to currentUser)
 * </ul>
 */
public final class DeviceSpecifications {

  private DeviceSpecifications() {}

  /**
   * STUDENT row-level filter: csak saját device-ok (aktív assignment to currentUser).
   *
   * <p>EXISTS subquery a device_assignments táblán: {@code WHERE da.toUser.id = currentUserId AND
   * da.active = true AND da.device.id = device.id}
   *
   * @param currentUserId a bejelentkezett user ID-ja (null = ADMIN, minden device)
   * @return Specification ami szűri a device-okat a currentUser assignment-jei alapján
   */
  public static Specification<Device> hasAccess(Long currentUserId) {
    if (currentUserId == null) {
      return (root, query, cb) -> cb.conjunction();
    }

    return (root, query, cb) -> {
      Subquery<Long> subquery = query.subquery(Long.class);
      var subRoot = subquery.from(DeviceAssignment.class);
      subquery
          .select(subRoot.get("id"))
          .where(
              cb.and(
                  cb.equal(subRoot.get("toUser").get("id"), currentUserId),
                  cb.equal(subRoot.get("status"), AssignmentStatus.ASSIGNED),
                  cb.isNull(subRoot.get("unassignDate")),
                  cb.equal(subRoot.get("device").get("id"), root.get("id"))));

      return cb.exists(subquery);
    };
  }

  /**
   * TEACHER row-level filter: saját device-ok (aktív assignment) VAGY az irodájában lévő device-ok
   * (device utolsó assignment toLocation = user officeLocation).
   *
   * @param currentUserId a bejelentkezett user ID-ja
   * @param currentUser a teljes AppUser entitás (officeLocation-nel)
   * @return Specification ami az OR-t kombinálja
   */
  public static Specification<Device> teacherAccess(Long currentUserId, AppUser currentUser) {
    Specification<Device> ownDevices = hasAccess(currentUserId);

    Specification<Device> officeDevices =
        (root, query, cb) -> {
          if (currentUser.getOfficeLocation() == null) {
            return cb.disjunction(); // nincs iroda → nincs extra device
          }

          Subquery<Long> subquery = query.subquery(Long.class);
          var subRoot = subquery.from(DeviceAssignment.class);
          subquery
              .select(subRoot.get("id"))
              .where(
                  cb.and(
                      cb.equal(
                          subRoot.get("toLocation").get("id"),
                          currentUser.getOfficeLocation().getId()),
                      cb.equal(subRoot.get("status"), AssignmentStatus.ASSIGNED),
                      cb.isNull(subRoot.get("unassignDate")),
                      cb.equal(subRoot.get("device").get("id"), root.get("id"))));

          return cb.exists(subquery);
        };

    return Specification.anyOf(ownDevices, officeDevices);
  }
}
