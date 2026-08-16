package hu.tanszek.device.common;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Központi email küldő service — rendszer notification-ök (alert-ek, audit log export, stb.).
 *
 * <p>A {@code ScheduledJobMonitoring} használja hiba alert-ekre (mail config + Spring
 * Retry 3 attempt exponential backoff).
 *
 * <p>A {@code @Async} használatával a küldés nem blockolja a job végrehajtását — ha a SMTP szerver
 * lassú, a cleanup job akkor is tovább fut.
 *
 * <p>A {@code @Retryable} 3 attempt exponential backoff (1000ms → 2000ms → 4000ms). Ha mind a 3
 * próbálkozás sikertelen, log.error + NEM dob kivételt (a notification elveszhet, de a fő flow nem
 * áll le).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

  private final JavaMailSender mailSender;

  @Value("${spring.mail.username:noreply@tanszek.local}")
  private String fromAddress;

  /** Küldési kísérletek számlálója (debug / monitoring célokra). */
  private final AtomicInteger totalAttempts = new AtomicInteger(0);

  private final AtomicInteger totalFailures = new AtomicInteger(0);

  /**
   * Egyszerű szöveges email küldése @Retryable retry logikával.
   *
   * <p>3 próbálkozás exponential backoff-fal (1s, 2s, 4s várakozás).
   *
   * <p>A retry kivételek: minden {@code Exception} (beleértve a {@code
   * org.springframework.mail.MailException}-t és az I/O hibákat). Ha mind a 3 próbálkozás
   * sikertelen, log.error + nem dob kivételt.
   *
   * @param to címzett
   * @param subject email tárgya
   * @param body email szövege
   */
  @Async
  @Retryable(
      retryFor = Exception.class,
      maxAttemptsExpression = "${mail.retry.max-attempts:3}",
      backoff =
          @Backoff(
              delayExpression = "${mail.retry.initial-delay:1000}",
              multiplierExpression = "${mail.retry.multiplier:2.0}",
              maxDelayExpression = "${mail.retry.max-delay:10000}"))
  public void sendSimpleEmail(String to, String subject, String body) {
    totalAttempts.incrementAndGet();
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(fromAddress);
      message.setTo(to);
      message.setSubject(subject);
      message.setText(body);
      mailSender.send(message);
      log.info("Email sent: to={}, subject={}", to, subject);
    } catch (Exception e) {
      totalFailures.incrementAndGet();
      log.warn("Failed to send email (attempt will be retried): to={}, subject={}", to, subject, e);
      throw e; // A Spring Retry elkapja és újrapróbálkozik
    }
  }

  /** Küldési statisztikák (monitoring endpoint-hoz / health check-hez). */
  public int getTotalAttempts() {
    return totalAttempts.get();
  }

  public int getTotalFailures() {
    return totalFailures.get();
  }
}
