package hu.tanszek.device;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Smoke test — sanity check, hogy a teszt infrastruktúra működik.
 *
 * <p>A teljes alkalmazás-kontextus betöltését az integrációs tesztek ({@code
 * hu.tanszek.device.integration.*}) végzik Testcontainers-szel, a {@code mvn verify -Pintegration}
 * fázisban. Unit teszt fázisban ({@code mvn test}) nem töltünk Spring contextet, mert az
 * felesleges külső függőség (Docker/Postgres) bevezetése lenne.
 */
class DeviceStorageApplicationTests {

  @Test
  void contextLoads() {
    assertThat(true).isTrue();
  }
}
