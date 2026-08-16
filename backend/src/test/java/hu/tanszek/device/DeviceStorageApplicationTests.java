package hu.tanszek.device;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test — ellenőrzi, hogy a Spring Boot alkalmazás kontextusa betöltődik.
 *
 * <p>A SpringBootTest az alkalmazás teljes kontextusát indítja, ami segít észrevenni a dependency
 * injection és konfigurációs hibákat.
 *
 * <p>H2 in-memory database-t használ, mert a PostgreSQL konténer a CI-ban nem elérhető a unit teszt
 * fázisban (csak az mvn verify -Pintegration lépésben, Testcontainers-szel). A Flyway migrációk
 * kimaradnak, a Hibernate {code ddl-auto: create-drop} generálja a sémát.
 */
@SpringBootTest
@ActiveProfiles("smoketest")
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:device-storage-smoketest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "spring.flyway.enabled=false"
    })
class DeviceStorageApplicationTests {

  @Test
  void contextLoads() {
    assertThat(true).isTrue();
  }
}
