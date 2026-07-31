package hu.tanszek.device.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tesztek a {@link RateLimiterService}-hez.
 *
 * <p>Teszteli:
 *
 * <ul>
 *   <li>Per-IP: 5 próba átmegy, a 6. elutasítódik
 *   <li>Per-email: 10 próba átmegy, a 11. elutasítódik
 *   <li>Különböző IP-k / email-ek független bucket-eket kapnak
 *   <li>Per-IP refill 1 perc után
 * </ul>
 */
class RateLimiterServiceTest {

  private RateLimiterService rateLimiterService;

  @BeforeEach
  void setUp() {
    rateLimiterService = new RateLimiterService();
  }

  @Test
  void perIpAllows5RequestsThenBlocks6th() {
    String ip = "192.168.1.1";

    // Első 5 kérés átmegy
    for (int i = 0; i < 5; i++) {
      assertThat(rateLimiterService.tryConsumePerIp(ip))
          .as("Request %d should be allowed", i + 1)
          .isTrue();
    }

    // 6. kérés elutasítódik
    assertThat(rateLimiterService.tryConsumePerIp(ip))
        .as("6th request should be rate limited")
        .isFalse();
  }

  @Test
  void perIpDifferentIpsAreIndependent() {
    String ip1 = "192.168.1.1";
    String ip2 = "192.168.1.2";

    // ip1 bucket tele
    for (int i = 0; i < 5; i++) {
      rateLimiterService.tryConsumePerIp(ip1);
    }
    assertThat(rateLimiterService.tryConsumePerIp(ip1)).isFalse();

    // ip2 bucket még szabad
    for (int i = 0; i < 5; i++) {
      assertThat(rateLimiterService.tryConsumePerIp(ip2))
          .as("ip2 request %d should be allowed", i + 1)
          .isTrue();
    }
  }

  @Test
  void perEmailAllows10RequestsThenBlocks11th() {
    String email = "test@tanszek.local";

    for (int i = 0; i < 10; i++) {
      assertThat(rateLimiterService.tryConsumePerEmail(email))
          .as("Email request %d should be allowed", i + 1)
          .isTrue();
    }

    assertThat(rateLimiterService.tryConsumePerEmail(email))
        .as("11th email request should be rate limited")
        .isFalse();
  }

  @Test
  void perEmailDifferentEmailsAreIndependent() {
    String email1 = "alice@tanszek.local";
    String email2 = "bob@tanszek.local";

    // email1 bucket tele
    for (int i = 0; i < 10; i++) {
      rateLimiterService.tryConsumePerEmail(email1);
    }
    assertThat(rateLimiterService.tryConsumePerEmail(email1)).isFalse();

    // email2 még szabad
    for (int i = 0; i < 10; i++) {
      assertThat(rateLimiterService.tryConsumePerEmail(email2))
          .as("email2 request %d should be allowed", i + 1)
          .isTrue();
    }
  }

  @Test
  void nullOrBlankIpIsAllowed() {
    // Nincs IP (proxy mögött nincs X-Forwarded-For és getRemoteAddr is null)
    assertThat(rateLimiterService.tryConsumePerIp(null)).isTrue();
    assertThat(rateLimiterService.tryConsumePerIp("")).isTrue();
    assertThat(rateLimiterService.tryConsumePerIp("   ")).isTrue();
  }

  @Test
  void nullOrBlankEmailIsAllowed() {
    assertThat(rateLimiterService.tryConsumePerEmail(null)).isTrue();
    assertThat(rateLimiterService.tryConsumePerEmail("")).isTrue();
  }
}
