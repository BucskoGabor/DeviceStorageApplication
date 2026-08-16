package hu.tanszek.device.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * OpenApiConfig — Springdoc OpenAPI integráció testreszabása.
 *
 * <p>A {@code springdoc-openapi-starter-webmvc-ui} dependency a Spring Boot auto-configgal
 * aktiválja az alapértelmezett {@code /v3/api-docs} (JSON) és {@code /swagger-ui.html} (UI)
 * endpoint-okat.
 *
 * <p>Ez az osztály testreszabja:
 *
 * <ul>
 *   <li>Project info (title, version, description, contact, license)
 *   <li>JWT Bearer security scheme (Authorization: Bearer xxx header)
 * </ul>
 */
@Configuration
public class OpenApiConfig {

  /**
   * OpenAPI bean — project info + JWT bearer security scheme.
   *
   * @return testreszabott OpenAPI objektum
   */
  @Bean
  public OpenAPI deviceStorageOpenAPI() {
    return new OpenAPI()
        // Project info
        .info(
            new Info()
                .title("Device Storage API")
                .description(
                    "Egyetemi Informatikai Tanszéki Nyilvántartó Rendszer — REST API. "
                        + "JWT authentikáció, row-level szűrés, audit log, "
                        + "Excel import, device assignment management.")
                .version("0.1.0")
                .contact(new Contact().name("Tanszéki Admin").email("admin@tanszek.local"))
                .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))

        // JWT Bearer security scheme
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearer-jwt",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description(
                            "JWT access token az Authorization headerben: 'Bearer {token}'")))

        // Alapértelmezett security requirement (minden endpoint védett)
        .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
  }
}
