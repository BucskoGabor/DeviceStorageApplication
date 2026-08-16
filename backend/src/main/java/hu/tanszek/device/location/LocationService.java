package hu.tanszek.device.location;

import java.util.ArrayList;
import java.util.List;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hu.tanszek.device.common.BusinessValidationException;
import hu.tanszek.device.common.ResourceNotFoundException;
import hu.tanszek.device.location.dto.LocationTreeDto;
import hu.tanszek.device.location.entity.Location;
import hu.tanszek.device.location.repository.LocationRepository;

import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * LocationService — hierarchikus location műveletek.
 *
 * <p>Legfontosabb feladata: a {@link #validateNoCycle(Long, Long)} metódus, ami rekurzívan
 * végigmegy a parent láncon, és dob, ha ciklust talál.
 *
 * <p>A {@link #move(Long, Long)} metódus Optimistic Lock retry logikát használ: ha párhuzamos
 * módosítás miatt OptimisticLockException dobódik, a Spring Retry 3x újrapróbálkozik exponential
 * backoff-fal (1s, 2s, 4s).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

  /**
   * Maximális fa-mélység, amit a {@link #buildTree()} visszaad. Védelmet nyújt végtelen rekurzió
   * vagy rossz adat ellen.
   */
  private static final int MAX_TREE_DEPTH = 10;

  private final LocationRepository locationRepository;

  /**
   * Location mozgatása új parenthez (cycle check + optimistic lock retry).
   *
   * <p>Retry policy: 3 attempt exponential backoff (1s, 2s, 4s). Ha mind a 3 próbálkozás
   * OptimisticLockException-t dob, a service az utolsó kivételt továbbdobja a
   * GlobalExceptionHandler felé.
   */
  @Retryable(
      retryFor = {OptimisticLockException.class, ObjectOptimisticLockingFailureException.class},
      maxAttempts = 3,
      backoff = @Backoff(delay = 1000, multiplier = 2.0))
  @Transactional
  public Location move(Long locationId, Long newParentId) {
    Location location =
        locationRepository
            .findById(locationId)
            .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + locationId));

    if (newParentId != null) {
      locationRepository
          .findById(newParentId)
          .orElseThrow(
              () -> new ResourceNotFoundException("Parent location not found: " + newParentId));
      validateNoCycle(locationId, newParentId);
      location.setParent(locationRepository.findById(newParentId).orElse(null));
    } else {
      location.setParent(null);
    }

    Location saved = locationRepository.save(location);
    log.info(
        "Location {} moved to parent {} (version {})", locationId, newParentId, saved.getVersion());
    return saved;
  }

  /**
   * Rekurzív ciklusellenőrzés.
   *
   * <p>Ha egy location parentId-jét (vagy saját ID-jét) át akarjuk állítani, ellenőrizzük, hogy a
   * parent lánc nem vezet vissza a saját location-höz.
   *
   * <p>Algoritmus:
   *
   * <ol>
   *   <li>Ha a parentId == locationId, ciklus (location lenne a saját szülője)
   *   <li>Követjük a parent láncot felfelé, amíg parent == null vagy parent != locationId
   *   <li>Ha parent == locationId, ciklus van
   *   <li>Ha parent lánc túl mély (védelem), break
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
          "locationCycleDetected", "Location cannot be its own parent");
    }

    // Rekurzívan követjük a parent láncot a newParentId-től felfelé
    Long currentParentId = newParentId;
    List<Long> visited = new ArrayList<>();
    int maxDepth = 100; // Védelem végtelen ciklus ellen

    while (currentParentId != null && maxDepth-- > 0) {
      if (visited.contains(currentParentId)) {
        // Már meglátogatott node — a DB szintű adat már ciklikus
        break;
      }
      visited.add(currentParentId);

      if (currentParentId.equals(locationId)) {
        throw new BusinessValidationException(
            "locationCycleDetected",
            "Cyclic parent reference detected: location "
                + locationId
                + " is a descendant of "
                + newParentId);
      }

      Location parent = locationRepository.findById(currentParentId).orElse(null);
      if (parent == null) {
        break;
      }
      currentParentId = parent.getParent() != null ? parent.getParent().getId() : null;
    }
  }

  /**
   * Hierarchikus location fa építése.
   *
   * <p>A tree a root node-októl (parent == null) indul, és rekurzívan járja be a children
   * kollekciót. A {@link Location#getChildren()} kollekció {@code LAZY} fetch, ezért a metódus
   * {@code @Transactional(readOnly = true)}-ben fut.
   *
   * <p>A {@link #MAX_TREE_DEPTH} (10) korlát védi a végtelen rekurziótól. Ha egy adott hierarchia
   * mélyebb, a level {@code depth = MAX_TREE_DEPTH} node üres children listát kap — a frontend így
   * is jelzi, hol van a határ.
   *
   * <p>A DB szintű ciklusok ellen a {@link #visitedIds} set véd — bár a {@link #validateNoCycle}
   * ezt megakadályozza create/update során, a védelem defense-in-depth.
   *
   * @return a teljes location fa, root szinten (parent == null) lévő node-okkal
   */
  @Transactional(readOnly = true)
  public List<LocationTreeDto> buildTree() {
    List<Location> roots = locationRepository.findByParentIsNull();
    return roots.stream().map(root -> buildNode(root, null, 0, new java.util.HashSet<>())).toList();
  }

  private LocationTreeDto buildNode(
      Location location, Long parentId, int depth, java.util.Set<Long> visitedIds) {
    if (depth >= MAX_TREE_DEPTH) {
      // Mélységi limit elérve — üres children listával térünk vissza
      return new LocationTreeDto(
          location.getId(), location.getName(), location.getType(), parentId, depth, List.of());
    }

    // Ciklus-védelem: ha már meglátogattuk ezt az ID-t, ne folytassuk
    if (!visitedIds.add(location.getId())) {
      return new LocationTreeDto(
          location.getId(), location.getName(), location.getType(), parentId, depth, List.of());
    }

    List<LocationTreeDto> children =
        location.getChildren().stream()
            .map(child -> buildNode(child, location.getId(), depth + 1, visitedIds))
            .toList();

    return new LocationTreeDto(
        location.getId(), location.getName(), location.getType(), parentId, depth, children);
  }
}
