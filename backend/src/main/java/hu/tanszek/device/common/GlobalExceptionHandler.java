package hu.tanszek.device.common;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import lombok.extern.slf4j.Slf4j;

/**
 * GlobalExceptionHandler — központi REST exception handler.
 *
 * <p>Minden controller-ből kijövő kivételt egységes JSON formátumban ad vissza:
 *
 * <pre>
 *   {
 *     "timestamp": "2026-07-29T...",
 *     "status": 400,
 *     "error": "Bad Request",
 *     "messageKey": "validationError",
 *     "message": "...",
 *     "path": "/api/users",
 *     "details": [...]  // opcionális, validation hibáknál
 *   }
 * </pre>
 *
 * <p>Handler-ek:
 *
 * <ul>
 *   <li>{@link BusinessValidationException} → 400 (vagy 429 rateLimitExceeded esetén)
 *   <li>{@link ResourceNotFoundException} → 404
 *   <li>{@link NoResourceFoundException} → 404 (Spring 6+ DispatcherServlet „no route match” handler)
 *   <li>{@link UnauthorizedActionException} → 401/403
 *   <li>{@link AuthenticationException} (BadCredentials, Disabled, Locked) → 401
 *   <li>{@link HttpMessageNotReadableException} → 400 (hibás JSON body)
 *   <li>{@link MethodArgumentNotValidException} → 400 validation hibákkal
 *   <li>{@link MaxUploadSizeExceededException} → 413 (10 MB felett)
 *   <li>{@link Exception} fallback → 500
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  /** BusinessValidationException — 400 vagy 429 (rateLimitExceeded). */
  @ExceptionHandler(BusinessValidationException.class)
  public ResponseEntity<Map<String, Object>> handleBusinessValidation(
      BusinessValidationException ex, WebRequest request) {
    String path = getPath(request);
    String messageKey = ex.getMessageKey();
    boolean isRateLimit = "rateLimitExceeded".equals(messageKey);

    Map<String, Object> body =
        createBody(
            isRateLimit ? HttpStatus.TOO_MANY_REQUESTS.value() : HttpStatus.BAD_REQUEST.value(),
            isRateLimit ? "Too Many Requests" : "Bad Request",
            messageKey,
            ex.getMessage(),
            path,
            null);

    log.warn("Business validation: {} at {} — {}", messageKey, path, ex.getMessage());
    return ResponseEntity.status(
            isRateLimit ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_REQUEST)
        .body(body);
  }

  /** ResourceNotFoundException — 404 Not Found. */
  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<Map<String, Object>> handleResourceNotFound(
      ResourceNotFoundException ex, WebRequest request) {
    Map<String, Object> body =
        createBody(
            HttpStatus.NOT_FOUND.value(),
            "Not Found",
            "resourceNotFound",
            ex.getMessage(),
            getPath(request),
            null);

    log.warn("Resource not found: {} at {} — {}", "?", getPath(request), ex.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
  }

  /**
   * NoResourceFoundException — Spring 6+ ezt dobja, ha egy request egyetlen route-hoz sem
   * illeszkedik (DispatcherServlet → ResourceHttpRequestHandler). Alapértelmezetten 404-et adna,
   * de mivel a fallback {@code @ExceptionHandler(Exception.class)} elfogja és 500-zá fordítja,
   * explicit handler kell, hogy a REST kliensek helyes 404-es státuszt kapjanak.
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<Map<String, Object>> handleNoResourceFound(
      NoResourceFoundException ex, WebRequest request) {
    String path = getPath(request);
    Map<String, Object> body =
        createBody(
            HttpStatus.NOT_FOUND.value(),
            "Not Found",
            "resourceNotFound",
            "No handler for " + path,
            path,
            null);

    log.debug("No resource found: {}", path);
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
  }

  /**
   * UnauthorizedActionException — 401 (authRequired, invalidCredentials) vagy 403 (többi). Az
   * authRequired és invalidCredentials azt jelenti, hogy a user nincs hitelesítve; minden más
   * UnauthorizedActionException kontextusfüggő jogosultsági hiba (pl. permission denied egy védett
   * endpoint-hoz).
   */
  @ExceptionHandler(UnauthorizedActionException.class)
  public ResponseEntity<Map<String, Object>> handleUnauthorized(
      UnauthorizedActionException ex, WebRequest request) {
    String messageKey = ex.getMessageKey();
    boolean isUnauthorized =
        "authRequired".equals(messageKey) || "invalidCredentials".equals(messageKey);
    HttpStatus status = isUnauthorized ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN;

    Map<String, Object> body =
        createBody(
            status.value(),
            isUnauthorized ? "Unauthorized" : "Forbidden",
            messageKey,
            ex.getMessage(),
            getPath(request),
            null);

    log.warn("Unauthorized: {} at {} — {}", messageKey, getPath(request), ex.getMessage());
    return ResponseEntity.status(status).body(body);
  }

  /** Spring Security AuthenticationException — 401 Unauthorized. */
  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<Map<String, Object>> handleAuthentication(
      AuthenticationException ex, WebRequest request) {
    HttpStatus status = HttpStatus.UNAUTHORIZED;
    String messageKey = "authenticationFailed";
    if (ex instanceof BadCredentialsException) messageKey = "invalidCredentials";
    else if (ex instanceof DisabledException) messageKey = "userDisabled";
    else if (ex instanceof LockedException) messageKey = "userLocked";

    Map<String, Object> body =
        createBody(
            status.value(),
            status.getReasonPhrase(),
            messageKey,
            ex.getMessage(),
            getPath(request),
            null);

    log.warn("Authentication failed: {} at {} — {}", messageKey, getPath(request), ex.getMessage());
    return ResponseEntity.status(status).body(body);
  }

  /** HttpMessageNotReadableException — 400 Bad Request. */
  @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadable(
      org.springframework.http.converter.HttpMessageNotReadableException ex, WebRequest request) {
    Map<String, Object> body =
        createBody(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            "validationError",
            "Malformed or missing request body",
            getPath(request),
            null);

    log.warn("Malformed JSON request at {}: {}", getPath(request), ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  /**
   * MethodArgumentNotValidException — 400 validation hibákkal.
   *
   * <p>A details tömb mezőnkénti listázza a hibákat: {field, message, rejectedValue}.
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(
      MethodArgumentNotValidException ex, WebRequest request) {
    List<Map<String, Object>> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                error -> {
                  Map<String, Object> m = new LinkedHashMap<>();
                  m.put("field", error.getField());
                  m.put("message", error.getDefaultMessage());
                  m.put("rejectedValue", error.getRejectedValue());
                  return m;
                })
            .collect(Collectors.toList());

    Map<String, Object> body =
        createBody(
            HttpStatus.BAD_REQUEST.value(),
            "Validation Failed",
            "validationError",
            "Request validation failed",
            getPath(request),
            fieldErrors);

    log.warn("Validation error at {}: {} field errors", getPath(request), fieldErrors.size());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  /**
   * MaxUploadSizeExceededException — 413 Payload Too Large. A Spring Boot a multipart feltöltési
   * limit túllépésekor dobja (10MB default, application.yml
   * spring.servlet.multipart.max-file-size).
   */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceeded(
      MaxUploadSizeExceededException ex, WebRequest request) {
    Map<String, Object> body =
        createBody(
            HttpStatus.PAYLOAD_TOO_LARGE.value(),
            "Payload Too Large",
            "fileTooLarge",
            "File size exceeds 10MB limit",
            getPath(request),
            null);

    log.warn("File too large: {} at {}", ex.getMessage(), getPath(request));
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
  }

  /** Fallback — minden más exception → 500 Internal Server Error. */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleAll(Exception ex, WebRequest request) {
    Map<String, Object> body =
        createBody(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal Server Error",
            "internalError",
            "An unexpected error occurred",
            getPath(request),
            null);

    log.error("Unexpected error at {}", getPath(request), ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }

  // ===== Helper =====

  private Map<String, Object> createBody(
      int status, String error, String messageKey, String message, String path, Object details) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("timestamp", Instant.now().toString());
    body.put("status", status);
    body.put("error", error);
    body.put("messageKey", messageKey);
    body.put("message", message);
    body.put("path", path);
    if (details != null) {
      body.put("details", details);
    }
    return body;
  }

  private String getPath(WebRequest request) {
    return request.getDescription(false).replace("uri=", "");
  }
}
