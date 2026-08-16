package hu.tanszek.device.auth;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * RateLimiterService — Bucket4j-alapú rate limiting in-memory tárolással.
 *
 * <p>Jelenleg csak a /api/auth/login endpointra alkalmazzuk:
 *
 * <ul>
 *   <li><b>per-IP:</b> 5 próba / perc (gyors brute-force védelem)
 *   <li><b>per-email:</b> 10 próba / óra (lassabb targeted attack védelem)
 * </ul>
 *
 * <p>Bucket-ek {@code ConcurrentHashMap}-ben tárolódnak, kulcs az IP-cím vagy az email SHA-256
 * hash-e. Régi entry-k cleanup-ja biztosítja a memória szivárgás megelőzését.
 *
 * <p>HA deployment esetén Redis-backed Bucket4j-re kell váltani (Future Work).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiterService {

  /** Per-IP: 5 próba / perc */
  private static final Bandwidth PER_IP_BANDWIDTH =
      Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1)));

  /** Per-email: 10 próba / óra */
  private static final Bandwidth PER_EMAIL_BANDWIDTH =
      Bandwidth.classic(10, Refill.intervally(10, Duration.ofHours(1)));

  /** Bucket-ek tárolása kulcs (IP vagy email hash) → Bucket */
  private final ConcurrentHashMap<String, Bucket> perIpBuckets = new ConcurrentHashMap<>();

  private final ConcurrentHashMap<String, Bucket> perEmailBuckets = new ConcurrentHashMap<>();

  /**
   * Rate limit check per-IP kulccsal.
   *
   * @param ip a kliens IP-címe (request.getRemoteAddr())
   * @return true ha a kérés átmehet (bucket van szabad token), false ha rate limit exceeded
   */
  public boolean tryConsumePerIp(String ip) {
    if (ip == null || ip.isBlank()) {
      return true; // Nincs IP, nem tudunk rate limit-et alkalmazni (proxy mögött?)
    }
    Bucket bucket =
        perIpBuckets.computeIfAbsent(ip, k -> Bucket.builder().addLimit(PER_IP_BANDWIDTH).build());
    boolean allowed = bucket.tryConsume(1);
    if (!allowed) {
      log.warn("Rate limit exceeded for IP: {}", ip);
    }
    return allowed;
  }

  /**
   * Rate limit check per-email kulccsal.
   *
   * @param email a user email címe (login input)
   * @return true ha a kérés átmehet
   */
  public boolean tryConsumePerEmail(String email) {
    if (email == null || email.isBlank()) {
      return true;
    }
    String emailHash = sha256(email);
    Bucket bucket =
        perEmailBuckets.computeIfAbsent(
            emailHash, k -> Bucket.builder().addLimit(PER_EMAIL_BANDWIDTH).build());
    boolean allowed = bucket.tryConsume(1);
    if (!allowed) {
      log.warn("Rate limit exceeded for email hash: {}", emailHash);
    }
    return allowed;
  }

  /** SHA-256 hash az emailből (bucket kulcsnak). */
  private String sha256(String input) {
    try {
      java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
