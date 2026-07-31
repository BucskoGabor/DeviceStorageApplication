package hu.tanszek.device.attachment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import hu.tanszek.device.attachment.entity.DeviceAttachment;

/**
 * Device attachment repository.
 *
 * @see DeviceAttachment
 */
@Repository
public interface DeviceAttachmentRepository extends JpaRepository<DeviceAttachment, Long> {

  /** Device összes attachment listája. */
  List<DeviceAttachment> findByDeviceId(Long deviceId);

  /** Device attachment számának lekérdezése (max 5 limit check). */
  long countByDeviceId(Long deviceId);

  /** Device összes attachment törlése (cascade delete). */
  void deleteByDeviceId(Long deviceId);
}
