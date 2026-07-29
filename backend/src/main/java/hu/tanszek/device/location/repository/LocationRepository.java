package hu.tanszek.device.location.repository;

import hu.tanszek.device.location.entity.Location;
import hu.tanszek.device.location.entity.LocationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Location-ok repository.
 *
 * A {@link JpaSpecificationExecutor} a Fázis 3-ban (Task 3.2) lesz használva
 * a row-level szűréshez és a dinamikus frontend szűrőkhöz.
 *
 * @see Location
 */
@Repository
public interface LocationRepository extends JpaRepository<Location, Long>, JpaSpecificationExecutor<Location> {

    /**
     * Root location-ok listája (parent_id IS NULL).
     */
    List<Location> findByParentIsNull();

    /**
     * Child location-ok listája egy adott parent alatt.
     */
    List<Location> findByParentId(Long parentId);

    /**
     * Location-ok listája típus szerint.
     */
    List<Location> findByType(LocationType type);
}