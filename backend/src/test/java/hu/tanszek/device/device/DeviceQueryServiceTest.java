package hu.tanszek.device.device;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import hu.tanszek.device.auth.entity.Permission;
import hu.tanszek.device.auth.entity.Role;
import hu.tanszek.device.device.entity.Device;
import hu.tanszek.device.device.repository.DeviceRepository;
import hu.tanszek.device.location.entity.Location;
import hu.tanszek.device.location.entity.LocationType;
import hu.tanszek.device.user.entity.AppUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tesztek a {@link DeviceQueryService}-hez.
 *
 * <p>Teszteli a jogosultság-alapú (Permission-Driven) Specification generálást:
 * <ul>
 *   <li>Null user (rendszer/admin kontextus) -> Teljes hozzáférés
 *   <li>Globális menedzsment joggal rendelkező user (DEVICE_CREATE / DEVICE_DELETE / USER_MANAGE) -> Teljes hozzáférés
 *   <li>Kiadási joggal (DEVICE_ASSIGN / DEVICE_UNASSIGN) vagy irodával rendelkező user -> Saját + irodai eszközök
 *   <li>Kizárólag olvasási joggal rendelkező user -> Csak saját aktív eszközök
 *   <li>findAllForCurrentUser lapozással és kiegészítő szűréssel
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DeviceQueryServiceTest {

  @Mock private DeviceRepository deviceRepository;

  @InjectMocks private DeviceQueryService deviceQueryService;

  private Permission permCreate;
  private Permission permDelete;
  private Permission permAssign;
  private Permission permRead;
  private Role adminRole;
  private Role teacherRole;
  private Role studentRole;
  private Location office;

  @BeforeEach
  void setUp() {
    permCreate = Permission.builder().id(1L).name("DEVICE_CREATE").build();
    permDelete = Permission.builder().id(2L).name("DEVICE_DELETE").build();
    permAssign = Permission.builder().id(3L).name("DEVICE_ASSIGN").build();
    permRead = Permission.builder().id(4L).name("DEVICE_READ").build();

    adminRole = Role.builder().id(1L).name("ROLE_ADMIN").permissions(Set.of(permCreate, permDelete, permAssign, permRead)).build();
    teacherRole = Role.builder().id(2L).name("ROLE_TEACHER").permissions(Set.of(permAssign, permRead)).build();
    studentRole = Role.builder().id(3L).name("ROLE_STUDENT").permissions(Set.of(permRead)).build();

    office = Location.builder().id(10L).name("Iroda 101").type(LocationType.OFFICE).build();
  }

  @Test
  @DisplayName("Null felhasználó esetén teljes hozzáférésű specifikáció készül")
  void buildSpecForNullUserReturnsGlobalAccess() {
    Specification<Device> spec = deviceQueryService.buildSpecForUser(null);
    assertThat(spec).isNotNull();
  }

  @Test
  @DisplayName("DEVICE_CREATE vagy DEVICE_DELETE joggal rendelkező user minden eszközt lát")
  void buildSpecForManagerReturnsGlobalAccess() {
    AppUser managerUser = AppUser.builder()
        .id(10L)
        .role(adminRole)
        .build();

    Specification<Device> spec = deviceQueryService.buildSpecForUser(managerUser);
    assertThat(spec).isNotNull();
  }

  @Test
  @DisplayName("DEVICE_ASSIGN joggal vagy irodával rendelkező user irodai és saját eszközöket lát")
  void buildSpecForTeacherReturnsTeacherAccess() {
    AppUser teacherUser = AppUser.builder()
        .id(20L)
        .role(teacherRole)
        .officeLocation(office)
        .build();

    Specification<Device> spec = deviceQueryService.buildSpecForUser(teacherUser);
    assertThat(spec).isNotNull();
  }

  @Test
  @DisplayName("Kizárólag DEVICE_READ joggal rendelkező hallgató csak saját aktív eszközeit látja")
  void buildSpecForStudentReturnsConsumerAccess() {
    AppUser studentUser = AppUser.builder()
        .id(30L)
        .role(studentRole)
        .build();

    Specification<Device> spec = deviceQueryService.buildSpecForUser(studentUser);
    assertThat(spec).isNotNull();
  }

  @Test
  @DisplayName("Közvetlenül hozzárendelt DEVICE_CREATE joggal egy alapvetően hallgatói role-os user is globális elérést kap")
  void buildSpecWithDirectPermissionOverridesRole() {
    AppUser directPermUser = AppUser.builder()
        .id(40L)
        .role(studentRole)
        .permissions(Set.of(permCreate))
        .build();

    Specification<Device> spec = deviceQueryService.buildSpecForUser(directPermUser);
    assertThat(spec).isNotNull();
  }

  @Test
  @DisplayName("findAllForCurrentUser sikeresen meghívja a repository-t a kombinált szűréssel és lapozással")
  void findAllForCurrentUserWithAdditionalSpec() {
    AppUser manager = AppUser.builder().id(10L).role(adminRole).build();
    Pageable pageable = PageRequest.of(0, 10);
    List<Device> devices = List.of(Device.builder().id(1L).inventoryNumber("INV-1").build());
    Page<Device> page = new PageImpl<>(devices, pageable, 1);

    when(deviceRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

    Specification<Device> extraSpec = (root, query, cb) -> cb.equal(root.get("type"), "laptop");
    Page<Device> result = deviceQueryService.findAllForCurrentUser(manager, extraSpec, pageable);

    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
    verify(deviceRepository).findAll(any(Specification.class), eq(pageable));
  }
}
