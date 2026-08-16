package hu.tanszek.device.audit;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import hu.tanszek.device.assignment.entity.AssignmentStatus;
import hu.tanszek.device.assignment.entity.DeviceAssignment;
import hu.tanszek.device.assignment.repository.DeviceAssignmentRepository;
import hu.tanszek.device.attachment.entity.DeviceAttachment;
import hu.tanszek.device.attachment.repository.DeviceAttachmentRepository;
import hu.tanszek.device.auth.entity.Permission;
import hu.tanszek.device.auth.entity.Role;
import hu.tanszek.device.auth.repository.RoleRepository;
import hu.tanszek.device.device.entity.Device;
import hu.tanszek.device.device.entity.DeviceStatus;
import hu.tanszek.device.device.repository.DeviceRepository;
import hu.tanszek.device.location.entity.Location;
import hu.tanszek.device.location.entity.LocationType;
import hu.tanszek.device.location.repository.LocationRepository;
import hu.tanszek.device.software.entity.Software;
import hu.tanszek.device.software.repository.SoftwareRepository;
import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityTypeRegistryTest {

  @Mock private DeviceRepository deviceRepository;
  @Mock private AppUserRepository userRepository;
  @Mock private LocationRepository locationRepository;
  @Mock private DeviceAssignmentRepository assignmentRepository;
  @Mock private SoftwareRepository softwareRepository;
  @Mock private DeviceAttachmentRepository attachmentRepository;
  @Mock private RoleRepository roleRepository;

  @InjectMocks private EntityTypeRegistry registry;

  @BeforeEach
  void setUp() {
    registry.initFinders();
  }

  @Test
  void findById_allTypes() {
    Device dev = Device.builder().id(1L).build();
    when(deviceRepository.findById(1L)).thenReturn(Optional.of(dev));
    assertThat(registry.findById("Device", 1L)).isEqualTo(dev);

    AppUser user = AppUser.builder().id(2L).build();
    when(userRepository.findById(2L)).thenReturn(Optional.of(user));
    assertThat(registry.findById("User", 2L)).isEqualTo(user);
    assertThat(registry.findById("AppUser", 2L)).isEqualTo(user);

    Location loc = Location.builder().name("Loc").build();
    loc.setId(3L);
    when(locationRepository.findById(3L)).thenReturn(Optional.of(loc));
    assertThat(registry.findById("Location", 3L)).isEqualTo(loc);

    DeviceAssignment assign = DeviceAssignment.builder().id(4L).build();
    when(assignmentRepository.findById(4L)).thenReturn(Optional.of(assign));
    assertThat(registry.findById("Assignment", 4L)).isEqualTo(assign);
    assertThat(registry.findById("DeviceAssignment", 4L)).isEqualTo(assign);

    Software sw = Software.builder().id(5L).build();
    when(softwareRepository.findById(5L)).thenReturn(Optional.of(sw));
    assertThat(registry.findById("Software", 5L)).isEqualTo(sw);

    DeviceAttachment att = DeviceAttachment.builder().id(6L).build();
    when(attachmentRepository.findById(6L)).thenReturn(Optional.of(att));
    assertThat(registry.findById("Attachment", 6L)).isEqualTo(att);

    Role role = Role.builder().id(7L).name("ROLE_ADMIN").build();
    when(roleRepository.findByIdWithPermissions(7L)).thenReturn(Optional.of(role));
    assertThat(registry.findById("Role", 7L)).isEqualTo(role);
  }

  @Test
  void findById_unknownTypeThrows() {
    assertThatThrownBy(() -> registry.findById("Unknown", 1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void deleteById_allTypes() {
    registry.deleteById("Device", 1L);
    verify(deviceRepository).deleteById(1L);

    registry.deleteById("User", 2L);
    verify(userRepository).deleteById(2L);

    registry.deleteById("Location", 3L);
    verify(locationRepository).deleteById(3L);

    registry.deleteById("Assignment", 4L);
    verify(assignmentRepository).deleteById(4L);

    registry.deleteById("Software", 5L);
    verify(softwareRepository).deleteById(5L);

    registry.deleteById("Attachment", 6L);
    verify(attachmentRepository).deleteById(6L);

    registry.deleteById("Role", 7L);
    verify(roleRepository).deleteById(7L);
  }

  @Test
  void deleteById_unknownTypeThrows() {
    assertThatThrownBy(() -> registry.deleteById("Unknown", 1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void saveEntity_allTypes() {
    Device dev = Device.builder().id(1L).build();
    registry.saveEntity(dev);
    verify(deviceRepository).save(dev);

    AppUser user = AppUser.builder().id(2L).build();
    registry.saveEntity(user);
    verify(userRepository).save(user);

    Location loc = Location.builder().name("Loc").build();
    loc.setId(3L);
    registry.saveEntity(loc);
    verify(locationRepository).save(loc);

    DeviceAssignment assign = DeviceAssignment.builder().id(4L).build();
    registry.saveEntity(assign);
    verify(assignmentRepository).save(assign);

    Software sw = Software.builder().id(5L).build();
    registry.saveEntity(sw);
    verify(softwareRepository).save(sw);

    DeviceAttachment att = DeviceAttachment.builder().id(6L).build();
    registry.saveEntity(att);
    verify(attachmentRepository).save(att);

    Role role = Role.builder().id(7L).build();
    registry.saveEntity(role);
    verify(roleRepository).save(role);
  }

  @Test
  void toJsonMap_and_applyJsonMap_Device() {
    Device dev =
        Device.builder()
            .id(1L)
            .type("Laptop")
            .inventoryNumber("INV-001")
            .status(DeviceStatus.IN_STORAGE)
            .build();

    Map<String, Object> map = registry.toJsonMap(dev);
    assertThat(map.get("inventoryNumber")).isEqualTo("INV-001");

    registry.applyJsonMap(dev, Map.of("type", "Desktop", "status", "MAINTENANCE"));
    assertThat(dev.getType()).isEqualTo("Desktop");
    assertThat(dev.getStatus()).isEqualTo(DeviceStatus.MAINTENANCE);
  }

  @Test
  void toJsonMap_and_applyJsonMap_User() {
    Role role =
        Role.builder()
            .id(1L)
            .name("ROLE_TEACHER")
            .permissions(Set.of(Permission.builder().name("DEVICE_READ").build()))
            .build();
    AppUser user =
        AppUser.builder()
            .id(10L)
            .emailHash("hash123")
            .emailEncrypted("enc123")
            .role(role)
            .active(true)
            .mustChangePassword(false)
            .build();

    Map<String, Object> map = registry.toJsonMap(user);
    assertThat(map.get("emailHash")).isEqualTo("hash123");

    registry.applyJsonMap(user, Map.of("active", false, "mustChangePassword", true));
    assertThat(user.isActive()).isFalse();
    assertThat(user.isMustChangePassword()).isTrue();
  }

  @Test
  void toJsonMap_and_applyJsonMap_Location() {
    Location parent = Location.builder().name("Building").build();
    parent.setId(100L);
    Location loc =
        Location.builder().name("Room 1").type(LocationType.OFFICE).parent(parent).build();
    loc.setId(200L);

    Map<String, Object> map = registry.toJsonMap(loc);
    assertThat(map.get("name")).isEqualTo("Room 1");

    when(locationRepository.findById(100L)).thenReturn(Optional.of(parent));
    registry.applyJsonMap(loc, Map.of("name", "Room 2", "type", "CLASSROOM", "parentId", 100L));
    assertThat(loc.getName()).isEqualTo("Room 2");
    assertThat(loc.getType()).isEqualTo(LocationType.CLASSROOM);
  }

  @Test
  void toJsonMap_and_applyJsonMap_OtherEntities() {
    DeviceAssignment assign =
        DeviceAssignment.builder().id(1L).status(AssignmentStatus.PENDING_ASSIGNMENT).build();
    Map<String, Object> assignMap = registry.toJsonMap(assign);
    assertThat(assignMap).containsKey("id");
    registry.applyJsonMap(assign, Map.of("status", "ASSIGNED"));
    assertThat(assign.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);

    Software sw = Software.builder().id(2L).name("Photoshop").build();
    Map<String, Object> swMap = registry.toJsonMap(sw);
    assertThat(swMap.get("name")).isEqualTo("Photoshop");
    registry.applyJsonMap(sw, Map.of("name", "GIMP"));
    assertThat(sw.getName()).isEqualTo("GIMP");

    DeviceAttachment att =
        DeviceAttachment.builder()
            .id(3L)
            .fileName("manual.pdf")
            .mimeType("application/pdf")
            .sizeBytes(1024L)
            .build();
    Map<String, Object> attMap = registry.toJsonMap(att);
    assertThat(attMap.get("fileName")).isEqualTo("manual.pdf");
    registry.applyJsonMap(att, Map.of("fileName", "new_manual.pdf", "sizeBytes", 2048L));
    assertThat(att.getFileName()).isEqualTo("new_manual.pdf");
    assertThat(att.getSizeBytes()).isEqualTo(2048L);

    Role role = Role.builder().id(4L).name("ROLE_VIEWER").permissions(Set.of()).build();
    Map<String, Object> roleMap = registry.toJsonMap(role);
    assertThat(roleMap.get("name")).isEqualTo("ROLE_VIEWER");
    registry.applyJsonMap(role, Map.of("name", "ROLE_EDITOR"));
    assertThat(role.getName()).isEqualTo("ROLE_EDITOR");
  }

  @Test
  void recreateEntity_Device() {
    when(deviceRepository.findByInventoryNumber("INV-999")).thenReturn(Optional.empty());
    when(deviceRepository.save(any(Device.class))).thenAnswer(i -> i.getArgument(0));

    Map<String, Object> fields =
        Map.of(
            "type", "Monitor",
            "inventoryNumber", "INV-999",
            "status", "DISPOSED");

    Object result = registry.recreateEntity("Device", 10L, fields);

    assertThat(result).isInstanceOf(Device.class);
    Device dev = (Device) result;
    assertThat(dev.getType()).isEqualTo("Monitor");
    assertThat(dev.getInventoryNumber()).isEqualTo("INV-999");
    assertThat(dev.getStatus()).isEqualTo(DeviceStatus.DISPOSED);
  }

  @Test
  void recreateEntity_Location_Software_Role_User() {
    when(locationRepository.save(any(Location.class))).thenAnswer(i -> i.getArgument(0));
    when(softwareRepository.save(any(Software.class))).thenAnswer(i -> i.getArgument(0));
    when(roleRepository.save(any(Role.class))).thenAnswer(i -> i.getArgument(0));
    when(userRepository.findByEmailHash(any())).thenReturn(Optional.empty());
    when(userRepository.save(any(AppUser.class))).thenAnswer(i -> i.getArgument(0));

    Object loc =
        registry.recreateEntity("Location", 1L, Map.of("name", "Room 404", "type", "CLASSROOM"));
    assertThat(loc).isInstanceOf(Location.class);

    Object sw = registry.recreateEntity("Software", 2L, Map.of("name", "IntelliJ"));
    assertThat(sw).isInstanceOf(Software.class);

    Object role = registry.recreateEntity("Role", 3L, Map.of("name", "ROLE_DEV"));
    assertThat(role).isInstanceOf(Role.class);

    Object user =
        registry.recreateEntity(
            "User", 4L, Map.of("email", "test@tanszek.local", "emailHash", "h123"));
    assertThat(user).isInstanceOf(AppUser.class);
  }
}
