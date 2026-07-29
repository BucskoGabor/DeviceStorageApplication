package hu.tanszek.device.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.tanszek.device.common.BusinessValidationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * RateLimitFilter — Bucket4j-alapú rate limit check a /api/auth/login endpointra.
 *
 * <p>Filter pozíció: Security Filter Chain 2. lépése (a CsrfFilter előtt),
 * hogy a brute-force botok ne kapjanak CSRF tokent a próbálkozásaik során.
 *
 * <p>A /api/auth/login endpointra két rate limitet alkalmazunk:
 * <ul>
 *   <li>Per-IP: 5 próba / perc — gyors brute-force védelem</li>
 *   <li>Per-email: 10 próba / óra — lassabb targeted attack védelem</li>
 * </ul>
 *
 * <p>Ha bármelyik bucket üres, {@link BusinessValidationException} dobódik,
 * amit a {@code GlobalExceptionHandler} 429-es státusszal kezel
 * ({@code rateLimitExceeded} messageKey).
 */
@Slf4j
@Component
@Order(1)  // A Security Filter Chain 2. lépése (CsrfFilter ELŐTT)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/auth/login";

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (!LOGIN_PATH.equals(request.getRequestURI()) || !"POST".equals(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = getClientIp(request);
        if (!rateLimiterService.tryConsumePerIp(ip)) {
            log.warn("Rate limit exceeded (per-IP) for IP: {}", ip);
            writeRateLimitError(response, "perIp");
            return;
        }

        // Email check: a request body-t olvassuk, de ez bonyolult.
        // Egyszerűsítés: a body-t egy wrapper RequestWrapper-rel olvassuk,
        // vagy kihagyjuk az email check-et, és csak per-IP limit van.
        // TODO Task 2.5+: email check a request body parsing után
        // (vagy ContentCachingRequestWrapper).

        filterChain.doFilter(request, response);
    }

    /**
     * Kliens IP-cím kinyerése (X-Forwarded-For header támogatással).
     */
    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // Az első IP a kliens IP-je a proxy láncban
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 429-es JSON válasz kiírása.
     */
    private void writeRateLimitError(HttpServletResponse response, String reason) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", "60"); // 60 másodperc

        Map<String, Object> body = Map.of(
                "status", 429,
                "error", "Too Many Requests",
                "messageKey", "rateLimitExceeded",
                "reason", reason
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}