package hu.tanszek.device.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tesztek a {@link GlobalExceptionHandler} response formátumához.
 *
 * <p>A Sonner toast fallback mechanizmus a backend oldali response formátumon
 * alapul — a frontend a {@code messageKey}-t próbálja lefordítani, és ha nincs
 * az i18n resource-ban, a {@code message} mezőt használja fallback-ként.
 *
 * <p>Ez a teszt biztosítja, hogy a response formátum mindig tartalmazza mindkét
 * mezőt, így a frontend fallback soha nem kap üres stringet.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessValidation_returnsBothMessageKeyAndMessage() {
        BusinessValidationException ex = new BusinessValidationException(
                "deviceNotAssignable",
                "Device is in MAINTENANCE status"
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/devices/1/assignments");

        ResponseEntity<Map<String, Object>> response = handler.handleBusinessValidation(
                ex, new ServletWebRequest(request));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("messageKey")).isEqualTo("deviceNotAssignable");
        assertThat(body.get("message")).isEqualTo("Device is in MAINTENANCE status");
        assertThat(body.get("path")).isEqualTo("/api/devices/1/assignments");
        assertThat(body.get("status")).isEqualTo(400);
        assertThat(body.get("error")).isEqualTo("Bad Request");
    }

    @Test
    void rateLimit_returns429InsteadOf400() {
        BusinessValidationException ex = new BusinessValidationException(
                "rateLimitExceeded",
                "Too many requests"
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");

        ResponseEntity<Map<String, Object>> response = handler.handleBusinessValidation(
                ex, new ServletWebRequest(request));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody().get("messageKey")).isEqualTo("rateLimitExceeded");
    }

    @Test
    void resourceNotFound_returns404WithResourceNotFoundKey() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Device not found: 99");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/devices/99");

        ResponseEntity<Map<String, Object>> response = handler.handleResourceNotFound(
                ex, new ServletWebRequest(request));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        Map<String, Object> body = response.getBody();
        assertThat(body.get("messageKey")).isEqualTo("resourceNotFound");
        assertThat(body.get("message")).isEqualTo("Device not found: 99");
    }

    @Test
    void unauthorized_returns403WithCustomKey() {
        UnauthorizedActionException ex = new UnauthorizedActionException(
                "permissionDenied",
                "You do not have permission for this action"
        );
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/users/1");

        ResponseEntity<Map<String, Object>> response = handler.handleUnauthorized(
                ex, new ServletWebRequest(request));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("messageKey")).isEqualTo("permissionDenied");
    }

    @Test
    void unauthorized_returns401ForAuthRequiredKey() {
        UnauthorizedActionException ex = new UnauthorizedActionException(
                "authRequired",
                "Authentication required"
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/devices");

        ResponseEntity<Map<String, Object>> response = handler.handleUnauthorized(
                ex, new ServletWebRequest(request));

        // authRequired kulcs → 401 (a többi → 403)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("messageKey")).isEqualTo("authRequired");
    }

    @Test
    void fallback_returns500WithInternalErrorKey() {
        Exception ex = new RuntimeException("Something went very wrong");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/devices");

        ResponseEntity<Map<String, Object>> response = handler.handleAll(
                ex, new ServletWebRequest(request));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        Map<String, Object> body = response.getBody();
        assertThat(body.get("messageKey")).isEqualTo("internalError");
        // A fallback message soha nem szivárogtatja ki a belső exception message-t
        assertThat(body.get("message")).isEqualTo("An unexpected error occurred");
    }

    @Test
    void fileTooLarge_returns413() {
        org.springframework.web.multipart.MaxUploadSizeExceededException ex =
                new org.springframework.web.multipart.MaxUploadSizeExceededException(15 * 1024 * 1024L);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/devices/1/attachments");

        ResponseEntity<Map<String, Object>> response = handler.handleMaxUploadSizeExceeded(
                ex, new ServletWebRequest(request));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().get("messageKey")).isEqualTo("fileTooLarge");
    }

    @Test
    void responseAlwaysContainsMessageAndMessageKey_forFrontendFallback() {
        // Ez a teszt a frontend Sonner fallback mechanizmus alapfeltétele:
        // a response body MINDIG tartalmazza mindkét mezőt (messageKey + message),
        // így a frontend soha nem kap üres fallback stringet.
        BusinessValidationException bv = new BusinessValidationException("testKey", "test message");
        ResourceNotFoundException rf = new ResourceNotFoundException("not found");
        UnauthorizedActionException ua = new UnauthorizedActionException("permissionDenied", "no perms");
        RuntimeException rt = new RuntimeException("crash");

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/test");
        ServletWebRequest webReq = new ServletWebRequest(req);

        for (var response : List.of(
                handler.handleBusinessValidation(bv, webReq),
                handler.handleResourceNotFound(rf, webReq),
                handler.handleUnauthorized(ua, webReq),
                handler.handleAll(rt, webReq)
        )) {
            Map<String, Object> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.get("messageKey"))
                    .as("messageKey must never be null/empty")
                    .isNotNull()
                    .asString()
                    .isNotEmpty();
            assertThat(body.get("message"))
                    .as("message must never be null/empty (frontend fallback target)")
                    .isNotNull()
                    .asString()
                    .isNotEmpty();
        }
    }
}
