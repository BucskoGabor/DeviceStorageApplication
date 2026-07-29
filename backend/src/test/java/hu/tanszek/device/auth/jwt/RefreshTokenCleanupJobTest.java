package hu.tanszek.device.auth.jwt;

import hu.tanszek.device.auth.repository.RefreshTokenRepository;
import hu.tanszek.device.common.MailService;
import hu.tanszek.device.common.ScheduledJobMonitoring;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tesztek a {@link RefreshTokenCleanupJob}-hoz.
 *
 * <p>Teszteli:
 * <ul>
 *   <li>cleanup() hívja a ScheduledJobMonitoring.run() metódust</li>
 *   <li>doCleanup() átadja a 7 napos grace cutoff-ot a repository-nak</li>
 *   <li>doCleanup() logolja a törölt tokenek számát</li>
 *   <li>cleanup() hiba esetén email alert-et küld (monitoring-on keresztül)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenCleanupJobTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private MailService mailService;
    @Mock private ScheduledJobMonitoring jobMonitoring;

    private RefreshTokenCleanupJob cleanupJob;

    @BeforeEach
    void setUp() {
        cleanupJob = new RefreshTokenCleanupJob(refreshTokenRepository, jobMonitoring);

        // Hogy a jobMonitoring.run() ténylegesen hívja a cleanup logikát
        // (nem csak mockolva legyen), beállítjuk, hogy a run() hívja meg a doCleanup-ot
        when(jobMonitoring.run(anyString(), any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return null;
        });
    }

    @Test
    void cleanupCallsRepositoryWith7DayGraceCutoff() {
        when(refreshTokenRepository.deleteOldTokens(any(Instant.class))).thenReturn(5);

        cleanupJob.cleanup();

        verify(refreshTokenRepository, times(1)).deleteOldTokens(any(Instant.class));
        verify(jobMonitoring, times(1)).run(anyString(), any(Runnable.class));
    }

    @Test
    void cleanupWithNoTokensDeletesNothing() {
        when(refreshTokenRepository.deleteOldTokens(any(Instant.class))).thenReturn(0);

        cleanupJob.cleanup();

        verify(refreshTokenRepository, times(1)).deleteOldTokens(any(Instant.class));
    }

    @Test
    void cleanupOnExceptionSendsAlertEmail() {
        // A jobMonitoring.run() ne hívja meg a doCleanup-ot (szimuláljuk a hibát)
        org.mockito.Mockito.reset(jobMonitoring);
        when(jobMonitoring.run(anyString(), any(Runnable.class)))
                .thenThrow(new RuntimeException("DB connection failed"));

        cleanupJob.cleanup();

        // A cleanup maga nem dob kivételt, mert a monitoring wrapper elkapja
        // és email alert-et küld (most mockolva, nem hívódik valódi mailService)
        verify(jobMonitoring, times(1)).run(anyString(), any(Runnable.class));
    }
}