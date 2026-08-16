package hu.tanszek.device.location.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import hu.tanszek.device.location.entity.Location;
import hu.tanszek.device.location.entity.LocationType;

/**
 * Location-ok repository.
 *
 * <p>A {@link JpaSpecificationExecutor} a dinamikus szűrésekhez és lekérdezésekhez biztosít
 * támogatást.
 *
 * @see Location
 */
@Repository
public interface LocationRepository
    extends JpaRepository<Location, Long>, JpaSpecificationExecutor<Location> {

  /** Root location-ok listája (parent_id IS NULL). */
  List<Location> findByParentIsNull();

  /**
   * Child location-ok listája egy adott parent alatt.
   *
   * <p>A Spring Data JPA method naming convention nem működik, mert a {@code parent} mező egy
   * {@code Location} entitás (ManyToOne), nem egy egyszerű long ID. Helyette explicit JPQL query-t
   * használunk.
   */
  @Query("SELECT l FROM Location l WHERE l.parent.id = :parentId")
  List<Location> findByParentId(@Param("parentId") Long parentId);

  /** Location-ok listája típus szerint. */
  List<Location> findByType(LocationType type);
}
