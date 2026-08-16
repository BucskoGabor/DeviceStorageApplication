package hu.tanszek.device.assignment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import hu.tanszek.device.assignment.entity.AssignmentStatus;
import hu.tanszek.device.assignment.entity.DeviceAssignment;

/**
 * Device assignment repository.
 *
 * @see DeviceAssignment
 */
@Repository
public interface DeviceAssignmentRepository
    extends JpaRepository<DeviceAssignment, Long>, JpaSpecificationExecutor<DeviceAssignment> {

  /** Device aktív (ASSIGNED status) assignmentje. */
  Optional<DeviceAssignment> findFirstByDeviceIdAndStatus(Long deviceId, AssignmentStatus status);

  /** User összes assignment listája. */
  List<DeviceAssignment> findByToUserId(Long userId);

  /** User összes hozzárendelési előzménye (cél vagy forrás). */
  List<DeviceAssignment> findByToUserIdOrFromUserIdOrderByCreatedDateDesc(
      Long toUserId, Long fromUserId);

  /** Usernél lévő jelenlegi aktív eszközök. */
  @org.springframework.data.jpa.repository.Query("""
      SELECT da.device FROM DeviceAssignment da
      WHERE da.toUser.id = :userId
        AND da.status = hu.tanszek.device.assignment.entity.AssignmentStatus.ASSIGNED
        AND da.device.status = hu.tanszek.device.device.entity.DeviceStatus.ASSIGNED
        AND da.unassignCreatedDate IS NULL
      ORDER BY da.createdDate DESC
  """)
  List<hu.tanszek.device.device.entity.Device> findCurrentDevicesByUserId(
      @org.springframework.data.repository.query.Param("userId") Long userId);

  /** Location összes assignment listája. */
  List<DeviceAssignment> findByToLocationId(Long locationId);

  /** Location összes hozzárendelési előzménye (cél vagy forrás). */
  List<DeviceAssignment> findByToLocationIdOrFromLocationIdOrderByCreatedDateDesc(
      Long toLocationId, Long fromLocationId);

  /** Device összes assignment listája (history). */
  List<DeviceAssignment> findByDeviceIdOrderByCreatedDateDesc(Long deviceId);

  /** Legutóbbi aktív (ASSIGNED) assignment egy device-hoz. */
  Optional<DeviceAssignment> findFirstByDeviceIdAndStatusOrderByCreatedDateDesc(
      Long deviceId, AssignmentStatus status);

  /**
   * Ellenőrzi, hogy a felhasználónak van-e jelenleg nála lévő aktív eszköze vagy folyamatban lévő kérése.
   *
   * <p>A history-ban lévő, már visszavett vagy továbbadott korábbi hozzárendelések nem számítanak aktívnak.
   */
  @org.springframework.data.jpa.repository.Query("""
      SELECT COUNT(da) > 0 FROM DeviceAssignment da
      WHERE (
          (da.toUser.id = :userId AND da.status = hu.tanszek.device.assignment.entity.AssignmentStatus.PENDING_ASSIGNMENT)
          OR (da.fromUser.id = :userId AND da.status = hu.tanszek.device.assignment.entity.AssignmentStatus.PENDING_UNASSIGNMENT)
          OR (da.toUser.id = :userId
              AND da.status = hu.tanszek.device.assignment.entity.AssignmentStatus.ASSIGNED
              AND da.device.status = hu.tanszek.device.device.entity.DeviceStatus.ASSIGNED
              AND da.unassignCreatedDate IS NULL)
      )
  """)
  boolean hasActiveOrPendingAssignments(
      @org.springframework.data.repository.query.Param("userId") Long userId);
}
