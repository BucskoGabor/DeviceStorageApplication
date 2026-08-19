package hu.tanszek.device.device;

import java.util.List;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceServiceCrudAndWorkflowTest {

  @Mock private DeviceRepository deviceRepository;
  @Mock private DeviceAssignmentRepository assignmentRepository;
  @Mock private LocationRepository locationRepository;
  @Mock private AppUserRepository userRepository;

  @InjectMocks private DeviceService deviceService;

  private Device device;
  private Location officeLoc;
  private Location storageLoc;
  private AppUser user;

  @BeforeEach
  void setUp() {
    officeLoc = Location.builder().name("Office 1").type(LocationType.OFFICE).build();
    officeLoc.setId(10L);

    storageLoc = Location.builder().name("Storage 1").type(LocationType.STORAGE).build();
    storageLoc.setId(20L);

    device =
        Device.builder()
            .id(1L)
            .inventoryNumber("INV-100")
            .type("Laptop")
            .status(DeviceStatus.IN_STORAGE)
            .build();

    user = AppUser.builder().id(100L).emailHash("hash").emailEncrypted("enc").build();
  }

  @Test
  void requestMaintenance_success() {
    when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
    when(deviceRepository.save(any(Device.class))).thenAnswer(i -> i.getArgument(0));

    Device result = deviceService.requestMaintenance(1L, "Broken screen", 100L);

    assertThat(result.getStatus()).isEqualTo(DeviceStatus.PENDING_MAINTENANCE);
  }

  @Test
  void approveMaintenance_success() {
    device.setStatus(DeviceStatus.PENDING_MAINTENANCE);
    when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
    when(deviceRepository.save(any(Device.class))).thenAnswer(i -> i.getArgument(0));

    Device result = deviceService.approveMaintenance(1L, 100L);

    assertThat(result.getStatus()).isEqualTo(DeviceStatus.MAINTENANCE);
  }

  @Test
  void rejectMaintenance_success() {
    device.setStatus(DeviceStatus.PENDING_MAINTENANCE);
    when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
    when(deviceRepository.save(any(Device.class))).thenAnswer(i -> i.getArgument(0));

    Device result = deviceService.rejectMaintenance(1L, 100L);

    assertThat(result.getStatus()).isEqualTo(DeviceStatus.IN_STORAGE);
  }

  @Test
  void returnFromMaintenance_success() {
    device.setStatus(DeviceStatus.MAINTENANCE);
    when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
    when(deviceRepository.save(any(Device.class))).thenAnswer(i -> i.getArgument(0));

    Device result = deviceService.returnFromMaintenance(1L, 100L);

    assertThat(result.getStatus()).isEqualTo(DeviceStatus.IN_STORAGE);
  }

  @Test
  void requestDisposal_success() {
    when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
    when(deviceRepository.save(any(Device.class))).thenAnswer(i -> i.getArgument(0));

    Device result = deviceService.requestDisposal(1L, "Too old", 100L);

    assertThat(result.getStatus()).isEqualTo(DeviceStatus.PENDING_DISPOSAL);
  }

  @Test
  void approveDisposal_success() {
    device.setStatus(DeviceStatus.PENDING_DISPOSAL);
    when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
    when(deviceRepository.save(any(Device.class))).thenAnswer(i -> i.getArgument(0));

    Device result = deviceService.approveDisposal(1L, 100L);

    assertThat(result.getStatus()).isEqualTo(DeviceStatus.DISPOSED);
  }

  @Test
  void rejectDisposal_success() {
    device.setStatus(DeviceStatus.PENDING_DISPOSAL);
    when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
    when(deviceRepository.save(any(Device.class))).thenAnswer(i -> i.getArgument(0));

    Device result = deviceService.rejectDisposal(1L, 100L);

    assertThat(result.getStatus()).isEqualTo(DeviceStatus.IN_STORAGE);
  }

  @Test
  void findPendingMaintenanceAndDisposal() {
    when(deviceRepository.findByStatusOrderByCreatedAtDesc(DeviceStatus.PENDING_MAINTENANCE))
        .thenReturn(List.of(device));
    when(deviceRepository.findByStatusOrderByCreatedAtDesc(DeviceStatus.PENDING_DISPOSAL))
        .thenReturn(List.of(device));

    assertThat(deviceService.findPendingMaintenanceDevices()).hasSize(1);
    assertThat(deviceService.findPendingDisposalDevices()).hasSize(1);
  }

  @Test
  void rejectAssignment_pendingAssignment_success() {
    DeviceAssignment assignment =
        DeviceAssignment.builder()
            .id(50L)
            .device(device)
            .status(AssignmentStatus.PENDING_ASSIGNMENT)
            .build();

    when(assignmentRepository.findById(50L)).thenReturn(Optional.of(assignment));
    when(userRepository.findById(100L)).thenReturn(Optional.of(user));
    when(assignmentRepository.save(any(DeviceAssignment.class))).thenAnswer(i -> i.getArgument(0));

    DeviceAssignment rejected = deviceService.rejectAssignment(50L, 100L);

    assertThat(rejected.getStatus()).isEqualTo(AssignmentStatus.REJECTED);
    verify(deviceRepository).save(device);
  }

  @Test
  void rejectAssignment_pendingUnassignment_success() {
    DeviceAssignment assignment =
        DeviceAssignment.builder()
            .id(51L)
            .device(device)
            .status(AssignmentStatus.PENDING_UNASSIGNMENT)
            .build();

    when(assignmentRepository.findById(51L)).thenReturn(Optional.of(assignment));
    when(userRepository.findById(100L)).thenReturn(Optional.of(user));
    when(assignmentRepository.save(any(DeviceAssignment.class))).thenAnswer(i -> i.getArgument(0));

    DeviceAssignment rejected = deviceService.rejectAssignment(51L, 100L);

    assertThat(rejected.getStatus()).isEqualTo(AssignmentStatus.REJECTED);
    verify(deviceRepository).save(device);
  }

  @Test
  void requestUnassignment_throwsWhenTargetNotStorage() {
    DeviceAssignment assignment =
        DeviceAssignment.builder().id(60L).device(device).status(AssignmentStatus.ASSIGNED).build();

    when(assignmentRepository.findById(60L)).thenReturn(Optional.of(assignment));
    when(locationRepository.findById(10L)).thenReturn(Optional.of(officeLoc)); // OFFICE not STORAGE

    assertThatThrownBy(() -> deviceService.requestUnassignment(60L, 10L, 100L))
        .isInstanceOf(BusinessValidationException.class);
  }

  @Test
  void requestAssignment_throwsWhenDeviceDisposed() {
    device.setStatus(DeviceStatus.DISPOSED);
    when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));

    assertThatThrownBy(() -> deviceService.requestAssignment(1L, 10L, 100L, 100L))
        .isInstanceOf(BusinessValidationException.class);
  }
}
