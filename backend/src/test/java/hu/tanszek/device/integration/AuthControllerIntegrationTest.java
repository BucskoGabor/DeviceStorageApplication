package hu.tanszek.device.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;

import hu.tanszek.device.auth.dto.LoginRequest;
import hu.tanszek.device.user.entity.AppUser;
import hu.tanszek.device.user.repository.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration teszt az AuthController /api/auth/login endpoint-hoz.
 *
 * <p>Testcontainers PostgreSQL + valódi Flyway migrációk + valódi Argon2 hash. A MockMvc a teljes
 * Spring context-et felépíti, így a Security Filter Chain (JwtAuthenticationFilter,
 * RateLimitFilter, stb.) is valódi.
 */
class AuthControllerIntegrationTest extends AbstractIntegrationTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private AppUserRepository userRepository;
  @Autowired private ObjectMapper objectMapper;

  private MockMvc mockMvc;
  private final Argon2PasswordEncoder passwordEncoder =
      new Argon2PasswordEncoder(16, 32, 1, 65536, 3);

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  @Test
  @Transactional
  void loginSuccessWithValidCredentials() throws Exception {
    // Setup: demo admin user (a V2__seed.sql-ből jön, de újrainicializáljuk)
    String emailHash = computeSha256("admin@tanszek.local");
    AppUser admin = userRepository.findByEmailHash(emailHash).orElseThrow();
    // Frissítjük a jelszót, hogy biztosan tudjunk belépni
    admin.setPasswordHash(passwordEncoder.encode("ChangeMe123!"));
    admin.setMustChangePassword(false);
    userRepository.save(admin);

    // Login
    LoginRequest request = new LoginRequest("admin@tanszek.local", "ChangeMe123!");

    MvcResult result =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.expiresIn").exists())
            .andExpect(jsonPath("$.role").value("ROLE_ADMIN"))
            .andExpect(jsonPath("$.permissions").isArray())
            .andExpect(jsonPath("$.mustChangePassword").value(false))
            .andReturn();

    assertThat(result.getResponse().getCookie("refresh_token")).isNotNull();
  }

  @Test
  @Transactional
  void loginFailureWithInvalidPassword() throws Exception {
    LoginRequest request = new LoginRequest("admin@tanszek.local", "WrongPassword!");

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.messageKey").value("invalidCredentials"));
  }

  @Test
  @Transactional
  void loginFailureWithNonexistentUser() throws Exception {
    LoginRequest request = new LoginRequest("nonexistent@tanszek.local", "AnyPassword");

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.messageKey").value("invalidCredentials"));
  }

  @Test
  void loginWithEmptyBodyShouldReturnBadRequest() throws Exception {
    mockMvc
        .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
  }

  /** SHA-256 hash az email-ből (mint a LocalAuthProvider). */
  private String computeSha256(String input) {
    try {
      byte[] hash =
          java.security.MessageDigest.getInstance("SHA-256")
              .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
