package hu.tanszek.device.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import hu.tanszek.device.auth.entity.Permission;

/**
 * Permission-ök repository.
 *
 * @see Permission
 */
@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

  /**
   * Permission keresése név alapján.
   *
   * @param name a permission neve (pl. "DEVICE_READ")
   * @return Optional a permission-nel
   */
  Optional<Permission> findByName(String name);
}
