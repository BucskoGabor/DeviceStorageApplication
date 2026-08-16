package hu.tanszek.device.location;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import hu.tanszek.device.common.BusinessValidationException;
import hu.tanszek.device.common.ResourceNotFoundException;
import hu.tanszek.device.location.entity.Location;
import hu.tanszek.device.location.entity.LocationType;
import hu.tanszek.device.location.repository.LocationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationServiceMoveTest {

  @Mock private LocationRepository locationRepository;

  @InjectMocks private LocationService locationService;

  @Test
  void move_toNewParent_success() {
    Location loc =
        Location.builder().name("Office 1").type(LocationType.OFFICE).version(1L).build();
    loc.setId(1L);

    Location newParent =
        Location.builder().name("Building A").type(LocationType.OFFICE).version(1L).build();
    newParent.setId(2L);

    when(locationRepository.findById(1L)).thenReturn(Optional.of(loc));
    when(locationRepository.findById(2L)).thenReturn(Optional.of(newParent));
    when(locationRepository.save(any(Location.class))).thenAnswer(i -> i.getArgument(0));

    Location moved = locationService.move(1L, 2L);

    assertThat(moved.getParent()).isEqualTo(newParent);
    verify(locationRepository).save(loc);
  }

  @Test
  void move_toRoot_success() {
    Location parent = Location.builder().name("Parent").type(LocationType.OFFICE).build();
    parent.setId(2L);

    Location loc =
        Location.builder()
            .name("Office 1")
            .type(LocationType.OFFICE)
            .parent(parent)
            .version(1L)
            .build();
    loc.setId(1L);

    when(locationRepository.findById(1L)).thenReturn(Optional.of(loc));
    when(locationRepository.save(any(Location.class))).thenAnswer(i -> i.getArgument(0));

    Location moved = locationService.move(1L, null);

    assertThat(moved.getParent()).isNull();
    verify(locationRepository).save(loc);
  }

  @Test
  void move_throwsWhenLocationNotFound() {
    when(locationRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> locationService.move(1L, 2L))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void move_throwsWhenParentNotFound() {
    Location loc = Location.builder().name("Office 1").type(LocationType.OFFICE).build();
    loc.setId(1L);

    when(locationRepository.findById(1L)).thenReturn(Optional.of(loc));
    when(locationRepository.findById(2L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> locationService.move(1L, 2L))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void validateNoCycle_throwsWhenSelfParent() {
    assertThatThrownBy(() -> locationService.validateNoCycle(1L, 1L))
        .isInstanceOf(BusinessValidationException.class)
        .satisfies(
            e ->
                assertThat(((BusinessValidationException) e).getMessageKey())
                    .isEqualTo("locationCycleDetected"));
  }

  @Test
  void validateNoCycle_throwsWhenDescendantBecomesParent() {
    // 1 -> 2 -> 3, try to make 1 child of 3
    Location loc3 = Location.builder().id(3L).build();
    Location loc2 = Location.builder().id(2L).build();

    loc3.setParent(loc2);
    loc2.setParent(Location.builder().id(1L).build());

    when(locationRepository.findById(3L)).thenReturn(Optional.of(loc3));
    when(locationRepository.findById(2L)).thenReturn(Optional.of(loc2));

    assertThatThrownBy(() -> locationService.validateNoCycle(1L, 3L))
        .isInstanceOf(BusinessValidationException.class)
        .satisfies(
            e ->
                assertThat(((BusinessValidationException) e).getMessageKey())
                    .isEqualTo("locationCycleDetected"));
  }

  @Test
  void validateNoCycle_allowsValidParent() {
    Location loc2 = Location.builder().id(2L).build();
    when(locationRepository.findById(2L)).thenReturn(Optional.of(loc2));

    locationService.validateNoCycle(1L, 2L);
  }
}
