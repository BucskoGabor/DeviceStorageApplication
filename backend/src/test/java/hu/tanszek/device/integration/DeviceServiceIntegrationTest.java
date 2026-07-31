package hu.tanszek.device.integration;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import hu.tanszek.device.assignment.entity.AssignmentStatus;
import hu.tanszek.device.assignment.repository.DeviceAssignmentRepository;
import hu.tanszek.device.common.BusinessValidationException;
import hu.tanszek.device.device.DeviceService;
import hu.tanszek.device.device.entity.Device;
import hu.tanszek.device.device.entity.DeviceStatus;
import hu.tanszek.device.device.repository.DeviceRepository;
import hu.tanszek.device.location.entity.Location;
import hu.tanszek.device.location.entity.LocationType;
import hu.tanszek.device.location.repository.LocationRepository;
import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration teszt a DeviceService-hez — valódi PostgreSQL konténerrel.
 *
 * <p>Teszteli az end-to-end flow-t: 1. Device + User + Location létrehozása 2. Assignment request →
 * APPROVE → device status = ASSIGNED 3. Unassign request → APPROVE → device status = IN_STORAGE
 */
class DeviceServiceIntegrationTest extends AbstractIntegrationTest {

  @Autowired private DeviceService deviceService;
  @Autowired private DeviceRepository deviceRepository;
  @Autowired private AppUserRepository userRepository;
  @Autowired private LocationRepository locationRepository;
  @Autowired private DeviceAssignmentRepository assignmentRepository;

  @Test
  @Transactional
  void fullAssignmentFlowWorks() {
    // 1. Setup: location + user + device
    Location classroom =
        locationRepository.save(
            Location.builder()
                .name("Integration Test Classroom")
                .type(LocationType.CLASSROOM)
                .version(0L)
                .build());

    AppUser byUser =
        userRepository.save(
            AppUser.builder()
                .emailEncrypted("encrypted")
                .emailHash("int-test-hash")
                .passwordHash("$argon2id$test")
                .active(true)
                .mustChangePassword(false)
                .failedLoginCount(0)
                .passwordChangedAt(java.time.Instant.now())
                .build());

    Device device =
        deviceRepository.save(
            Device.builder()
                .type("laptop")
                .inventoryNumber("INT-001")
                .status(DeviceStatus.IN_STORAGE)
                .build());

    // 2. Request assignment (PENDING_ASSIGNMENT)
    var pendingAssignment =
        deviceService.requestAssignment(
            device.getId(), classroom.getId(), byUser.getId(), byUser.getId());
    assertThat(pendingAssignment.getStatus()).isEqualTo(AssignmentStatus.PENDING_ASSIGNMENT);
    assertThat(pendingAssignment.isActive()).isFalse();
    assertThat(pendingAssignment.getToLocation()).isEqualTo(classroom);
    assertThat(pendingAssignment.getToUser()).isEqualTo(byUser);

    // 3. Approve assignment → ASSIGNED
    var approvedAssignment =
        deviceService.approveAssignment(pendingAssignment.getId(), byUser.getId());
    assertThat(approvedAssignment.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
    assertThat(approvedAssignment.isActive()).isTrue();
    assertThat(approvedAssignment.getDateOfAssignment()).isNotNull();

    // 4. Device status frissült-e
    Device updatedDevice = deviceRepository.findById(device.getId()).orElseThrow();
    assertThat(updatedDevice.getStatus()).isEqualTo(DeviceStatus.ASSIGNED);

    // 5. Request unassignment
    var unassignRequest =
        deviceService.requestUnassignment(approvedAssignment.getId(), byUser.getId());
    assertThat(unassignRequest.getStatus()).isEqualTo(AssignmentStatus.PENDING_UNASSIGNMENT);

    // 6. Approve unassignment → IN_STORAGE
    var completedUnassign =
        deviceService.approveUnassignment(unassignRequest.getId(), byUser.getId());
    assertThat(completedUnassign.getStatus()).isEqualTo(AssignmentStatus.IN_STORAGE);

    // 7. Device status végállapot: IN_STORAGE
    Device finalDevice = deviceRepository.findById(device.getId()).orElseThrow();
    assertThat(finalDevice.getStatus()).isEqualTo(DeviceStatus.IN_STORAGE);

    // 8. Cleanup: töröljük a teszt adatokat
    assignmentRepository.deleteAll(
        List.of(pendingAssignment, approvedAssignment, unassignRequest, completedUnassign));
    deviceRepository.delete(device);
    userRepository.delete(byUser);
    locationRepository.delete(classroom);
  }

  @Test
  @Transactional
  void cannotAssignGroupLocation() {
    // GROUP típusú location létrehozása
    Location group =
        locationRepository.save(
            Location.builder().name("Test Group").type(LocationType.GROUP).version(0L).build());

    AppUser byUser =
        userRepository.save(
            AppUser.builder()
                .emailEncrypted("enc")
                .emailHash("h2")
                .passwordHash("$argon2id$x")
                .active(true)
                .mustChangePassword(false)
                .failedLoginCount(0)
                .passwordChangedAt(java.time.Instant.now())
                .build());

    Device device =
        deviceRepository.save(
            Device.builder()
                .type("laptop")
                .inventoryNumber("INT-002")
                .status(DeviceStatus.IN_STORAGE)
                .build());

    // GROUP location-ra assignolni kell, hogy tiltva legyen
    assertThatThrownBy(
            () ->
                deviceService.requestAssignment(
                    device.getId(), group.getId(), null, byUser.getId()))
        .isInstanceOf(BusinessValidationException.class)
        .hasMessageContaining("GROUP");

    // Cleanup
    deviceRepository.delete(device);
    userRepository.delete(byUser);
    locationRepository.delete(group);
  }
}
