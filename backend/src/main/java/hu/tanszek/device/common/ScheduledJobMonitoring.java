package hu.tanszek.device.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled job monitoring wrapper — email alert küldése ha a job fail-el.
 *
 * <p>Minden {@code @Scheduled} metódus ezen a wrapper-en fut, hogy hiba esetén emailt küldjön az
 * {@code ALERT_EMAIL_RECIPIENT}-nek.
 *
 * <p>Használat:
 *
 * <pre>
 *   scheduledJobMonitoring.run("refresh-token-cleanup", () -> {
 *       // cleanup logic
 *   });
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledJobMonitoring {

  private final MailService mailService;

  @Value("${audit.alert-email-recipient:admin@tanszek.local}")
  private String alertEmailRecipient;

  /**
   * Scheduled job futtatása monitoring wrapper-rel.
   *
   * @param jobName a job neve (hiba alert subject-jében jelenik meg)
   * @param runnable a job logikája
   */
  public void run(String jobName, Runnable runnable) {
    log.info("Starting scheduled job: {}", jobName);
    try {
      runnable.run();
      log.info("Scheduled job completed successfully: {}", jobName);
    } catch (Exception e) {
      log.error("Scheduled job FAILED: {}", jobName, e);
      sendAlertEmail(jobName, e);
    }
  }

  /** Alert email küldése hiba esetén. */
  private void sendAlertEmail(String jobName, Exception e) {
    String subject = String.format("[ALERT] Scheduled job failed: %s", jobName);
    String body =
        String.format(
            "The following scheduled job failed:\n\n"
                + "Job: %s\n"
                + "Error: %s\n"
                + "Message: %s\n\n"
                + "Check the application logs for the full stacktrace.\n",
            jobName, e.getClass().getName(), e.getMessage());

    mailService.sendSimpleEmail(alertEmailRecipient, subject, body);
  }
}
