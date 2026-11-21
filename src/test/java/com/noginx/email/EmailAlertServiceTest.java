package com.noginx.email;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailAlertServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Test
    void sendsUtf8MimeAlertWhenConfigured() throws Exception {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        EmailAlertService service = new EmailAlertService(
                mailSender, "sender@example.com", "app-password",
                "receiver@example.com", true);

        service.send("🚨 noginx Anomaly Detected",
                "🚨 Anomaly Detected!\n\nIP Address: 127.0.0.1");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void skipsEmailWhenCredentialsAreMissing() {
        EmailAlertService service = new EmailAlertService(
                mailSender, "", "", "", true);

        service.send("subject", "body");

        verifyNoInteractions(mailSender);
    }

    @Test
    void emailCanBeDisabled() {
        EmailAlertService service = new EmailAlertService(
                mailSender, "sender@example.com", "password",
                "receiver@example.com", false);

        service.send("subject", "body");

        verifyNoInteractions(mailSender);
    }
}
