package hu.tanszek.device.integration;

import org.springframework.boot.test.context.SpringBootTest;
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
 * <p>Használat:
 *
 * <pre>
 *   &#64;SpringBootTest
 *   class MyServiceIntegrationTest extends AbstractIntegrationTest {
 *     &#64;Autowired MyService service;
 *
 *     &#64;Test void testSomething() {
 *       service.doSomething();
 *       // ...
 *     }
 *   }
 * </pre>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

  /**
   * PostgreSQL container — minden teszt osztály egy shared container-t kap. A Testcontainers
   * automatikusan elindítja a konténert az első teszt metódus előtt, és leállítja az utolsó után.
   *
   * <p>A {@code withFixedExposedPort(5432, 5432)} biztosítja, hogy a Spring context cache kulcsa
   * (amely a JDBC URL-t tartalmazza) konzisztens maradjon a teszt osztályok között — máskülönben a
   * Testcontainers random portot választ, és a második teszt osztály Spring context cache miss-t
   * okoz ami Hikari connection timeout-hoz vezethet.
   */
  @Container
  protected static final PostgreSQLContainer<?> postgresContainer =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("tanszek_db_test")
          .withUsername("admin")
          .withPassword("test_password")
          .withFixedExposedPort(5432, 5432)
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
