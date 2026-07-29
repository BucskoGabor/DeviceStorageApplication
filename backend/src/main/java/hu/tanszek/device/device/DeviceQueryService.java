package hu.tanszek.device.device;

import hu.tanszek.device.device.entity.Device;
import hu.tanszek.device.device.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * DeviceQueryService — Device entity olvasási műveletek JpaSpecificationExecutor-ral.
 *
 * <p>A row-level filter (Task 3.2 scope) itt történik a JpaSpecificationExecutor
 * Specification-ön keresztül. A service-szintű assertion (write műveletekre,
 * Task 3.3 scope) a {@link DeviceService}-ben lesz implementálva.
 *
 * <p>A Specification a SecurityContext-ből olvassa a bejelentkezett user
 * role-ját, és alkalmazza a row-level filtert:
 * <ul>
 *   <li>ADMIN: minden device</li>
 *   <li>TEACHER: saját device-ok (active assignment to currentUser)
 *       + irodai device-ok (location_id = currentUser.office_location_id)</li>
 *   <li>STUDENT: csak saját device-ok (active assignment to currentUser)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DeviceQueryService {

    private final DeviceRepository deviceRepository;

    /**
     * Device lista lekérdezése row-level filterrel.
     *
     * <p>A filter a SecurityContext-ből olvassa a currentUser-t, és a role
     * alapján szűr. A pontos SQL implementáció a Specification osztályban
     * van (lásd {@link DeviceSpecifications}).
     *
     * @param currentUserId a bejelentkezett user ID-ja
     * @return a row-level filter által engedélyezett device-ok listája
     */
    @Transactional(readOnly = true)
    public List<Device> findAllForCurrentUser(Long currentUserId) {
        // TODO Task 3.3: implementálni a pontos Specification-t a role-alapú szűréshez
        // Most egy placeholder Specification-t használunk (minden device visszaadása,
        // ha a currentUserId null, ami az ADMIN szerepkörre utal)
        if (currentUserId == null) {
            return deviceRepository.findAll();
        }

        // TODO Task 3.3: helyettesíteni a tényleges role-alapú Specification-gal
        Specification<Device> spec = DeviceSpecifications.hasAccess(currentUserId);
        return deviceRepository.findAll(spec);
    }
}