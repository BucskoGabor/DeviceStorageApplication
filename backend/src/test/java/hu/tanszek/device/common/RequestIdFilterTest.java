package hu.tanszek.device.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import jakarta.servlet.FilterChain;

/**
 * Unit tesztek a {@link RequestIdFilter}-hez.
 *
 * <p>Teszteli:
 *
 * <ul>
 *   <li>Generált UUID ha nincs X-Request-ID header
 *   <li>Client X-Request-ID elfogadása ha valid
 *   <li>Invalid X-Request-ID elutasítása (security: log injection védelem)
 *   <li>MDC törlése a filter után (finally block)
 * </ul>
 */
class RequestIdFilterTest {

  private final RequestIdFilter filter = new RequestIdFilter();

  @AfterEach
  void cleanup() {
    MDC.clear();
  }

  @Test
  void generatesUuidWhenClientHeaderMissing() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    String requestId = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
    assertThat(requestId).isNotNull();
    // UUID formátum: 36 karakter, kötőjelekkel
    assertThat(requestId).hasSize(36);
    assertThat(requestId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
  }

  @Test
  void acceptsValidClientRequestId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "client-trace-12345");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER))
        .isEqualTo("client-trace-12345");
  }

  @Test
  void rejectsInvalidClientRequestId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    // Log injection attempt: contains special characters
    request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "<script>alert('xss')</script>");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    // Should generate a new UUID instead of accepting the malicious input
    String requestId = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
    assertThat(requestId).isNotEqualTo("<script>alert('xss')</script>");
    assertThat(requestId).hasSize(36); // UUID
  }

  @Test
  void rejectsRequestIdLongerThan64Chars() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "a".repeat(65));
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    // Should generate a new UUID
    String requestId = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
    assertThat(requestId).hasSize(36);
  }

  @Test
  void mdcClearedAfterFilter() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    // A filter után az MDC-ből törölve kell legyen
    assertThat(MDC.get(RequestIdFilter.MDC_REQUEST_ID_KEY)).isNull();
  }

  @Test
  void emptyHeaderGeneratesNewUuid() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    String requestId = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
    assertThat(requestId).hasSize(36); // UUID, nem üres
  }
}
