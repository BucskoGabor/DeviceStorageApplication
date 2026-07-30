package hu.tanszek.device.location;

import hu.tanszek.device.common.BusinessValidationException;
import hu.tanszek.device.location.entity.Location;
import hu.tanszek.device.location.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * LocationService — hierarchikus location műveletek.
 *
 * <p>Legfontosabb feladata: a {@link #validateNoCycle(Long, Long)} metódus,
 * ami rekurzívan végigmegy a parent láncon, és dob, ha ciklust talál.
 */
@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;

    /**
     * Rekurzív ciklusellenőrzés.
     *
     * <p>Ha egy location parentId-jét (vagy saját ID-jét) át akarjuk állítani,
     * ellenőrizzük, hogy a parent lánc nem vezet vissza a saját location-höz.
     *
     * <p>Algoritmus:
     * <ol>
     *   <li>Ha a parentId == locationId, ciklus (location lenne a saját szülője)</li>
     *   <li>Követjük a parent láncot felfelé, amíg parent == null vagy parent != locationId</li>
     *   <li>Ha parent == locationId, ciklus van</li>
     *   <li>Ha parent lánc túl mély (védelem), break</li>
     * </ol>
     *
     * @param locationId az átállítandó location ID (null = új location)
     * @param newParentId az új parent ID (null = root)
     * @throws BusinessValidationException ha ciklus van
     */
    @Transactional(readOnly = true)
    public void validateNoCycle(Long locationId, Long newParentId) {
        if (locationId == null || newParentId == null) {
            return; // Új location vagy root — nincs ciklus
        }

        if (locationId.equals(newParentId)) {
            throw new BusinessValidationException(
                    "locationCycleDetected",
                    "Location cannot be its own parent"
            );
        }

        // Rekurzívan követjük a parent láncot a newParentId-től felfelé
        Long currentParentId = newParentId;
        List<Long> visited = new ArrayList<>();
        int maxDepth = 100;  // Védelem végtelen ciklus ellen

        while (currentParentId != null && maxDepth-- > 0) {
            if (visited.contains(currentParentId)) {
                // Már meglátogatott node — a DB szintű adat már ciklikus
                break;
            }
            visited.add(currentParentId);

            if (currentParentId.equals(locationId)) {
                throw new BusinessValidationException(
                        "locationCycleDetected",
                        "Cyclic parent reference detected: location " + locationId +
                                " is a descendant of " + newParentId
                );
            }

            Location parent = locationRepository.findById(currentParentId).orElse(null);
            if (parent == null) {
                break;
            }
            currentParentId = parent.getParent() != null ? parent.getParent().getId() : null;
        }
    }
}
