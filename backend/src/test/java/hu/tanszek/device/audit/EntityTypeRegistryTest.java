package hu.tanszek.device.audit;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import hu.tanszek.device.assignment.entity.AssignmentStatus;
import hu.tanszek.device.assignment.entity.DeviceAssignment;
import hu.tanszek.device.assignment.repository.DeviceAssignmentRepository;
import hu.tanszek.device.attachment.entity.DeviceAttachment;
import hu.tanszek.device.attachment.repository.DeviceAttachmentRepository;
import hu.tanszek.device.auth.entity.Permission;
import hu.tanszek.device.auth.entity.Role;
import hu.tanszek.device.auth.repository.PermissionRepository;
import hu.tanszek.device.auth.repository.RoleRepository;
import hu.tanszek.device.crypto.CryptoService;
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
  @Mock private PermissionRepository permissionRepository;
  @Mock private CryptoService cryptoService;
  @Mock private Argon2PasswordEncoder passwordEncoder;
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
    Location loc = Location.builder().name("Raktar A").type(LocationType.STORAGE).build();
    loc.setId(15L);

    Device dev =
        Device.builder()
            .id(1L)
            .type("Laptop")
            .inventoryNumber("INV-001")
            .status(DeviceStatus.IN_STORAGE)
            .currentLocation(loc)
            .build();

    Map<String, Object> map = registry.toJsonMap(dev);
    assertThat(map.get("inventoryNumber")).isEqualTo("INV-001");
    assertThat(map.get("currentLocationId")).isEqualTo(15L);

    Location newLoc = Location.builder().name("Raktar B").type(LocationType.STORAGE).build();
    newLoc.setId(25L);
    when(locationRepository.findById(25L)).thenReturn(Optional.of(newLoc));

    registry.applyJsonMap(
        dev, Map.of("type", "Desktop", "status", "MAINTENANCE", "currentLocationId", 25L));
    assertThat(dev.getType()).isEqualTo("Desktop");
    assertThat(dev.getStatus()).isEqualTo(DeviceStatus.MAINTENANCE);
    assertThat(dev.getCurrentLocation().getId()).isEqualTo(25L);

    // Test explicit null clears currentLocation
    Map<String, Object> clearLocMap = new java.util.HashMap<>();
    clearLocMap.put("currentLocationId", null);
    registry.applyJsonMap(dev, clearLocMap);
    assertThat(dev.getCurrentLocation()).isNull();
  }

  @Test
  void toJsonMap_and_applyJsonMap_User() {
    Role role =
        Role.builder()
            .id(1L)
            .name("ROLE_TEACHER")
            .permissions(Set.of(Permission.builder().name("DEVICE_READ").build()))
            .build();
    Location office = Location.builder().name("Iroda 101").type(LocationType.OFFICE).build();
    office.setId(30L);
    Permission directPerm = Permission.builder().id(99L).name("EXPORT_DATA").build();

    AppUser user =
        AppUser.builder()
            .id(10L)
            .emailHash("hash123")
            .emailEncrypted("enc123")
            .role(role)
            .officeLocation(office)
            .permissions(new java.util.HashSet<>(Set.of(directPerm)))
            .active(true)
            .mustChangePassword(false)
            .build();

    Map<String, Object> map = registry.toJsonMap(user);
    assertThat(map.get("emailHash")).isEqualTo("hash123");
    assertThat(map.get("officeLocationId")).isEqualTo(30L);
    assertThat(map.get("permissions")).isEqualTo(java.util.List.of("EXPORT_DATA"));

    Location newOffice = Location.builder().name("Iroda 102").type(LocationType.OFFICE).build();
    newOffice.setId(35L);
    when(locationRepository.findById(35L)).thenReturn(Optional.of(newOffice));

    Permission newDirectPerm = Permission.builder().id(100L).name("IMPORT_DATA").build();
    when(permissionRepository.findByName("IMPORT_DATA")).thenReturn(Optional.of(newDirectPerm));

    registry.applyJsonMap(
        user,
        Map.of(
            "active",
            false,
            "mustChangePassword",
            true,
            "officeLocationId",
            35L,
            "permissions",
            java.util.List.of("IMPORT_DATA")));
    assertThat(user.isActive()).isFalse();
    assertThat(user.isMustChangePassword()).isTrue();
    assertThat(user.getOfficeLocation().getId()).isEqualTo(35L);
    assertThat(user.getPermissions()).extracting("name").containsExactly("IMPORT_DATA");

    // Test explicit null clears officeLocation and permissions
    Map<String, Object> clearMap = new java.util.HashMap<>();
    clearMap.put("officeLocationId", null);
    clearMap.put("permissions", null);
    registry.applyJsonMap(user, clearMap);
    assertThat(user.getOfficeLocation()).isNull();
    assertThat(user.getPermissions()).isEmpty();
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
  void applyJsonMap_User_restoresRoleId() {
    // A user jelenleg ROLE_STUDENT (a rollback UTÁN: tanár → diák váltás).
    // A beforeState a ROLE_TEACHER roleId-t tartalmazza.
    Role teacherRole = Role.builder().id(5L).name("ROLE_TEACHER").build();
    Role studentRole = Role.builder().id(6L).name("ROLE_STUDENT").build();
    AppUser user = AppUser.builder().id(20L).emailHash("h").role(studentRole).build();

    when(roleRepository.findById(5L)).thenReturn(Optional.of(teacherRole));

    registry.applyJsonMap(user, Map.of("roleId", 5));

    // A user role-ját vissza kellett állítani tanárra.
    assertThat(user.getRole().getId()).isEqualTo(5L);
    assertThat(user.getRole().getName()).isEqualTo("ROLE_TEACHER");
  }

  @Test
  void applyJsonMap_User_clearsRoleWhenRoleIdIsNull() {
    // Ha a beforeState.roleId = null, a user role-ja törlődik.
    Role oldRole = Role.builder().id(7L).name("ROLE_X").build();
    AppUser user = AppUser.builder().id(21L).emailHash("h").role(oldRole).build();

    Map<String, Object> before = new java.util.HashMap<>();
    before.put("roleId", null);

    registry.applyJsonMap(user, before);

    assertThat(user.getRole()).isNull();
  }

  @Test
  void applyJsonMap_DeviceAssignment_restoresReferences() {
    // A DeviceAssignment jelenleg egy másik device-hoz van rendelve (a rollback
    // UTÁN: a device megváltozott). A beforeState az eredeti device/location/user
    // ID-kat tartalmazza.
    Device origDevice = Device.builder().id(50L).type("laptop").build();
    Location origFromLoc = Location.builder().id(60L).name("From").build();
    Location origToLoc = Location.builder().id(61L).name("To").build();
    AppUser origFromUser = AppUser.builder().id(70L).emailHash("x").build();
    AppUser origToUser = AppUser.builder().id(71L).emailHash("y").build();

    Device currentDevice = Device.builder().id(99L).type("monitor").build();
    Location currentToLoc = Location.builder().id(199L).name("Current").build();

    DeviceAssignment assign =
        DeviceAssignment.builder()
            .id(1L)
            .device(currentDevice)
            .toLocation(currentToLoc)
            .status(AssignmentStatus.ASSIGNED)
            .build();

    when(deviceRepository.findById(50L)).thenReturn(Optional.of(origDevice));
    when(locationRepository.findById(60L)).thenReturn(Optional.of(origFromLoc));
    when(locationRepository.findById(61L)).thenReturn(Optional.of(origToLoc));
    when(userRepository.findById(70L)).thenReturn(Optional.of(origFromUser));
    when(userRepository.findById(71L)).thenReturn(Optional.of(origToUser));

    Map<String, Object> before =
        Map.of(
            "deviceId", 50,
            "fromLocationId", 60,
            "toLocationId", 61,
            "fromUserId", 70,
            "toUserId", 71);

    registry.applyJsonMap(assign, before);

    // Minden referenciát vissza kellett állítani.
    assertThat(assign.getDevice().getId()).isEqualTo(50L);
    assertThat(assign.getFromLocation().getId()).isEqualTo(60L);
    assertThat(assign.getToLocation().getId()).isEqualTo(61L);
    assertThat(assign.getFromUser().getId()).isEqualTo(70L);
    assertThat(assign.getToUser().getId()).isEqualTo(71L);
  }

  @Test
  void applyJsonMap_Role_restoresPermissions() {
    // A role jelenleg 1 permissionnel rendelkezik (a rollback UTÁN: a DEVICE_ASSIGN
    // törölve lett). A beforeState a teljes permission listát tartalmazza.
    Permission readPerm = Permission.builder().id(101L).name("DEVICE_READ").build();
    Permission writePerm = Permission.builder().id(102L).name("DEVICE_ASSIGN").build();

    Role role = Role.builder().id(4L).name("ROLE_VIEWER").build();
    role.getPermissions().add(readPerm); // jelenlegi state: csak DEVICE_READ

    Map<String, Object> before =
        Map.of(
            "name",
            "ROLE_VIEWER",
            "permissions",
            java.util.List.of("DEVICE_READ", "DEVICE_ASSIGN"));

    when(permissionRepository.findByName("DEVICE_READ")).thenReturn(Optional.of(readPerm));
    when(permissionRepository.findByName("DEVICE_ASSIGN")).thenReturn(Optional.of(writePerm));

    registry.applyJsonMap(role, before);

    // A role-nak most mindkét permissiont vissza kellett kapnia.
    assertThat(role.getPermissions())
        .extracting("name")
        .containsExactlyInAnyOrder("DEVICE_READ", "DEVICE_ASSIGN");
  }

  @Test
  void applyJsonMap_Role_clearsPermissionsWhenBeforeHasNull() {
    // Ha a beforeState.permissions = null, a rollback kiüríti a role permissionjeit.
    Role role = Role.builder().id(5L).name("ROLE_EMPTY").build();
    role.getPermissions().add(Permission.builder().id(1L).name("X").build());

    Map<String, Object> before = new java.util.HashMap<>();
    before.put("name", "ROLE_EMPTY");
    before.put("permissions", null);

    registry.applyJsonMap(role, before);

    assertThat(role.getPermissions()).isEmpty();
  }

  @Test
  void recreateEntity_Device() {
    when(deviceRepository.findByInventoryNumber("INV-999")).thenReturn(Optional.empty());
    when(deviceRepository.save(any(Device.class))).thenAnswer(i -> i.getArgument(0));

    Location loc = Location.builder().name("Storage").build();
    loc.setId(55L);
    when(locationRepository.findById(55L)).thenReturn(Optional.of(loc));

    Map<String, Object> fields =
        Map.of(
            "type", "Monitor",
            "inventoryNumber", "INV-999",
            "status", "DISPOSED",
            "currentLocationId", 55L);

    Object result = registry.recreateEntity("Device", 10L, fields);

    assertThat(result).isInstanceOf(Device.class);
    Device dev = (Device) result;
    assertThat(dev.getType()).isEqualTo("Monitor");
    assertThat(dev.getInventoryNumber()).isEqualTo("INV-999");
    assertThat(dev.getStatus()).isEqualTo(DeviceStatus.DISPOSED);
    assertThat(dev.getCurrentLocation().getId()).isEqualTo(55L);
  }

  @Test
  void recreateEntity_Location_Software_Role_User() {
    when(locationRepository.save(any(Location.class))).thenAnswer(i -> i.getArgument(0));
    when(softwareRepository.save(any(Software.class))).thenAnswer(i -> i.getArgument(0));
    when(roleRepository.save(any(Role.class))).thenAnswer(i -> i.getArgument(0));
    when(userRepository.findByEmailHash(any())).thenReturn(Optional.empty());
    when(userRepository.save(any(AppUser.class))).thenAnswer(i -> i.getArgument(0));
    when(cryptoService.encrypt(any())).thenAnswer(i -> "enc:" + i.getArgument(0));
    when(passwordEncoder.encode(any())).thenReturn("hashed-pwd");

    Object loc =
        registry.recreateEntity("Location", 1L, Map.of("name", "Room 404", "type", "CLASSROOM"));
    assertThat(loc).isInstanceOf(Location.class);

    Object sw =
        registry.recreateEntity("Software", 2L, Map.of("name", "IntelliJ", "licenseKey", "KEY123"));
    assertThat(sw).isInstanceOf(Software.class);
    assertThat(((Software) sw).getLicenseKeyEncrypted()).isEqualTo("enc:KEY123");

    Object role = registry.recreateEntity("Role", 3L, Map.of("name", "ROLE_DEV"));
    assertThat(role).isInstanceOf(Role.class);

    Object user =
        registry.recreateEntity(
            "User", 4L, Map.of("email", "test@tanszek.local", "emailHash", "h123"));
    assertThat(user).isInstanceOf(AppUser.class);
    AppUser appUser = (AppUser) user;
    assertThat(appUser.getEmailEncrypted()).isEqualTo("enc:test@tanszek.local");
    assertThat(appUser.getPasswordHash()).isEqualTo("hashed-pwd");
    assertThat(appUser.getPasswordChangedAt()).isNotNull();
  }

  @Test
  void recreateEntity_unknownTypeThrows() {
    assertThatThrownBy(() -> registry.recreateEntity("Alien", 99L, Map.of("foo", "bar")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Alien");
  }

  @Test
  void recreateEntity_nullFieldsThrows() {
    assertThatThrownBy(() -> registry.recreateEntity("Device", 1L, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void recreateEntity_Device_duplicateInventoryNumberThrows() {
    Device existing = Device.builder().inventoryNumber("INV-DUP").build();
    existing.setId(7L);
    when(deviceRepository.findByInventoryNumber("INV-DUP")).thenReturn(Optional.of(existing));

    assertThatThrownBy(
            () -> registry.recreateEntity("Device", 10L, Map.of("inventoryNumber", "INV-DUP")))
        .isInstanceOf(hu.tanszek.device.common.BusinessValidationException.class)
        .hasMessageContaining("INV-DUP");
  }

  @Test
  void recreateEntity_Device_fallsBackToUnknownTypeWhenMissingOrMasked() {

    when(deviceRepository.save(any(Device.class))).thenAnswer(i -> i.getArgument(0));

    Device dev = (Device) registry.recreateEntity("Device", 1L, Map.of("type", "***"));
    assertThat(dev.getType()).isEqualTo("Unknown");
    assertThat(dev.getInventoryNumber()).startsWith("INV-RESTORED-");
  }

  @Test
  void recreateEntity_Device_handlesMissingLocationId() {

    when(deviceRepository.save(any(Device.class))).thenAnswer(i -> i.getArgument(0));

    Device dev = (Device) registry.recreateEntity("Device", 1L, Map.of("type", "Laptop"));
  }

  @Test
  void recreateEntity_Software_usesRestoredKeyWhenLicenseMissingOrMasked() {
    when(softwareRepository.save(any(Software.class))).thenAnswer(i -> i.getArgument(0));
    when(cryptoService.encrypt(any())).thenAnswer(i -> "enc:" + i.getArgument(0));

    Software sw =
        (Software)
            registry.recreateEntity("Software", 5L, Map.of("name", "X", "licenseKey", "***"));

    assertThat(sw.getLicenseKeyEncrypted()).startsWith("enc:RESTORED-KEY-");
  }

  @Test
  void recreateEntity_Role_restoresPermissions() {
    Permission p1 = new Permission();
    p1.setId(1L);
    p1.setName("DEVICE_READ");
    when(permissionRepository.findByName("DEVICE_READ")).thenReturn(Optional.of(p1));
    when(roleRepository.save(any(Role.class))).thenAnswer(i -> i.getArgument(0));

    Role role =
        (Role)
            registry.recreateEntity(
                "Role", 1L, Map.of("name", "ROLE_TEST", "permissions", List.of("DEVICE_READ")));

    assertThat(role.getPermissions()).hasSize(1);
  }

  @Test
  void recreateEntity_User_restoresRoleAndOfficeLocation() {
    Role r = new Role();
    r.setId(5L);
    Location office = Location.builder().name("Office").build();
    office.setId(11L);
    Permission p1 = new Permission();
    p1.setId(1L);
    p1.setName("DEVICE_READ");

    when(userRepository.save(any(AppUser.class))).thenAnswer(i -> i.getArgument(0));
    when(roleRepository.findById(5L)).thenReturn(Optional.of(r));
    when(locationRepository.findById(11L)).thenReturn(Optional.of(office));
    when(permissionRepository.findByName("DEVICE_READ")).thenReturn(Optional.of(p1));
    when(cryptoService.encrypt(any())).thenAnswer(i -> "enc:" + i.getArgument(0));
    when(cryptoService.sha256(any())).thenAnswer(i -> "hashed:" + i.getArgument(0));
    when(passwordEncoder.encode(any())).thenReturn("hashed-pwd");

    AppUser user =
        (AppUser)
            registry.recreateEntity(
                "User",
                9L,
                Map.of(
                    "email",
                    "u@tanszek.local",
                    "roleId",
                    5L,
                    "officeLocationId",
                    11L,
                    "permissions",
                    List.of("DEVICE_READ")));

    assertThat(user.getRole()).isEqualTo(r);
    assertThat(user.getOfficeLocation()).isEqualTo(office);
    assertThat(user.getPermissions()).hasSize(1);
  }

  @Test
  void recreateEntity_User_duplicateEmailThrows() {
    AppUser existing = new AppUser();
    existing.setId(7L);
    when(userRepository.findByEmailHash("dup")).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> registry.recreateEntity("User", 1L, Map.of("emailHash", "dup")))
        .isInstanceOf(hu.tanszek.device.common.BusinessValidationException.class);
  }

  @Test
  void recreateEntity_Device_fallsBackToInvRestoredWhenMasked() {
    when(deviceRepository.save(any(Device.class))).thenAnswer(i -> i.getArgument(0));

    Device dev =
        (Device)
            registry.recreateEntity(
                "Device", 1L, Map.of("type", "Laptop", "inventoryNumber", "***"));

    assertThat(dev.getInventoryNumber()).startsWith("INV-RESTORED-");
  }

  @Test
  void recreateEntity_Location_handlesMissingType() {
    when(locationRepository.save(any(Location.class))).thenAnswer(i -> i.getArgument(0));

    Location loc = (Location) registry.recreateEntity("Location", 1L, Map.of("name", "AutoName"));

    assertThat(loc.getType()).isEqualTo(LocationType.CLASSROOM);
  }
}
