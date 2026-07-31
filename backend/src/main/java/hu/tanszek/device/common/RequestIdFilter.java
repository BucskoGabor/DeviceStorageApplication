package hu.tanszek.device.common;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * RequestIdFilter — request_id MDC-be rakása minden request-en.
 *
 * <p>Ha a kliens küld X-Request-ID headert (pl. distributed tracing-hez), elfogadjuk. Egyébként
 * UUID-t generálunk. A request_id a response X-Request-ID headerben visszaadódik, hogy a kliens is
 * lássa (debug, support ticket-ek).
 *
 * <p>MDC (Mapped Diagnostic Context) propagálódik a structured JSON log-okba (lásd
 * logback-spring.xml): minden log bejegyzés tartalmazza a request_id-t.
 *
 * <p>Filter pozíció: Security Filter Chain legeleje (RateLimitFilter előtt), hogy a request_id
 * minden log-ban megjelenjen, még a security check előtt.
 */
@Component
@Order(0) // A Security Filter Chain legeleje
public class RequestIdFilter extends OncePerRequestFilter {

  public static final String REQUEST_ID_HEADER = "X-Request-ID";
  public static final String MDC_REQUEST_ID_KEY = "request_id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String requestId = extractOrGenerateRequestId(request);

    try {
      MDC.put(MDC_REQUEST_ID_KEY, requestId);
      response.setHeader(REQUEST_ID_HEADER, requestId);
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_REQUEST_ID_KEY);
    }
  }

  /** Kliens X-Request-ID headere vagy új UUID generálása. */
  private String extractOrGenerateRequestId(HttpServletRequest request) {
    String header = request.getHeader(REQUEST_ID_HEADER);
    if (header != null && !header.isBlank() && isValidRequestId(header)) {
      return header;
    }
    return UUID.randomUUID().toString();
  }

  /**
   * X-Request-ID validáció — max 64 karakter, csak alfanumerikus + dash.
   *
   * <p>Megelőzi a log injection-t (ha a kliens rosszindulatú headert küld).
   */
  private boolean isValidRequestId(String header) {
    return header.length() <= 64 && header.matches("[a-zA-Z0-9-]+");
  }
}
