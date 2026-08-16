package hu.tanszek.device.auth.jwt;

import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;

class JwtTokenProviderTest {

  private JwtTokenProvider jwtTokenProvider;
  private JwtProperties jwtProperties;

  @BeforeEach
  void setUp() {
    jwtProperties = new JwtProperties();
    // 32-byte secret Base64 encoded
    String secret =
        Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes());
    jwtProperties.getKids().setActive(secret);
    jwtProperties.setAccessTokenTtlMin(15);

    jwtTokenProvider = new JwtTokenProvider(jwtProperties);
  }

  @Test
  void generateAndValidateToken() {
    String token =
        jwtTokenProvider.generateAccessToken(
            "testEmailHash", "ROLE_ADMIN", List.of("DEVICE_READ", "DEVICE_CREATE"));

    assertThat(token).isNotBlank();

    Claims claims = jwtTokenProvider.validateTokenWithGracePeriod(token);
    assertThat(claims.getSubject()).isEqualTo("testEmailHash");
    assertThat(claims.get("role")).isEqualTo("ROLE_ADMIN");

    var authorities = jwtTokenProvider.extractAuthorities(claims);
    assertThat(authorities)
        .extracting(GrantedAuthority::getAuthority)
        .contains("ROLE_ADMIN", "DEVICE_READ", "DEVICE_CREATE");
  }
}
