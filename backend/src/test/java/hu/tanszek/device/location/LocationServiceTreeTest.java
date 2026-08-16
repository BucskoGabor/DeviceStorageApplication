package hu.tanszek.device.location;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import hu.tanszek.device.location.dto.LocationTreeDto;
import hu.tanszek.device.location.entity.Location;
import hu.tanszek.device.location.entity.LocationType;
import hu.tanszek.device.location.repository.LocationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tesztek a {@link LocationService#buildTree()} metódushoz.
 *
 * <p>Teszteli:
 *
 * <ul>
 *   <li>Egyszintű fa (csak root node-ok) helyes visszaadása
 *   <li>Többszintű fa rekurzív bejárása
 *   <li>Üres fa (nincs root node) üres listát ad
 *   <li>MAX_TREE_DEPTH (10) korlát működése
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class LocationServiceTreeTest {

  @Mock private LocationRepository locationRepository;

  @InjectMocks private LocationService locationService;

  @Test
  void buildTree_returnsEmptyListWhenNoRoots() {
    when(locationRepository.findByParentIsNull()).thenReturn(List.of());

    List<LocationTreeDto> result = locationService.buildTree();

    assertThat(result).isEmpty();
  }

  @Test
  void buildTree_returnsFlatListOfRoots() {
    Location root1 = location(1L, "Root A", LocationType.OFFICE, null);
    Location root2 = location(2L, "Root B", LocationType.STORAGE, null);

    when(locationRepository.findByParentIsNull()).thenReturn(List.of(root1, root2));

    List<LocationTreeDto> result = locationService.buildTree();

    assertThat(result).hasSize(2);
    assertThat(result.get(0).id()).isEqualTo(1L);
    assertThat(result.get(0).depth()).isEqualTo(0);
    assertThat(result.get(0).children()).isEmpty();
    assertThat(result.get(1).id()).isEqualTo(2L);
  }

  @Test
  void buildTree_buildsHierarchyWithChildren() {
    // Root (1) → Child (2) → GrandChild (3)
    Location grandChild = location(3L, "GrandChild", LocationType.CLASSROOM, null);
    Location child = locationWithChildren(2L, "Child", LocationType.OFFICE, List.of(grandChild));
    Location root = locationWithChildren(1L, "Root", LocationType.OFFICE, List.of(child));

    when(locationRepository.findByParentIsNull()).thenReturn(List.of(root));

    List<LocationTreeDto> result = locationService.buildTree();

    assertThat(result).hasSize(1);
    LocationTreeDto rootDto = result.get(0);
    assertThat(rootDto.id()).isEqualTo(1L);
    assertThat(rootDto.depth()).isEqualTo(0);

    assertThat(rootDto.children()).hasSize(1);
    LocationTreeDto childDto = rootDto.children().get(0);
    assertThat(childDto.id()).isEqualTo(2L);
    assertThat(childDto.parentId()).isEqualTo(1L);
    assertThat(childDto.depth()).isEqualTo(1);

    assertThat(childDto.children()).hasSize(1);
    LocationTreeDto grandChildDto = childDto.children().get(0);
    assertThat(grandChildDto.id()).isEqualTo(3L);
    assertThat(grandChildDto.parentId()).isEqualTo(2L);
    assertThat(grandChildDto.depth()).isEqualTo(2);
  }

  @Test
  void buildTree_capsDepthAtMax() {
    // 12 szintű lánc — MAX_TREE_DEPTH = 10, így a 10. szint után üres children
    Location deepNode = location(12L, "Lvl 12", LocationType.OFFICE, null);
    Location current = deepNode;
    for (long i = 11; i >= 1; i--) {
      Location parent = locationWithChildren(i, "Lvl " + i, LocationType.OFFICE, List.of(current));
      current = parent;
    }
    Location root = current;

    when(locationRepository.findByParentIsNull()).thenReturn(List.of(root));

    List<LocationTreeDto> result = locationService.buildTree();

    // 12 szintű lánc, de a MAX_TREE_DEPTH (10) feletti node-ok üres children listát kapnak
    LocationTreeDto node = result.get(0);
    int depthSeen = 0;
    while (!node.children().isEmpty() && depthSeen < 20) {
      node = node.children().get(0);
      depthSeen++;
    }

    // A 10-es mélység elérése után a children üres
    assertThat(node.depth()).isLessThanOrEqualTo(10);
  }

  // ===== Helpers =====

  private Location location(Long id, String name, LocationType type, Location parent) {
    Location loc = Location.builder().name(name).type(type).parent(parent).version(0L).build();
    loc.setId(id);
    return loc;
  }

  private Location locationWithChildren(
      Long id, String name, LocationType type, List<Location> children) {
    Location loc = location(id, name, type, null);
    loc.setChildren(new ArrayList<>(children));
    return loc;
  }
}
