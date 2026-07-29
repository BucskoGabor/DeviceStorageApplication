package hu.tanszek.device;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test — ellenőrzi, hogy a Spring Boot alkalmazás kontextusa betöltődik.
 * A Task 1.2-ben ez az első ellenőrzés.
 *
 * <p>A SpringBootTest az alkalmazás teljes kontextusát indítja,
 * ami segít észrevenni a dependency injection és konfigurációs hibákat.
 *
 * <p>H2 in-memory database-t használ a teszt, mert a PostgreSQL konténer még nem elérhető.
 * A Flyway migrációk a H2-n is lefutnak (ha kompatibilis), vagy kihagyjuk a @SpringBootTest
 * konfigurációval.
 */
@SpringBootTest
class DeviceStorageApplicationTests {

    @Test
    void contextLoads() {
        // Ha az alkalmazás kontextusa nem töltődik be, ez a teszt fail-el.
        // Implementációkor: assertThat(deviceStorageApplication).isNotNull();
        assertThat(true).isTrue();
    }
}