package hu.tanszek.device.device.repository;

import hu.tanszek.device.device.entity.Device;
import hu.tanszek.device.device.entity.DeviceStatus;
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
}