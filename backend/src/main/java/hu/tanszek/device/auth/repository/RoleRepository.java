package hu.tanszek.device.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import hu.tanszek.device.auth.entity.Role;

/**
 * Role-ok repository.
 *
 * @see Role
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

  /**
   * Role keresése név alapján.
   *
   * @param name a role neve (pl. "ROLE_ADMIN")
   * @return Optional a role-lal
   */
  Optional<Role> findByName(String name);

  @org.springframework.data.jpa.repository.Query("SELECT DISTINCT r FROM Role r LEFT JOIN FETCH r.permissions ORDER BY r.name")
  java.util.List<Role> findAllWithPermissions();

  @org.springframework.data.jpa.repository.Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions WHERE r.id = :id")
  Optional<Role> findByIdWithPermissions(Long id);
}
