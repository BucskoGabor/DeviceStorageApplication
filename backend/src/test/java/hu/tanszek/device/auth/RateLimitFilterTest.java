package hu.tanszek.device.auth;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import jakarta.servlet.ServletException;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

  @Mock private RateLimiterService rateLimiterService;
  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks private RateLimitFilter rateLimitFilter;

  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private MockFilterChain filterChain;

  @BeforeEach
  void setUp() {
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    filterChain = new MockFilterChain();
  }

  @Test
  void doFilter_skipsNonLogin() throws ServletException, IOException {
    request.setRequestURI("/api/devices");
    request.setMethod("GET");

    rateLimitFilter.doFilterInternal(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void doFilter_allowsLoginWithinLimits() throws ServletException, IOException {
    request.setRequestURI("/api/auth/login");
    request.setMethod("POST");
    request.setContent("{\"email\":\"user@test.local\",\"password\":\"pass\"}".getBytes());

    when(rateLimiterService.tryConsumePerIp(anyString())).thenReturn(true);
    when(rateLimiterService.tryConsumePerEmail("user@test.local")).thenReturn(true);

    rateLimitFilter.doFilterInternal(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void doFilter_blocksWhenIpLimitExceeded() throws ServletException, IOException {
    request.setRequestURI("/api/auth/login");
    request.setMethod("POST");

    when(rateLimiterService.tryConsumePerIp(anyString())).thenReturn(false);

    rateLimitFilter.doFilterInternal(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(429);
    assertThat(response.getHeader("Retry-After")).isEqualTo("60");
  }

  @Test
  void doFilter_blocksWhenEmailLimitExceeded() throws ServletException, IOException {
    request.setRequestURI("/api/auth/login");
    request.setMethod("POST");
    request.setContent("{\"email\":\"spammer@test.local\",\"password\":\"pass\"}".getBytes());

    when(rateLimiterService.tryConsumePerIp(anyString())).thenReturn(true);
    when(rateLimiterService.tryConsumePerEmail("spammer@test.local")).thenReturn(false);

    rateLimitFilter.doFilterInternal(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(429);
  }
}
