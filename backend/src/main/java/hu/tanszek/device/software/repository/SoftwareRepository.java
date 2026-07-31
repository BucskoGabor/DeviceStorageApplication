package hu.tanszek.device.software.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import hu.tanszek.device.software.entity.Software;

/**
 * Software repository.
 *
 * @see Software
 */
@Repository
public interface SoftwareRepository extends JpaRepository<Software, Long> {

  /** Software keresése név alapján. */
  Optional<Software> findByName(String name);
}
