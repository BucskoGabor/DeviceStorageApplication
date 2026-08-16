package hu.tanszek.device.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class az integration tesztekhez — Testcontainers PostgreSQL.
 *
 * <p>Az integration tesztek ezt az osztályt extendelik, és kapnak egy valódi PostgreSQL konténert
 * minden teszthez. A Flyway migrációk automatikusan lefutnak az alkalmazás indításakor
 * (application-test.yml Flyway config alapján).
 *
   * <p>A {@code @DirtiesContext} biztosítja, hogy minden teszt osztály friss Spring contextet kapjon
   * — a Testcontainers container a teszt osztály után leáll, és a Spring context cache a régi
   * (leállított) container connection-jét tárolná, ami miatt a Flyway migrációk nem futnának le az új
   * containeren.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
public abstract class AbstractIntegrationTest {

  /**
   * PostgreSQL container — minden teszt osztály kap egy saját container-t. A Testcontainers
   * automatikusan elindítja a konténert az első teszt metódus előtt, és leállítja az utolsó után.
   */
  @Container
  protected static final PostgreSQLContainer<?> postgresContainer =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("tanszek_db_test")
          .withUsername("admin")
          .withPassword("test_password")
          .withReuse(true);

  /**
   * Spring environment beállítása a container URL-jére. A Flyway ez alapján fog lefutni az
   * alkalmazás indításakor.
   */
  @DynamicPropertySource
  static void registerPostgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
    registry.add("spring.datasource.username", postgresContainer::getUsername);
    registry.add("spring.datasource.password", postgresContainer::getPassword);
    registry.add("spring.flyway.enabled", () -> "true");
  }
}
