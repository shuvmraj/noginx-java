package com.noginx.notification;

import com.noginx.email.EmailAlertService;
import com.noginx.model.FeatureRecord;
import com.noginx.model.ParsedLogEntry;
import com.noginx.watcher.NginxLogWatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AlertingServiceTest {

    @Mock
    private SystemNotificationService notificationService;

    @Mock
    private EmailAlertService emailAlertService;

    @Test
    void sendsOriginalAnomalyBodyToBothAlertChannels() {
        AlertingService service = new AlertingService(notificationService, emailAlertService);
        ParsedLogEntry entry = new ParsedLogEntry(
                "127.0.0.1",
                "21/Nov/2026:08:30:00 +0530",
                "GET",
                "/downloads/product_1?item=1",
                "HTTP/1.1",
                304,
                0,
                "Mozilla/5.0");
        FeatureRecord feature = new FeatureRecord(304, 0, 1, 0, 54, 8);
        NginxLogWatcher.WatcherPrediction prediction =
                new NginxLogWatcher.WatcherPrediction(entry, feature, -1);

        service.sendAnomalyAlert(prediction);

        verify(notificationService).send(
                "🚨 Anomaly Alert!",
                "🚨 Anomaly Detected!\n\n"
                        + "IP Address: 127.0.0.1\n"
                        + "Timestamp: 21/Nov/2026:08:30:00 +0530\n"
                        + "Path: /downloads/product_1\n"
                        + "User Agent: Mozilla/5.0");
        verify(emailAlertService).send(
                "🚨 noginx Anomaly Detected",
                "🚨 Anomaly Detected!\n\n"
                        + "IP Address: 127.0.0.1\n"
                        + "Timestamp: 21/Nov/2026:08:30:00 +0530\n"
                        + "Path: /downloads/product_1\n"
                        + "User Agent: Mozilla/5.0");
    }
}
