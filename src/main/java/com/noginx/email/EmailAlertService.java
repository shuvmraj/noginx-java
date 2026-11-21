package com.noginx.email;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailAlertService {

    private static final Logger log = LoggerFactory.getLogger(EmailAlertService.class);

    private final JavaMailSender mailSender;
    private final String sender;
    private final String password;
    private final String receiver;
    private final boolean enabled;

    public EmailAlertService(
            JavaMailSender mailSender,
            @Value("${noginx.mail-id:${NOGINX_MAIL_ID:}}") String sender,
            @Value("${noginx.mail-password:${NOGINX_MAIL_PASSWORD:}}") String password,
            @Value("${noginx.receiver-mail-id:${RECEIVER_MAIL_ID:}}") String receiver,
            @Value("${noginx.email.enabled:true}") boolean enabled) {
        this.mailSender = mailSender;
        this.sender = sender;
        this.password = password;
        this.receiver = receiver;
        this.enabled = enabled;
    }

    public void send(String subject, String body) {
        if (!enabled) {
            return;
        }
        if (sender == null || sender.isBlank()
                || password == null || password.isBlank()
                || receiver == null || receiver.isBlank()) {
            log.info("Email alert skipped: NOGINX_MAIL_ID, NOGINX_MAIL_PASSWORD, "
                    + "and RECEIVER_MAIL_ID must be configured");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(sender);
            helper.setTo(receiver);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
            log.info("Email anomaly alert sent to {}", receiver);
        } catch (Exception e) {
            log.warn("Email alert failed: {}", e.getMessage());
        }
    }
}
