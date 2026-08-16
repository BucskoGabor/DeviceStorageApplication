package hu.tanszek.device.auth.jwt;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.ServletException;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private UserDetailsService userDetailsService;

  @InjectMocks private JwtAuthenticationFilter filter;

  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private MockFilterChain filterChain;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    filterChain = new MockFilterChain();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void doFilter_authenticatesValidBearerToken() throws ServletException, IOException {
    request.addHeader("Authorization", "Bearer valid.jwt.token");
    Claims claims = Jwts.claims().subject("userHash123").build();

    UserDetails userDetails =
        new User("userHash123", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    when(jwtTokenProvider.validateTokenWithGracePeriod("valid.jwt.token")).thenReturn(claims);
    when(userDetailsService.loadUserByUsername("userHash123")).thenReturn(userDetails);

    filter.doFilterInternal(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
        .isEqualTo("userHash123");
  }

  @Test
  void doFilter_ignoresInvalidToken() throws ServletException, IOException {
    request.addHeader("Authorization", "Bearer invalid.token");

    when(jwtTokenProvider.validateTokenWithGracePeriod("invalid.token"))
        .thenThrow(new JwtException("Invalid token"));

    filter.doFilterInternal(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }
}
