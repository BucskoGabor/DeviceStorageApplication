package hu.tanszek.device.assignment;

import hu.tanszek.device.assignment.controller.AssignmentController;
import hu.tanszek.device.assignment.entity.AssignmentStatus;
import hu.tanszek.device.assignment.entity.DeviceAssignment;
import hu.tanszek.device.assignment.repository.DeviceAssignmentRepository;
import hu.tanszek.device.device.DeviceService;
import hu.tanszek.device.device.entity.Device;
import hu.tanszek.device.device.repository.DeviceRepository;
import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tesztek a {@link AssignmentController} "pending" és "findByDevice" végpontjaihoz.
 *
 * <p>A service-szintű unit tesztek a DeviceServiceTest-ben vannak (DeviceService
 * az, ami a tényleges state machine logikát tartalmazza). Itt csak a controller
 * szintű delegációt és a SecurityContext → userId feloldást teszteljük.
 */
@ExtendWith(MockitoExtension.class)
class AssignmentControllerTest {

    @Mock private DeviceService deviceService;
    @Mock private DeviceAssignmentRepository assignmentRepository;
    @Mock private DeviceRepository deviceRepository;
    @Mock private AppUserRepository userRepository;

    @InjectMocks private AssignmentController controller;

    @Test
    void requestAssignment_resolvesUserIdFromAuthenticationAndDelegatesToService() {
        AppUser user = AppUser.builder().id(42L).emailHash("hash-xyz").build();
        DeviceAssignment expected = DeviceAssignment.builder().id(100L).build();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "hash-xyz", null, List.of(new SimpleGrantedAuthority("DEVICE_ASSIGN")));

        when(userRepository.findByEmailHash("hash-xyz")).thenReturn(Optional.of(user));
        when(deviceService.requestAssignment(eq(7L), eq(3L), eq(null), eq(42L))).thenReturn(expected);

        AssignmentController.CreateAssignmentRequest req =
                new AssignmentController.CreateAssignmentRequest(3L, null);

        var response = controller.requestAssignment(7L, req, auth);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isSameAs(expected);
        verify(deviceService).requestAssignment(7L, 3L, null, 42L);
    }

    @Test
    void requestAssignment_passesTargetUserIdWhenProvided() {
        AppUser user = AppUser.builder().id(42L).emailHash("hash-xyz").build();
        DeviceAssignment expected = DeviceAssignment.builder().id(100L).build();
        Authentication auth = new UsernamePasswordAuthenticationToken("hash-xyz", null, List.of());

        when(userRepository.findByEmailHash("hash-xyz")).thenReturn(Optional.of(user));
        when(deviceService.requestAssignment(eq(7L), eq(3L), eq(99L), eq(42L))).thenReturn(expected);

        AssignmentController.CreateAssignmentRequest req =
                new AssignmentController.CreateAssignmentRequest(3L, 99L);

        controller.requestAssignment(7L, req, auth);

        verify(deviceService).requestAssignment(7L, 3L, 99L, 42L);
    }

    @Test
    void approveAssignment_resolvesUserIdFromAuthenticationAndDelegatesToService() {
        AppUser approver = AppUser.builder().id(42L).emailHash("hash-xyz").build();
        DeviceAssignment approved = DeviceAssignment.builder()
                .id(100L).status(AssignmentStatus.ASSIGNED).active(true).build();
        Authentication auth = new UsernamePasswordAuthenticationToken("hash-xyz", null, List.of());

        when(userRepository.findByEmailHash("hash-xyz")).thenReturn(Optional.of(approver));
        when(deviceService.approveAssignment(100L, 42L)).thenReturn(approved);

        var response = controller.approveAssignment(100L, auth);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
    }

    @Test
    void requestUnassignment_createsPendingUnassignmentRecord() {
        AppUser requester = AppUser.builder().id(42L).emailHash("hash-xyz").build();
        DeviceAssignment unassignRequest = DeviceAssignment.builder()
                .id(101L).status(AssignmentStatus.PENDING_UNASSIGNMENT).build();
        Authentication auth = new UsernamePasswordAuthenticationToken("hash-xyz", null, List.of());

        when(userRepository.findByEmailHash("hash-xyz")).thenReturn(Optional.of(requester));
        when(deviceService.requestUnassignment(100L, 42L)).thenReturn(unassignRequest);

        var response = controller.requestUnassignment(100L, auth);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().getStatus()).isEqualTo(AssignmentStatus.PENDING_UNASSIGNMENT);
    }

    @Test
    void approveUnassignment_setsStatusToInStorage() {
        AppUser approver = AppUser.builder().id(42L).emailHash("hash-xyz").build();
        DeviceAssignment approved = DeviceAssignment.builder()
                .id(101L).status(AssignmentStatus.IN_STORAGE).active(true).build();
        Authentication auth = new UsernamePasswordAuthenticationToken("hash-xyz", null, List.of());

        when(userRepository.findByEmailHash("hash-xyz")).thenReturn(Optional.of(approver));
        when(deviceService.approveUnassignment(101L, 42L)).thenReturn(approved);

        var response = controller.approveUnassignment(101L, auth);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getStatus()).isEqualTo(AssignmentStatus.IN_STORAGE);
    }

    @Test
    void findPending_returnsOnlyPendingStatuses() {
        DeviceAssignment pending1 = DeviceAssignment.builder()
                .id(1L).status(AssignmentStatus.PENDING_ASSIGNMENT).createdDate(Instant.now()).build();
        DeviceAssignment pending2 = DeviceAssignment.builder()
                .id(2L).status(AssignmentStatus.PENDING_UNASSIGNMENT).createdDate(Instant.now()).build();

        when(assignmentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(pending1, pending2));

        var response = controller.findPending();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody())
                .extracting(DeviceAssignment::getStatus)
                .containsExactlyInAnyOrder(AssignmentStatus.PENDING_ASSIGNMENT, AssignmentStatus.PENDING_UNASSIGNMENT);
    }

    @Test
    void findByDevice_throwsWhenDeviceNotFound() {
        when(deviceRepository.existsById(99L)).thenReturn(false);
        Authentication auth = new UsernamePasswordAuthenticationToken("hash-xyz", null, List.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> controller.findByDevice(99L, 0, 20))
                .isInstanceOf(hu.tanszek.device.common.ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(assignmentRepository, never()).findAll(
                any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class));
    }
}
