package hu.tanszek.device.device;

import hu.tanszek.device.assignment.entity.AssignmentStatus;
import hu.tanszek.device.assignment.entity.DeviceAssignment;
import hu.tanszek.device.assignment.repository.DeviceAssignmentRepository;
import hu.tanszek.device.auth.entity.Role;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tesztek a {@link DeviceService}-hez.
 *
 * <p>Teszteli:
 * <ul>
 *   <li>requestAssignment: sikeres, hibás device státusz, GROUP location</li>
 *   <li>approveAssignment: sikeres, nem PENDING státusz</li>
 *   <li>requestUnassignment: sikeres, nem aktív assignment</li>
 *   <li>approveUnassignment: sikeres</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock private DeviceRepository deviceRepository;
    @Mock private DeviceAssignmentRepository assignmentRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private AppUserRepository userRepository;

    @InjectMocks private DeviceService deviceService;

    private Device device;
    private Location office;
    private Location groupLocation;
    private AppUser byUser;
    private AppUser targetUser;

    @BeforeEach
    void setUp() {
        device = Device.builder()
                .id(1L)
                .type("laptop")
                .inventoryNumber("INV-001")
                .status(DeviceStatus.IN_STORAGE)
                .build();

        office = Location.builder()
                .id(10L)
                .name("Tanterem 101")
                .type(LocationType.CLASSROOM)
                .build();

        groupLocation = Location.builder()
                .id(20L)
                .name("Hallgatói Csoport")
                .type(LocationType.GROUP)
                .build();

        Role role = Role.builder().id(1L).name("ROLE_ADMIN").build();

        byUser = AppUser.builder()
                .id(100L)
                .emailHash("admin-hash")
                .role(role)
                .build();

        targetUser = AppUser.builder()
                .id(101L)
                .emailHash("user-hash")
                .role(role)
                .build();
    }

    // ===== requestAssignment =====

    @Test
    void requestAssignmentSuccess() {
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(locationRepository.findById(10L)).thenReturn(Optional.of(office));
        when(userRepository.findById(101L)).thenReturn(Optional.of(targetUser));
        when(userRepository.findById(100L)).thenReturn(Optional.of(byUser));
        when(assignmentRepository.findByDeviceIdAndActiveTrue(1L)).thenReturn(Optional.empty());
        when(assignmentRepository.save(any(DeviceAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeviceAssignment result = deviceService.requestAssignment(1L, 10L, 101L, 100L);

        assertThat(result.getStatus()).isEqualTo(AssignmentStatus.PENDING_ASSIGNMENT);
        assertThat(result.isActive()).isFalse();
        assertThat(result.getDevice()).isEqualTo(device);
        assertThat(result.getToLocation()).isEqualTo(office);
        assertThat(result.getToUser()).isEqualTo(targetUser);
        assertThat(result.getCreatedByUser()).isEqualTo(byUser);
    }

    @Test
    void requestAssignmentFailsWhenDeviceMaintenance() {
        device.setStatus(DeviceStatus.MAINTENANCE);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));

        assertThatThrownBy(() -> deviceService.requestAssignment(1L, 10L, 101L, 100L))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("MAINTENANCE");

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void requestAssignmentFailsWhenDeviceDisposed() {
        device.setStatus(DeviceStatus.DISPOSED);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));

        assertThatThrownBy(() -> deviceService.requestAssignment(1L, 10L, 101L, 100L))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("DISPOSED");
    }

    @Test
    void requestAssignmentFailsWhenTargetLocationIsGroup() {
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(locationRepository.findById(20L)).thenReturn(Optional.of(groupLocation));

        assertThatThrownBy(() -> deviceService.requestAssignment(1L, 20L, 101L, 100L))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("GROUP");
    }

    @Test
    void requestAssignmentFailsWhenDeviceNotFound() {
        when(deviceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceService.requestAssignment(99L, 10L, 101L, 100L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ===== approveAssignment =====

    @Test
    void approveAssignmentSuccess() {
        DeviceAssignment pending = DeviceAssignment.builder()
                .id(50L)
                .device(device)
                .status(AssignmentStatus.PENDING_ASSIGNMENT)
                .active(false)
                .build();

        when(assignmentRepository.findById(50L)).thenReturn(Optional.of(pending));
        when(userRepository.findById(100L)).thenReturn(Optional.of(byUser));
        when(assignmentRepository.save(any(DeviceAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeviceAssignment result = deviceService.approveAssignment(50L, 100L);

        assertThat(result.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(result.isActive()).isTrue();
        assertThat(result.getApprovedBy()).isEqualTo(byUser);
        assertThat(result.getDateOfAssignment()).isNotNull();
        verify(deviceRepository, times(1)).save(device);
        assertThat(device.getStatus()).isEqualTo(DeviceStatus.ASSIGNED);
    }

    @Test
    void approveAssignmentFailsWhenNotPending() {
        DeviceAssignment notPending = DeviceAssignment.builder()
                .id(50L)
                .device(device)
                .status(AssignmentStatus.ASSIGNED)
                .active(true)
                .build();
        when(assignmentRepository.findById(50L)).thenReturn(Optional.of(notPending));

        assertThatThrownBy(() -> deviceService.approveAssignment(50L, 100L))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("not in PENDING_ASSIGNMENT");
    }

    // ===== requestUnassignment =====

    @Test
    void requestUnassignmentSuccess() {
        DeviceAssignment active = DeviceAssignment.builder()
                .id(50L)
                .device(device)
                .toLocation(office)
                .toUser(targetUser)
                .status(AssignmentStatus.ASSIGNED)
                .active(true)
                .build();
        when(assignmentRepository.findById(50L)).thenReturn(Optional.of(active));
        when(userRepository.findById(100L)).thenReturn(Optional.of(byUser));
        when(assignmentRepository.save(any(DeviceAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeviceAssignment result = deviceService.requestUnassignment(50L, 100L);

        // Régi aktív inaktiválva
        assertThat(active.isActive()).isFalse();
        // Új PENDING_UNASSIGNMENT rekord
        assertThat(result.getStatus()).isEqualTo(AssignmentStatus.PENDING_UNASSIGNMENT);
        assertThat(result.isActive()).isFalse();
        assertThat(result.getDevice()).isEqualTo(device);
    }

    @Test
    void requestUnassignmentFailsWhenNotActive() {
        DeviceAssignment inactive = DeviceAssignment.builder()
                .id(50L)
                .device(device)
                .status(AssignmentStatus.ASSIGNED)
                .active(false)
                .build();
        when(assignmentRepository.findById(50L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> deviceService.requestUnassignment(50L, 100L))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("not active");
    }

    // ===== approveUnassignment =====

    @Test
    void approveUnassignmentSuccess() {
        DeviceAssignment pendingUnassign = DeviceAssignment.builder()
                .id(60L)
                .device(device)
                .status(AssignmentStatus.PENDING_UNASSIGNMENT)
                .active(false)
                .build();
        when(assignmentRepository.findById(60L)).thenReturn(Optional.of(pendingUnassign));
        when(userRepository.findById(100L)).thenReturn(Optional.of(byUser));
        when(assignmentRepository.save(any(DeviceAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeviceAssignment result = deviceService.approveUnassignment(60L, 100L);

        assertThat(result.getStatus()).isEqualTo(AssignmentStatus.IN_STORAGE);
        assertThat(result.isActive()).isTrue();
        verify(deviceRepository, times(1)).save(device);
        assertThat(device.getStatus()).isEqualTo(DeviceStatus.IN_STORAGE);
    }
}