package hu.tanszek.device.device;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import hu.tanszek.device.assignment.entity.AssignmentStatus;
import hu.tanszek.device.assignment.entity.DeviceAssignment;
import hu.tanszek.device.assignment.repository.DeviceAssignmentRepository;
import hu.tanszek.device.common.BusinessValidationException;
import hu.tanszek.device.device.entity.Device;
import hu.tanszek.device.device.entity.DeviceStatus;
import hu.tanszek.device.device.repository.DeviceRepository;
import hu.tanszek.device.location.entity.Location;
import hu.tanszek.device.location.entity.LocationType;
import hu.tanszek.device.user.entity.AppUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceAssignmentConcurrencyTest {

  @Mock private DeviceRepository deviceRepository;
  @Mock private DeviceAssignmentRepository assignmentRepository;
  @Mock private hu.tanszek.device.user.repository.AppUserRepository userRepository;

  @InjectMocks private DeviceService deviceService;

  private Device device;
  private DeviceAssignment pendingAssignment;
  private AppUser user;

  @BeforeEach
  void setUp() {
    device =
        Device.builder()
            .id(1L)
            .inventoryNumber("INV-001")
            .type("Laptop")
            .status(DeviceStatus.IN_STORAGE)
            .build();

    Location office = Location.builder().name("Office 101").type(LocationType.OFFICE).build();
    office.setId(10L);

    user = AppUser.builder().emailHash("hash-001").emailEncrypted("enc-001").build();
    user.setId(5L);

    pendingAssignment =
        DeviceAssignment.builder()
            .id(50L)
            .device(device)
            .toUser(user)
            .status(AssignmentStatus.PENDING_ASSIGNMENT)
            .build();
  }

  @Test
  void concurrentAssignmentApprovals_handlesLockingAndValidationProperly()
      throws InterruptedException {
    int threadCount = 8;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch readyLatch = new CountDownLatch(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failureCount = new AtomicInteger(0);
    List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

    java.util.concurrent.atomic.AtomicBoolean isAssigned =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    when(assignmentRepository.findById(50L))
        .thenAnswer(
            invocation -> {
              if (isAssigned.get()) {
                return Optional.of(
                    DeviceAssignment.builder()
                        .id(50L)
                        .device(device)
                        .status(AssignmentStatus.ASSIGNED)
                        .build());
              }
              return Optional.of(pendingAssignment);
            });

    when(assignmentRepository.save(any(DeviceAssignment.class)))
        .thenAnswer(
            invocation -> {
              if (!isAssigned.compareAndSet(false, true)) {
                throw new OptimisticLockingFailureException("Version conflict");
              }
              pendingAssignment.setStatus(AssignmentStatus.ASSIGNED);
              return pendingAssignment;
            });

    when(userRepository.findById(any())).thenReturn(Optional.of(user));
    when(deviceRepository.save(any(Device.class))).thenAnswer(i -> i.getArgument(0));

    for (int i = 0; i < threadCount; i++) {
      final long adminId = 100L + i;
      executor.submit(
          () -> {
            readyLatch.countDown();
            try {
              startLatch.await();
              deviceService.approveAssignment(50L, adminId);
              successCount.incrementAndGet();
            } catch (BusinessValidationException | OptimisticLockingFailureException e) {
              failureCount.incrementAndGet();
              exceptions.add(e);
            } catch (Exception e) {
              exceptions.add(e);
            }
          });
    }

    readyLatch.await(5, TimeUnit.SECONDS);
    startLatch.countDown();
    executor.shutdown();
    executor.awaitTermination(5, TimeUnit.SECONDS);

    assertThat(successCount.get()).isEqualTo(1);
    assertThat(failureCount.get()).isEqualTo(threadCount - 1);
    assertThat(pendingAssignment.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
  }
}
