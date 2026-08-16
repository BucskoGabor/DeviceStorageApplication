package hu.tanszek.device.config.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import hu.tanszek.device.config.entity.Config;

/**
 * Konfigurációs bejegyzések repository.
 *
 * @see Config
 */
@Repository
public interface ConfigRepository extends JpaRepository<Config, Long> {

  /**
   * Konfigurációs érték keresése kulcs alapján.
   *
   * @param key a konfigurációs kulcs (pl. "AUTH_PROVIDER")
   * @return Optional a konfigurációs értékkel
   */
  Optional<Config> findByKey(String key);

  /** Ellenőrzi, hogy létezik-e a kulcs. */
  boolean existsByKey(String key);
}
