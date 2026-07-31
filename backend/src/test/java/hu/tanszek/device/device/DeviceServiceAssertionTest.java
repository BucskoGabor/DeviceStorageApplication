package hu.tanszek.device.device;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import hu.tanszek.device.assignment.entity.AssignmentStatus;
import hu.tanszek.device.assignment.entity.DeviceAssignment;
import hu.tanszek.device.assignment.repository.DeviceAssignmentRepository;
import hu.tanszek.device.common.BusinessValidationException;
import hu.tanszek.device.common.ResourceNotFoundException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tesztek a DeviceService state machine logikájához. (A korábbi DeviceServiceTest a
 * requestAssignment + approve + MAINTENANCE/GROUP ellenőrzéseket tartalmazta, ez a kiegészítés a
 * service assertion-öket és a repository interakciókat teszteli.)
 */
@ExtendWith(MockitoExtension.class)
class DeviceServiceAssertionTest {

  @Mock private DeviceRepository deviceRepository;
  @Mock private DeviceAssignmentRepository assignmentRepository;
  @Mock private LocationRepository locationRepository;
  @Mock private AppUserRepository userRepository;

  @InjectMocks private DeviceService deviceService;

  private Device device;
  private AppUser byUser;

  @BeforeEach
  void setUp() {
    device =
        Device.builder()
            .id(1L)
            .type("laptop")
            .inventoryNumber("INV-001")
            .status(DeviceStatus.IN_STORAGE)
            .build();

    byUser = AppUser.builder().id(100L).emailHash("admin-hash").build();
  }

  @Test
  void requestAssignmentInactivatesOldActiveAssignment() {
    DeviceAssignment oldActive =
        DeviceAssignment.builder()
            .id(50L)
            .device(device)
            .status(AssignmentStatus.ASSIGNED)
            .active(true)
            .build();

    when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
    when(locationRepository.findById(10L))
        .thenReturn(Optional.of(Location.builder().id(10L).type(LocationType.CLASSROOM).build()));
    when(userRepository.findById(100L)).thenReturn(Optional.of(byUser));
    when(assignmentRepository.findByDeviceIdAndActiveTrue(1L)).thenReturn(Optional.of(oldActive));
    when(assignmentRepository.save(any(DeviceAssignment.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    deviceService.requestAssignment(1L, 10L, null, 100L);

    // A régi aktív inaktívvá vált
    assertThat(oldActive.isActive()).isFalse();
    verify(assignmentRepository, times(1)).save(oldActive);
  }

  @Test
  void approveAssignmentSetsDeviceStatusToAssigned() {
    DeviceAssignment pending =
        DeviceAssignment.builder()
            .id(50L)
            .device(device)
            .status(AssignmentStatus.PENDING_ASSIGNMENT)
            .active(false)
            .build();

    when(assignmentRepository.findById(50L)).thenReturn(Optional.of(pending));
    when(userRepository.findById(100L)).thenReturn(Optional.of(byUser));
    when(assignmentRepository.save(any(DeviceAssignment.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    deviceService.approveAssignment(50L, 100L);

    assertThat(device.getStatus()).isEqualTo(DeviceStatus.ASSIGNED);
    verify(deviceRepository, times(1)).save(device);
  }

  @Test
  void approveUnassignmentRevertsToInStorage() {
    DeviceAssignment pendingUnassign =
        DeviceAssignment.builder()
            .id(60L)
            .device(device)
            .status(AssignmentStatus.PENDING_UNASSIGNMENT)
            .active(false)
            .build();

    when(assignmentRepository.findById(60L)).thenReturn(Optional.of(pendingUnassign));
    when(userRepository.findById(100L)).thenReturn(Optional.of(byUser));
    when(assignmentRepository.save(any(DeviceAssignment.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    deviceService.approveUnassignment(60L, 100L);

    assertThat(device.getStatus()).isEqualTo(DeviceStatus.IN_STORAGE);
    assertThat(pendingUnassign.getStatus()).isEqualTo(AssignmentStatus.IN_STORAGE);
    assertThat(pendingUnassign.isActive()).isTrue();
  }

  @Test
  void approveUnassignmentSetsUnassignApprover() {
    DeviceAssignment pendingUnassign =
        DeviceAssignment.builder()
            .id(60L)
            .device(device)
            .status(AssignmentStatus.PENDING_UNASSIGNMENT)
            .active(false)
            .build();

    when(assignmentRepository.findById(60L)).thenReturn(Optional.of(pendingUnassign));
    when(userRepository.findById(100L)).thenReturn(Optional.of(byUser));
    when(assignmentRepository.save(any(DeviceAssignment.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    deviceService.approveUnassignment(60L, 100L);

    assertThat(pendingUnassign.getUnassignApprovedBy()).isEqualTo(byUser);
    assertThat(pendingUnassign.getApprovedBy()).isEqualTo(byUser);
  }

  @Test
  void requestUnassignmentFailsIfNotActive() {
    DeviceAssignment inactive =
        DeviceAssignment.builder()
            .id(50L)
            .device(device)
            .status(AssignmentStatus.ASSIGNED)
            .active(false)
            .build();

    when(assignmentRepository.findById(50L)).thenReturn(Optional.of(inactive));

    assertThatThrownBy(() -> deviceService.requestUnassignment(50L, 100L))
        .isInstanceOf(BusinessValidationException.class)
        .hasMessageContaining("not active");

    verify(assignmentRepository, never()).save(any());
  }

  @Test
  void requestAssignmentThrowsIfDeviceNotFound() {
    when(deviceRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> deviceService.requestAssignment(99L, 10L, null, 100L))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
