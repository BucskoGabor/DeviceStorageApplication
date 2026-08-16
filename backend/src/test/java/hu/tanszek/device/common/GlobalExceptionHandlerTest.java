package hu.tanszek.device.common;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

  private GlobalExceptionHandler handler;
  private ServletWebRequest webRequest;

  @BeforeEach
  void setUp() {
    handler = new GlobalExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/test");
    webRequest = new ServletWebRequest(request);
  }

  @Test
  void handleBusinessValidation_badRequest() {
    BusinessValidationException ex = new BusinessValidationException("invalidField", "Bad input");
    ResponseEntity<Map<String, Object>> response = handler.handleBusinessValidation(ex, webRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().get("messageKey")).isEqualTo("invalidField");
  }

  @Test
  void handleBusinessValidation_rateLimit() {
    BusinessValidationException ex =
        new BusinessValidationException("rateLimitExceeded", "Too many requests");
    ResponseEntity<Map<String, Object>> response = handler.handleBusinessValidation(ex, webRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().get("messageKey")).isEqualTo("rateLimitExceeded");
  }

  @Test
  void handleResourceNotFound() {
    ResourceNotFoundException ex = new ResourceNotFoundException("Not found");
    ResponseEntity<Map<String, Object>> response = handler.handleResourceNotFound(ex, webRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().get("messageKey")).isEqualTo("resourceNotFound");
  }

  @Test
  void handleAuthentication_badCredentials() {
    BadCredentialsException ex = new BadCredentialsException("Bad creds");
    ResponseEntity<Map<String, Object>> response = handler.handleAuthentication(ex, webRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody().get("messageKey")).isEqualTo("invalidCredentials");
  }

  @Test
  void handleAuthentication_disabled() {
    DisabledException ex = new DisabledException("Disabled");
    ResponseEntity<Map<String, Object>> response = handler.handleAuthentication(ex, webRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody().get("messageKey")).isEqualTo("userDisabled");
  }

  @Test
  void handleAuthentication_locked() {
    LockedException ex = new LockedException("Locked");
    ResponseEntity<Map<String, Object>> response = handler.handleAuthentication(ex, webRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody().get("messageKey")).isEqualTo("userLocked");
  }

  @Test
  void handleMaxUploadSizeExceeded() {
    MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(10485760L);
    ResponseEntity<Map<String, Object>> response =
        handler.handleMaxUploadSizeExceeded(ex, webRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    assertThat(response.getBody().get("messageKey")).isEqualTo("fileTooLarge");
  }

  @Test
  void handleAll() {
    Exception ex = new RuntimeException("Unexpected error");
    ResponseEntity<Map<String, Object>> response = handler.handleAll(ex, webRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody().get("messageKey")).isEqualTo("internalError");
  }
}
