package hu.tanszek.device.auth;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.scheduling.annotation.Scheduled;
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
 *   <li><b>email:</b> 10 próba / óra (lassabb targeted attack védelem)
 * </ul>
 *
 * <p>Bucket-ek {@code ConcurrentHashMap}-ben tárolódnak, kulcs az IP-cím vagy az email SHA-256
 * hash-e. A map entry-k egy {@link TimestampedBucket} wrapperben tartják az utolsó használat
 * idejét, és egy {@code @Scheduled} cleanup metódus 5 percenként törli a 30 perce nem használt
 * entry-ket — így nincs memória szivárgás hosszú futásidejű deploymentnél.
 *
 * <p>Null/blank IP vagy email esetén a kérés ELUTASÍTÁSRA kerül (return {@code false}) — korábban
 * átengedte, ami brute-force-hoz vezethetett. Az email blank mezőt a {@code RateLimitFilter} előtt
 * a login DTO {@code @NotBlank} validációjának kell elkapnia, de a service-szintű védelem védelmet
 * nyújt közvetlen hívás ellen is.
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

  /**
   * Cleanup threshold: 30 perc inaktivitás után töröljük a bucket entry-t. Literál szorzat, mert a
   * {@code @Scheduled} annotáció csak compile-time constanst fogad el — a {@code
   * Duration.ofMinutes(30).toMillis()} metódushívás nem az.
   */
  private static final long IDLE_EVICTION_MS = 30L * 60L * 1000L;

  /**
   * Cleanup futás gyakorisága: 5 perc. Szintén literál, hogy a {@code @Scheduled} annotáció
   * elfogadja.
   */
  private static final long CLEANUP_INTERVAL_MS = 5L * 60L * 1000L;

  /** Bucket wrapper az utolsó használat idejével — a cleanup ezt használja. */
  private static final class TimestampedBucket {
    final Bucket bucket;
    final AtomicLong lastAccessAt;

    TimestampedBucket(Bucket bucket, long now) {
      this.bucket = bucket;
      this.lastAccessAt = new AtomicLong(now);
    }

    void touch(long now) {
      lastAccessAt.set(now);
    }
  }

  private final ConcurrentHashMap<String, TimestampedBucket> perIpBuckets =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, TimestampedBucket> perEmailBuckets =
      new ConcurrentHashMap<>();

  /**
   * Rate limit check per-IP kulccsal.
   *
   * @param ip a kliens IP-címe (request.getRemoteAddr())
   * @return true ha a kérés átmehet (bucket van szabad token), false ha rate limit exceeded vagy az
   *     IP hiányzik/blank.
   */
  public boolean tryConsumePerIp(String ip) {
    if (ip == null || ip.isBlank()) {
      // Korábban átengedtük — most ELUTASÍTJUK, mert a rate limit megkerüléséhez
      // vezetett (proxy mögötti X-Forwarded-For nélküli kérések korlátlanok voltak).
      log.warn("Rate limit check with null/blank IP — denying request");
      return false;
    }
    final long now = System.currentTimeMillis();
    TimestampedBucket entry =
        perIpBuckets.computeIfAbsent(
            ip,
            k -> new TimestampedBucket(Bucket.builder().addLimit(PER_IP_BANDWIDTH).build(), now));
    entry.touch(now);
    boolean allowed = entry.bucket.tryConsume(1);
    if (!allowed) {
      log.warn("Rate limit exceeded for IP: {}", ip);
    }
    return allowed;
  }

  /**
   * Rate limit check per-email kulccsal.
   *
   * @param email a user email címe (login input)
   * @return true ha a kérés átmehet, false ha rate limit exceeded vagy email hiányzik
   */
  public boolean tryConsumePerEmail(String email) {
    if (email == null || email.isBlank()) {
      log.warn("Rate limit check with null/blank email — denying request");
      return false;
    }
    String emailHash = sha256(email);
    final long now = System.currentTimeMillis();
    TimestampedBucket entry =
        perEmailBuckets.computeIfAbsent(
            emailHash,
            k ->
                new TimestampedBucket(Bucket.builder().addLimit(PER_EMAIL_BANDWIDTH).build(), now));
    entry.touch(now);
    boolean allowed = entry.bucket.tryConsume(1);
    if (!allowed) {
      log.warn("Rate limit exceeded for email hash: {}", emailHash);
    }
    return allowed;
  }

  /** Periodikus cleanup — törli az IDLE_EVICTION_MS-nél régebben használt bucket entry-ket. */
  @Scheduled(fixedRate = CLEANUP_INTERVAL_MS)
  public void evictIdleBuckets() {
    final long cutoff = System.currentTimeMillis() - IDLE_EVICTION_MS;
    int evicted = evictIdleFrom(perIpBuckets, cutoff);
    int evictedEmail = evictIdleFrom(perEmailBuckets, cutoff);
    if (evicted + evictedEmail > 0) {
      log.info("RateLimiter cleanup: {} IP + {} email idle buckets evicted", evicted, evictedEmail);
    }
  }

  private int evictIdleFrom(ConcurrentHashMap<String, TimestampedBucket> map, long cutoff) {
    int n = 0;
    Iterator<Map.Entry<String, TimestampedBucket>> it = map.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String, TimestampedBucket> e = it.next();
      if (e.getValue().lastAccessAt.get() < cutoff) {
        it.remove();
        n++;
      }
    }
    return n;
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
