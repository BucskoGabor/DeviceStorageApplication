package hu.tanszek.device.device.repository;

import hu.tanszek.device.device.entity.Device;
import hu.tanszek.device.device.entity.DeviceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Device repository.
 *
 * A {@link JpaSpecificationExecutor} a Fázis 3-ban (Task 3.2) lesz használva
 * a row-level szűréshez (STUDENT/TEACHER csak a saját eszközeiket látják).
 *
 * @see Device
 */
@Repository
public interface DeviceRepository extends JpaRepository<Device, Long>, JpaSpecificationExecutor<Device> {

    /**
     * Device keresése inventory_number alapján.
     */
    Optional<Device> findByInventoryNumber(String inventoryNumber);

    /**
     * Device-ok listája státusz szerint.
     */
    List<Device> findByStatus(DeviceStatus status);

    /**
     * Device-ok lapozott listája státusz szerint.
     */
    Page<Device> findByStatus(DeviceStatus status, Pageable pageable);

    /**
     * Azon device-ok listája, amelyekre egy adott szoftver telepítve van.
     *
     * <p>JPQL JOIN a {@code device_softwares} táblán keresztül — a
     * Software entitás nem tartalmaz visszafelé mutató {@code devices} kollekciót,
     * ezért ezt a lekérdezést itt, a Device oldalon definiáljuk.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT d FROM Device d JOIN d.softwares s WHERE s.id = :softwareId"
    )
    java.util.List<Device> findDevicesBySoftwareId(@org.springframework.data.repository.query.Param("softwareId") Long softwareId);
}