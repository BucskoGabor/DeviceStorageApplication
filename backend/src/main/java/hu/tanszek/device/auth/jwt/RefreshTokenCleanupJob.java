package hu.tanszek.device.auth.jwt;

import hu.tanszek.device.auth.repository.RefreshTokenRepository;
import hu.tanszek.device.common.ScheduledJobMonitoring;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Refresh token cleanup scheduled job — napi 04:00-kor fut.
 *
 * <p>Törli a 7+ napos lejárt vagy revoked tokeneket. A grace period
 * debug célokat szolgál — ha egy user logoutolt, a tokenje 7 napig
 * megmarad a DB-ben, hogy az esetleges audit log-okban trace-elhető legyen.
 *
 * <p>Ütemezés: {@code cron = "0 0 4 * * *"} (minden nap 04:00-kor, UTC).
 *
 * <p>Hiba esetén email alert küldése a {@link ScheduledJobMonitoring}-on
 * keresztül az {@code ALERT_EMAIL_RECIPIENT}-nek.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    /** Grace period a revoked token-ek törléséhez (debug célokra) */
    private static final Duration GRACE_PERIOD = Duration.ofDays(7);

    private final RefreshTokenRepository refreshTokenRepository;
    private final ScheduledJobMonitoring jobMonitoring;

    /**
     * Napi 04:00-kor futó cleanup.
     *
     * <p>Törli:
     * <ul>
     *   <li>Lejárt tokenek ({@code expires_at < now}) — azonnal</li>
     *   <li>Revoked tokenek ({@code revoked = true AND created_at < now - 7 days}) — grace után</li>
     * </ul>
     */
    @Scheduled(cron = "0 0 4 * * *")
    public void cleanup() {
        jobMonitoring.run("refresh-token-cleanup", this::doCleanup);
    }

    /**
     * A tényleges cleanup logika (a monitoring wrapper hívja).
     */
    private void doCleanup() {
        Instant now = Instant.now();
        Instant graceCutoff = now.minus(GRACE_PERIOD);

        int deletedCount = refreshTokenRepository.deleteOldTokens(graceCutoff);

        if (deletedCount > 0) {
            log.info("Refresh token cleanup deleted {} tokens older than {}", deletedCount, graceCutoff);
        } else {
            log.debug("Refresh token cleanup found no expired/revoked tokens to delete");
        }
    }
}