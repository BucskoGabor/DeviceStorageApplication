package hu.tanszek.device.device.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import hu.tanszek.device.device.entity.Device;
import hu.tanszek.device.device.entity.DeviceStatus;

/**
 * Device repository.
 *
 * <p>A {@link JpaSpecificationExecutor} a row-level szűréshez és egyedi feltételrendszerekhez
 * biztosít támogatást.
 *
 * @see Device
 */
@Repository
public interface DeviceRepository
    extends JpaRepository<Device, Long>, JpaSpecificationExecutor<Device> {

  /** Device keresése inventory_number alapján. */
  Optional<Device> findByInventoryNumber(String inventoryNumber);

  /** Device-ok listája státusz szerint. */
  List<Device> findByStatus(DeviceStatus status);

  /** Device-ok listája státusz szerint időrendben. */
  List<Device> findByStatusOrderByCreatedAtDesc(DeviceStatus status);

  /** Device-ok lapozott listája státusz szerint. */
  Page<Device> findByStatus(DeviceStatus status, Pageable pageable);

  /**
   * Azon device-ok listája, amelyekre egy adott szoftver telepítve van.
   *
   * <p>JPQL JOIN a {@code device_softwares} táblán keresztül — a Software entitás nem tartalmaz
   * visszafelé mutató {@code devices} kollekciót, ezért ezt a lekérdezést itt, a Device oldalon
   * definiáljuk.
   */
  @org.springframework.data.jpa.repository.Query(
      "SELECT d FROM Device d JOIN d.softwares s WHERE s.id = :softwareId")
  java.util.List<Device> findDevicesBySoftwareId(
      @org.springframework.data.repository.query.Param("softwareId") Long softwareId);

  /** Egy adott helyszínen (raktár vagy terem) lévő, nem selejtezett eszközök listája. */
  List<Device> findByCurrentLocationIdAndStatusNot(Long locationId, DeviceStatus status);
}
