package hu.tanszek.device.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Központi email küldő service — rendszer notification-ök (alert-ek, audit log export, stb.).
 *
 * <p>Jelenleg a {@code ScheduledJobMonitoring} használja hiba alert-ekre
 * (Task 2.10 mail config + Spring Retry 3 attempt exponential backoff).
 *
 * <p>A @Async használatával a küldés nem blockolja a job végrehajtását —
 * ha a SMTP szerver lassú, a cleanup job akkor is tovább fut.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@tanszek.local}")
    private String fromAddress;

    /**
     * Egyszerű szöveges email küldése.
     *
     * <p>A {@link Async} miatt a hívó nem várja meg a küldést — a Spring külön
     * thread-en futtatja.
     *
     * @param to címzett (pl. admin@tanszek.local)
     * @param subject email tárgya
     * @param body email szövege
     */
    @Async
    public void sendSimpleEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email sent: to={}, subject={}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to={}, subject={}", to, subject, e);
            // Ne dobjon kivételt — a @Async miatt a caller nem látja,
            // de log.error rögzíti a hibát monitoring célokra.
        }
    }
}