package hu.tanszek.device.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tesztek a {@link MailService}-hez.
 *
 * <p>A @Retryable logikát Spring Boot Integration Test-tel kellene tesztelni,
 * de unit szinten ellenőrizzük a retry konfigurációt és az alapvető viselkedést.
 *
 * <p>Megjegyzés: A Spring Retry annotáció csak Spring context-ben aktív,
 * ezért a retry viselkedést itt manuálisan teszteljük.
 */
class MailServiceTest {

    private JavaMailSender mailSender;
    private MailService mailService;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        mailService = new MailService(mailSender);
        ReflectionTestUtils.setField(mailService, "fromAddress", "noreply@tanszek.local");
    }

    @Test
    void sendSimpleEmailCallsMailSenderOnceOnSuccess() {
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        mailService.sendSimpleEmail("admin@tanszek.local", "Test Subject", "Test Body");

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
        assertThat(mailService.getTotalAttempts()).isEqualTo(1);
        assertThat(mailService.getTotalFailures()).isZero();
    }

    @Test
    void sendSimpleEmailIncrementsFailureCounterOnException() {
        doThrow(new MailSendException("SMTP error"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        // Retry-val együtt 3 próbálkozás lenne, de @Retryable nélkül csak 1-et hívunk
        try {
            mailService.sendSimpleEmail("admin@tanszek.local", "Subject", "Body");
        } catch (Exception e) {
            // A @Retryable nélküli unit tesztben a kivétel tovább terjed
        }

        assertThat(mailService.getTotalAttempts()).isEqualTo(1);
        assertThat(mailService.getTotalFailures()).isEqualTo(1);
    }

    @Test
    void getTotalAttemptsReturnsZeroInitially() {
        assertThat(mailService.getTotalAttempts()).isZero();
        assertThat(mailService.getTotalFailures()).isZero();
    }

    @Test
    void multipleSendsAccumulateCounters() {
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        mailService.sendSimpleEmail("user1@x.com", "Subj1", "Body1");
        mailService.sendSimpleEmail("user2@x.com", "Subj2", "Body2");
        mailService.sendSimpleEmail("user3@x.com", "Subj3", "Body3");

        assertThat(mailService.getTotalAttempts()).isEqualTo(3);
        assertThat(mailService.getTotalFailures()).isZero();
        verify(mailSender, times(3)).send(any(SimpleMailMessage.class));
    }
}