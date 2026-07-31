package hu.tanszek.device.software;

import hu.tanszek.device.audit.AuditTarget;
import hu.tanszek.device.common.BusinessValidationException;
import hu.tanszek.device.common.ResourceNotFoundException;
import hu.tanszek.device.crypto.CryptoService;
import hu.tanszek.device.software.entity.Software;
import hu.tanszek.device.software.repository.SoftwareRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SoftwareService — szoftver CRUD + licence key kezelés.
 *
 * <p>A service-szintű metódusok {@code @AuditTarget} annotációval vannak
 * ellátva, így az AOP audit interceptor (lásd {@code AuditAspect}) minden
 * módosítást automatikusan naplóz az {@code audit_logs} táblába.
 *
 * <p>Licence key kezelés:
 * <ul>
 *   <li>{@link #create(String, String)} — új szoftver létrehozása titkosított kulccsal</li>
 *   <li>{@link #update(Long, String, String)} — partial update (csak a nem-null mezők)</li>
 *   <li>{@link #delete(Long)} — szoftver törlése (a join tábla bejegyzések manuálisan tisztítandók a DB cascade által)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwareService {

    private final SoftwareRepository softwareRepository;
    private final CryptoService cryptoService;

    /**
     * Új szoftver létrehozása. A licence key titkosítva kerül DB-be.
     */
    @AuditTarget(entityType = "Software", action = "create")
    @Transactional
    public Software create(String name, String licenseKey) {
        validateName(name);
        Software software = Software.builder()
                .name(name)
                .licenseKeyEncrypted(cryptoService.encrypt(licenseKey))
                .build();
        Software saved = softwareRepository.save(software);
        log.info("Software created: id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    /**
     * Szoftver módosítása — partial update. Csak a nem-null mezők frissülnek.
     *
     * @param id a szoftver azonosítója
     * @param name új név (null = nem változik)
     * @param licenseKey új licence key (null = nem változik); ha nem null, újra titkosítjuk
     */
    @AuditTarget(entityType = "Software", action = "update")
    @Transactional
    public Software update(Long id, String name, String licenseKey) {
        Software software = softwareRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Software not found: " + id));

        if (name != null) {
            validateName(name);
            software.setName(name);
        }
        if (licenseKey != null && !licenseKey.isBlank()) {
            software.setLicenseKeyEncrypted(cryptoService.encrypt(licenseKey));
        }

        Software saved = softwareRepository.save(software);
        log.info("Software updated: id={}", saved.getId());
        return saved;
    }

    /**
     * Szoftver törlése.
     */
    @AuditTarget(entityType = "Software", action = "delete")
    @Transactional
    public void delete(Long id) {
        if (!softwareRepository.existsById(id)) {
            throw new ResourceNotFoundException("Software not found: " + id);
        }
        softwareRepository.deleteById(id);
        log.info("Software deleted: id={}", id);
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessValidationException(
                    "validation.nameNotBlank",
                    "Software name cannot be blank"
            );
        }
    }
}
