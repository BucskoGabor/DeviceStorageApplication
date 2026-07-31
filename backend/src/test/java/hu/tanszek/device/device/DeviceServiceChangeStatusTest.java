package hu.tanszek.device.device;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import hu.tanszek.device.assignment.repository.DeviceAssignmentRepository;
import hu.tanszek.device.common.BusinessValidationException;
import hu.tanszek.device.common.ResourceNotFoundException;
import hu.tanszek.device.device.entity.Device;
import hu.tanszek.device.device.entity.DeviceStatus;
import hu.tanszek.device.device.repository.DeviceRepository;
import hu.tanszek.device.location.repository.LocationRepository;
import hu.tanszek.device.user.repository.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tesztek a {@link DeviceService#changeStatus(Long, DeviceStatus)} metódushoz.
 *
 * <p>Teszteli:
 *
 * <ul>
 *   <li>Sikeres átmenetek (PENDING → IN_STORAGE, IN_STORAGE → MAINTENANCE, stb.)
 *   <li>Érvénytelen átmenetek (pl. PENDING → DISPOSED, bármi → PENDING, DISPOSED → bármi)
 *   <li>Ugyanarra a státuszra váltás no-op
 *   <li>IN_STORAGE váltáskor az aktív assignment inaktívvá válik
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DeviceServiceChangeStatusTest {

  @Mock private DeviceRepository deviceRepository;
  @Mock private DeviceAssignmentRepository assignmentRepository;
  @Mock private LocationRepository locationRepository;
  @Mock private AppUserRepository userRepository;

  @InjectMocks private DeviceService deviceService;

  private Device device;

  @BeforeEach
  void setUp() {
    device =
        Device.builder()
            .id(1L)
            .inventoryNumber("INV-001")
            .type("laptop")
            .status(DeviceStatus.PENDING)
            .build();
  }

  @Test
  void changeStatus_succeedsForPendingToInStorage() {
    when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
    when(deviceRepository.save(device)).thenReturn(device);

    Device result = deviceService.changeStatus(1L, DeviceStatus.IN_STORAGE);

    assertThat(result.getStatus()).isEqualTo(DeviceStatus.IN_STORAGE);
  }

  @Test
  void changeStatus_succeedsForInStorageToMaintenance() {
    device.setStatus(DeviceStatus.IN_STORAGE);
    when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
    when(deviceRepository.save(device)).thenReturn(device);

    Device result = deviceService.changeStatus(1L, DeviceStatus.MAINTENANCE);

    assertThat(result.getStatus()).isEqualTo(DeviceStatus.MAINTENANCE);
  }

  @Test
  void changeStatus_succeedsForMaintenanceToDisposed() {
    device.setStatus(DeviceStatus.MAINTENANCE);
    when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
    when(deviceRepository.save(device)).thenReturn(device);

    Device result = deviceService.changeStatus(1L, DeviceStatus.DISPOSED);

    assertThat(result.getStatus()).isEqualTo(DeviceStatus.DISPOSED);
  }

  @Test
  void changeStatus_rejectsDisposedAsFinalState() {
    device.setStatus(DeviceStatus.DISPOSED);
    when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));

    assertThatThrownBy(() -> deviceService.changeStatus(1L, DeviceStatus.IN_STORAGE))
        .isInstanceOf(BusinessValidationException.class)
        .hasMessageContaining("deviceStatusTransitionNotAllowed");

    verify(deviceRepository, never()).save(device);
  }

  @Test
  void changeStatus_rejectsPendingToAssigned() {
    // PENDING → ASSIGNED nincs a state machine-ben (az assignment workflow-ban van)
    when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));

    assertThatThrownBy(() -> deviceService.changeStatus(1L, DeviceStatus.ASSIGNED))
        .isInstanceOf(BusinessValidationException.class)
        .hasMessageContaining("deviceStatusTransitionNotAllowed");
  }

  @Test
  void changeStatus_rejectsInStorageToAssigned() {
    device.setStatus(DeviceStatus.IN_STORAGE);
    when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));

    // IN_STORAGE → ASSIGNED sincs — az assignment workflow kezeli
    assertThatThrownBy(() -> deviceService.changeStatus(1L, DeviceStatus.ASSIGNED))
        .isInstanceOf(BusinessValidationException.class);
  }

  @Test
  void changeStatus_isNoOpWhenSameStatus() {
    device.setStatus(DeviceStatus.IN_STORAGE);
    when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));

    Device result = deviceService.changeStatus(1L, DeviceStatus.IN_STORAGE);

    assertThat(result.getStatus()).isEqualTo(DeviceStatus.IN_STORAGE);
    verify(deviceRepository, never()).save(device);
  }

  @Test
  void changeStatus_throwsWhenDeviceNotFound() {
    when(deviceRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> deviceService.changeStatus(99L, DeviceStatus.IN_STORAGE))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void changeStatus_toInStorageInactivatesActiveAssignment() {
    device.setStatus(DeviceStatus.ASSIGNED);
    hu.tanszek.device.assignment.entity.DeviceAssignment activeAssignment =
        hu.tanszek.device.assignment.entity.DeviceAssignment.builder()
            .id(10L)
            .device(device)
            .active(true)
            .build();
    when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
    when(assignmentRepository.findByDeviceIdAndActiveTrue(1L))
        .thenReturn(Optional.of(activeAssignment));
    when(assignmentRepository.save(activeAssignment)).thenReturn(activeAssignment);
    when(deviceRepository.save(device)).thenReturn(device);

    Device result = deviceService.changeStatus(1L, DeviceStatus.IN_STORAGE);

    assertThat(result.getStatus()).isEqualTo(DeviceStatus.IN_STORAGE);
    assertThat(activeAssignment.isActive()).isFalse();
    assertThat(activeAssignment.getUnassignCreatedDate()).isNotNull();
    verify(assignmentRepository).save(activeAssignment);
  }
}
