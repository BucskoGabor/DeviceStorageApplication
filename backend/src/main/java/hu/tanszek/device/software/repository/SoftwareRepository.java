package hu.tanszek.device.software.repository;

import hu.tanszek.device.software.entity.Software;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Software repository.
 *
 * @see Software
 */
@Repository
public interface SoftwareRepository extends JpaRepository<Software, Long> {

    /**
     * Software keresése név alapján.
     */
    Optional<Software> findByName(String name);
}