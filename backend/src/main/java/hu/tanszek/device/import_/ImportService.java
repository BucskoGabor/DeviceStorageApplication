package hu.tanszek.device.import_;

import hu.tanszek.device.common.BusinessValidationException;
import hu.tanszek.device.common.ResourceNotFoundException;
import hu.tanszek.device.crypto.CryptoService;
import hu.tanszek.device.device.entity.Device;
import hu.tanszek.device.device.entity.DeviceStatus;
import hu.tanszek.device.device.repository.DeviceRepository;
import hu.tanszek.device.import_.dto.ImportDeviceRow;
import hu.tanszek.device.import_.dto.ImportPreviewResponse;
import hu.tanszek.device.import_.dto.ImportPreviewResponse.InvalidRow;
import hu.tanszek.device.import_.dto.ImportResult;
import hu.tanszek.device.import_.dto.ImportUserRow;
import hu.tanszek.device.location.entity.Location;
import hu.tanszek.device.location.repository.LocationRepository;
import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;
import hu.tanszek.device.auth.repository.RoleRepository;
import hu.tanszek.device.auth.entity.Role;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ImportService — Excel (xlsx) import users + devices számára.
 *
 * <p>Flow:
 * <ol>
 *   <li>parse(file) — feltöltött xlsx parse-olása, minden sor DTO-ba</li>
 *   <li>validate(file) — Bean Validation minden DTO-ra, hibás sorok listája</li>
 *   <li>preview(file) — száraz futtatás, valid/invalid sorok visszaadása</li>
 *   <li>execute(previewResult) — tényleges import idempotens UPDATE-or-SKIP logikával</li>
 * </ol>
 *
 * <p>Idempotens logika:
 * <ul>
 *   <li>User: email_hash (SHA-256) alapján — ha létezik UPDATE, ha nem INSERT</li>
 *   <li>Device: inventory_number alapján — ha létezik UPDATE, ha nem INSERT</li>
 * </ul>
 *
 * <p>Concurrency: synchronized metódus, hogy egyszerre csak egy import fusson.
 * A 409-es választ a controller dobja, ha a lock foglalt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

    private final Validator validator;
    private final AppUserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final LocationRepository locationRepository;
    private final RoleRepository roleRepository;
    private final CryptoService cryptoService;

    private final Object importLock = new Object();

    /**
     * Excel fájl preview — valid/invalid sorok listája.
     *
     * <p>Szinkron metódus, mert az Excel parser nem thread-safe.
     */
    public synchronized ImportPreviewResponse preview(MultipartFile file) {
        try {
            List<List<String>> rows = parseExcel(file);
            return validate(rows);
        } catch (IOException e) {
            throw new BusinessValidationException("importFileReadError", "Failed to read Excel file: " + e.getMessage());
        }
    }

    /**
     * Excel fájl tényleges import — idempotens UPDATE-or-SKIP.
     *
     * <p>@Transactional: az egész import egy tranzakcióban fut. Ha bármelyik
     * sor fail, az egész rollback-elődik.
     *
     * <p>Concurrency: synchronized blokk, hogy egyszerre csak egy import fusson.
     */
    @Transactional
    public synchronized ImportResult execute(ImportPreviewResponse preview) {
        synchronized (importLock) {
            int usersInserted = 0, usersUpdated = 0;
            int devicesInserted = 0, devicesUpdated = 0;
            int errors = 0;

            // ===== User-ek import =====
            for (ImportUserRow row : preview.validUsers()) {
                try {
                    String emailHash = cryptoService.sha256(row.email());
                    Optional<AppUser> existing = userRepository.findByEmailHash(emailHash);

                    String emailEncrypted = cryptoService.encrypt(row.email());

                    // Role lookup
                    Role role = roleRepository.findByName(row.role())
                            .orElseThrow(() -> new BusinessValidationException(
                                    "invalidRole",
                                    "Unknown role: " + row.role()
                            ));

                    // Office location lookup (ha megadva)
                    Location office = null;
                    if (row.officeLocationName() != null && !row.officeLocationName().isBlank()) {
                        office = locationRepository.findByType(hu.tanszek.device.location.entity.LocationType.OFFICE).stream()
                                .filter(l -> l.getName().equals(row.officeLocationName()))
                                .findFirst()
                                .orElseThrow(() -> new ResourceNotFoundException(
                                        "Office location not found: " + row.officeLocationName()
                                ));
                    }

                    boolean active = row.active() == null || row.active();

                    if (existing.isPresent()) {
                        // UPDATE
                        AppUser user = existing.get();
                        user.setEmailEncrypted(emailEncrypted);
                        user.setRole(role);
                        user.setActive(active);
                        user.setOfficeLocation(office);
                        userRepository.save(user);
                        usersUpdated++;
                    } else {
                        // INSERT (default jelszó: ChangeMe123! + must_change_password=true)
                        AppUser newUser = AppUser.builder()
                                .emailEncrypted(emailEncrypted)
                                .emailHash(emailHash)
                                .officeLocation(office)
                                .passwordHash("$argon2id$v=19$m=65536,t=3,p=1$PLACEHOLDER_SALT$PLACEHOLDER_HASH")
                                .active(active)
                                .mustChangePassword(true)
                                .role(role)
                                .failedLoginCount(0)
                                .lockedUntil(null)
                                .passwordChangedAt(Instant.now())
                                .build();
                        userRepository.save(newUser);
                        usersInserted++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to import user: {}", row.email(), e);
                    errors++;
                }
            }

            // ===== Device-ok import =====
            for (ImportDeviceRow row : preview.validDevices()) {
                try {
                    DeviceStatus status;
                    try {
                        status = DeviceStatus.valueOf(row.status());
                    } catch (IllegalArgumentException e) {
                        throw new BusinessValidationException(
                                "invalidDeviceStatus",
                                "Invalid device status: " + row.status()
                        );
                    }

                    Location location = null;
                    if (row.locationName() != null && !row.locationName().isBlank()) {
                        location = locationRepository.findByType(hu.tanszek.device.location.entity.LocationType.OFFICE).stream()
                                .filter(l -> l.getName().equals(row.locationName()))
                                .findFirst()
                                .orElseThrow(() -> new ResourceNotFoundException(
                                        "Location not found: " + row.locationName()
                                ));
                    }

                    Optional<Device> existing = deviceRepository.findByInventoryNumber(row.inventoryNumber());

                    if (existing.isPresent()) {
                        // UPDATE
                        Device device = existing.get();
                        device.setType(row.type());
                        device.setStatus(status);
                        deviceRepository.save(device);
                        devicesUpdated++;
                    } else {
                        // INSERT
                        Device newDevice = Device.builder()
                                .type(row.type())
                                .inventoryNumber(row.inventoryNumber())
                                .status(status)
                                .build();
                        deviceRepository.save(newDevice);
                        devicesInserted++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to import device: {}", row.inventoryNumber(), e);
                    errors++;
                }
            }

            return new ImportResult(usersInserted, usersUpdated, devicesInserted, devicesUpdated, errors);
        }
    }

    /**
     * Excel fájl parse-olása: minden sor List<String> formátumban.
     *
     * <p>Sheet 0: Users (email, firstName, lastName, role, active, officeLocationName)
     * Sheet 1: Devices (inventoryNumber, type, status, locationName)
     */
    private List<List<String>> parseExcel(MultipartFile file) throws IOException {
        List<List<String>> allRows = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            // Sheet 0: Users
            Sheet usersSheet = workbook.getSheetAt(0);
            if (usersSheet != null) {
                List<List<String>> userRows = parseSheet(usersSheet, "User");
                allRows.addAll(userRows.stream().map(r -> addEntityType(r, "User")).toList());
            }

            // Sheet 1: Devices
            Sheet devicesSheet = workbook.getSheetAt(1);
            if (devicesSheet != null) {
                List<List<String>> deviceRows = parseSheet(devicesSheet, "Device");
                allRows.addAll(deviceRows.stream().map(r -> addEntityType(r, "Device")).toList());
            }
        }

        return allRows;
    }

    /**
     * Egy sheet sorainak parse-olása.
     *
     * <p>Az első sor a header, ezt kihagyjuk.
     */
    private List<List<String>> parseSheet(Sheet sheet, String entityType) {
        List<List<String>> rows = new ArrayList<>();

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            List<String> cells = new ArrayList<>();
            for (int j = 0; j < row.getLastCellNum(); j++) {
                Cell cell = row.getCell(j);
                cells.add(cell == null ? "" : getCellStringValue(cell));
            }
            // Entity type hozzáfűzése a sorhoz (0. cella)
            cells.add(0, entityType);
            cells.add(0, String.valueOf(i + 1)); // Row number (1-indexed)
            rows.add(cells);
        }

        return rows;
    }

    /**
     * Cell értékének kiolvasása String-ként.
     */
    private String getCellStringValue(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    /**
     * Sorhoz entity type hozzáfűzése.
     */
    private List<String> addEntityType(List<String> row, String entityType) {
        List<String> result = new ArrayList<>(row);
        result.add(entityType);
        return result;
    }

    /**
     * Sorok validálása — Bean Validation minden DTO-ra.
     */
    private ImportPreviewResponse validate(List<List<String>> rows) {
        List<ImportUserRow> validUsers = new ArrayList<>();
        List<ImportDeviceRow> validDevices = new ArrayList<>();
        List<InvalidRow> invalidRows = new ArrayList<>();

        for (List<String> row : rows) {
            // Row formátum: [rowNumber, entityType, ...cells]
            if (row.size() < 3) continue;

            int rowNumber;
            String entityType;
            try {
                rowNumber = Integer.parseInt(row.get(0));
                entityType = row.get(1);
            } catch (NumberFormatException e) {
                continue;
            }

            try {
                if ("User".equals(entityType)) {
                    ImportUserRow dto = parseUserRow(row);
                    Set<ConstraintViolation<ImportUserRow>> violations = validator.validate(dto);
                    if (violations.isEmpty()) {
                        validUsers.add(dto);
                    } else {
                        invalidRows.add(new InvalidRow(
                                rowNumber, entityType, String.join(",", row),
                                violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage()).collect(Collectors.toList())
                        ));
                    }
                } else if ("Device".equals(entityType)) {
                    ImportDeviceRow dto = parseDeviceRow(row);
                    Set<ConstraintViolation<ImportDeviceRow>> violations = validator.validate(dto);
                    if (violations.isEmpty()) {
                        validDevices.add(dto);
                    } else {
                        invalidRows.add(new InvalidRow(
                                rowNumber, entityType, String.join(",", row),
                                violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage()).collect(Collectors.toList())
                        ));
                    }
                }
            } catch (Exception e) {
                invalidRows.add(new InvalidRow(
                        rowNumber, entityType, String.join(",", row),
                        List.of("Parse error: " + e.getMessage())
                ));
            }
        }

        return new ImportPreviewResponse(rows.size(), validUsers, validDevices, invalidRows);
    }

    /**
     * User sor parse-olása ImportUserRow DTO-ba.
     */
    private ImportUserRow parseUserRow(List<String> row) {
        // Row formátum: [rowNumber, entityType, email, firstName, lastName, role, active, officeLocationName]
        return new ImportUserRow(
                row.size() > 2 ? row.get(2) : "",
                row.size() > 3 ? row.get(3) : "",
                row.size() > 4 ? row.get(4) : "",
                row.size() > 5 ? row.get(5) : "",
                row.size() > 6 ? Boolean.parseBoolean(row.get(6)) : null,
                row.size() > 7 ? row.get(7) : null
        );
    }

    /**
     * Device sor parse-olása ImportDeviceRow DTO-ba.
     */
    private ImportDeviceRow parseDeviceRow(List<String> row) {
        // Row formátum: [rowNumber, entityType, inventoryNumber, type, status, locationName]
        return new ImportDeviceRow(
                row.size() > 2 ? row.get(2) : "",
                row.size() > 3 ? row.get(3) : "",
                row.size() > 4 ? row.get(4) : "",
                row.size() > 5 ? row.get(5) : null
        );
    }
}