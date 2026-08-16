package hu.tanszek.device.import_;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import hu.tanszek.device.auth.entity.Role;
import hu.tanszek.device.auth.repository.RoleRepository;
import hu.tanszek.device.crypto.CryptoService;
import hu.tanszek.device.device.entity.Device;
import hu.tanszek.device.device.repository.DeviceRepository;
import hu.tanszek.device.import_.dto.ImportDeviceRow;
import hu.tanszek.device.import_.dto.ImportPreviewResponse;
import hu.tanszek.device.import_.dto.ImportResult;
import hu.tanszek.device.import_.dto.ImportUserRow;
import hu.tanszek.device.location.entity.Location;
import hu.tanszek.device.location.entity.LocationType;
import hu.tanszek.device.location.repository.LocationRepository;
import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

@ExtendWith(MockitoExtension.class)
class ImportServiceTest {

  @Spy private Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
  @Mock private AppUserRepository userRepository;
  @Mock private DeviceRepository deviceRepository;
  @Mock private LocationRepository locationRepository;
  @Mock private RoleRepository roleRepository;
  @Mock private CryptoService cryptoService;

  @Spy
  private Argon2PasswordEncoder passwordEncoder = new Argon2PasswordEncoder(16, 32, 1, 65536, 3);

  @InjectMocks private ImportService importService;

  private byte[] sampleExcelBytes;

  @BeforeEach
  void setUp() throws IOException {
    try (Workbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet userSheet = workbook.createSheet("Users");
      Row userHeader = userSheet.createRow(0);
      userHeader.createCell(0).setCellValue("email");
      userHeader.createCell(1).setCellValue("firstName");
      userHeader.createCell(2).setCellValue("lastName");
      userHeader.createCell(3).setCellValue("role");
      userHeader.createCell(4).setCellValue("active");
      userHeader.createCell(5).setCellValue("officeLocationName");

      Row userRow = userSheet.createRow(1);
      userRow.createCell(0).setCellValue("teacher@test.local");
      userRow.createCell(1).setCellValue("John");
      userRow.createCell(2).setCellValue("Doe");
      userRow.createCell(3).setCellValue("ROLE_TEACHER");
      userRow.createCell(4).setCellValue("true");
      userRow.createCell(5).setCellValue("Office 101");

      Sheet deviceSheet = workbook.createSheet("Devices");
      Row devHeader = deviceSheet.createRow(0);
      devHeader.createCell(0).setCellValue("inventoryNumber");
      devHeader.createCell(1).setCellValue("type");
      devHeader.createCell(2).setCellValue("status");
      devHeader.createCell(3).setCellValue("locationName");

      Row devRow = deviceSheet.createRow(1);
      devRow.createCell(0).setCellValue("INV-001");
      devRow.createCell(1).setCellValue("Laptop");
      devRow.createCell(2).setCellValue("IN_STORAGE");
      devRow.createCell(3).setCellValue("Storage 1");

      workbook.write(out);
      sampleExcelBytes = out.toByteArray();
    }
  }

  @Test
  void preview_parsesUsersAndDevices() {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "import.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            sampleExcelBytes);

    ImportPreviewResponse preview = importService.preview(file);

    assertThat(preview.validUsers()).hasSize(1);
    assertThat(preview.validUsers().get(0).email()).isEqualTo("teacher@test.local");
    assertThat(preview.validDevices()).hasSize(1);
    assertThat(preview.validDevices().get(0).inventoryNumber()).isEqualTo("INV-001");
  }

  @Test
  void execute_insertsAndUpdates() {
    ImportUserRow userRow =
        new ImportUserRow("new@test.local", "Jane", "Doe", "ROLE_TEACHER", true, "Office 101");
    ImportDeviceRow devRow = new ImportDeviceRow("INV-002", "Monitor", "IN_STORAGE", "Office 101");
    ImportPreviewResponse preview =
        new ImportPreviewResponse(2, List.of(userRow), List.of(devRow), List.of());

    when(cryptoService.sha256("new@test.local")).thenReturn("hash123");
    when(cryptoService.encrypt("new@test.local")).thenReturn("enc123");
    when(userRepository.findByEmailHash("hash123")).thenReturn(Optional.empty());

    Role role = Role.builder().id(1L).name("ROLE_TEACHER").build();
    when(roleRepository.findByName("ROLE_TEACHER")).thenReturn(Optional.of(role));

    Location office = Location.builder().name("Office 101").type(LocationType.OFFICE).build();
    office.setId(10L);
    when(locationRepository.findByType(LocationType.OFFICE)).thenReturn(List.of(office));

    when(deviceRepository.findByInventoryNumber("INV-002")).thenReturn(Optional.empty());

    ImportResult result = importService.execute(preview);

    assertThat(result.usersInserted()).isEqualTo(1);
    assertThat(result.devicesInserted()).isEqualTo(1);
    assertThat(result.errors()).isEqualTo(0);

    verify(userRepository).save(any(AppUser.class));
    verify(deviceRepository).save(any(Device.class));
  }
}
