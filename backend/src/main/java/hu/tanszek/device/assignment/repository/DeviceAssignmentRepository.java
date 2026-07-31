package hu.tanszek.device.assignment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import hu.tanszek.device.assignment.entity.DeviceAssignment;

/**
 * Device assignment repository.
 *
 * @see DeviceAssignment
 */
@Repository
public interface DeviceAssignmentRepository
    extends JpaRepository<DeviceAssignment, Long>, JpaSpecificationExecutor<DeviceAssignment> {

  /** Device aktív assignmentje. */
  Optional<DeviceAssignment> findByDeviceIdAndActiveTrue(Long deviceId);

  /** User összes assignment listája. */
  List<DeviceAssignment> findByToUserId(Long userId);

  /** Location összes assignment listája. */
  List<DeviceAssignment> findByToLocationId(Long locationId);

  /** Device összes assignment listája (history). */
  List<DeviceAssignment> findByDeviceIdOrderByCreatedDateDesc(Long deviceId);
}
