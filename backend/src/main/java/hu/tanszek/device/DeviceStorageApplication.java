package hu.tanszek.device;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot alkalmazás belépési pontja.
 *
 * <p>A rendszer főbb funkciói:
 * <ul>
 *   <li>Eszköz- és szoftver-nyilvántartás (devices, softwares, device_softwares kapcsolat)</li>
 *   <li>Helyszín-nyilvántartás hierarchikus felépítésben (locations)</li>
 *   <li>Felhasználó-kezelés granularitású jogosultságokkal (app_users, roles, permissions)</li>
 *   <li>Eszköz hozzárendelés user-ekhez/location-okhöz (device_assignments)</li>
 *   <li>Audit log + rollback (audit_logs, EntityTypeRegistry)</li>
 *   <li>JWT auth refresh token rotation-nel és kid rotációval</li>
 *   <li>Row-level szűrés minden műveletre</li>
 *   <li>Excel import (Apache POI)</li>
 *   <li>i18n (hu + en, code-generált TypeScript union type)</li>
 * </ul>
 *
 * <p>Annotációk:
 * <ul>
 *   <li>{@code @SpringBootApplication} — Spring Boot auto-config + component scan</li>
 *   <li>{@code @EnableJpaAuditing} — created_at/updated_at JPA Auditing a BaseEntity-nél</li>
 *   <li>{@code @EnableScheduling} — @Scheduled metódusok (backup cleanup, audit retention, refresh token cleanup)</li>
 *   <li>{@code @ConfigurationPropertiesScan} — @ConfigurationProperties osztályok (JwtProperties, stb.)</li>
 * </ul>
 *
 * @see <a href="../implementation_plan.md">implementation_plan.md</a> — teljes architektúra
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
@ConfigurationPropertiesScan
public class DeviceStorageApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeviceStorageApplication.class, args);
    }
}