package com.noginx.notification;

import com.noginx.email.EmailAlertService;
import com.noginx.watcher.NginxLogWatcher;
import org.springframework.stereotype.Service;

@Service
public class AlertingService {

    private final SystemNotificationService notificationService;
    private final EmailAlertService emailAlertService;

    public AlertingService(
            SystemNotificationService notificationService,
            EmailAlertService emailAlertService) {
        this.notificationService = notificationService;
        this.emailAlertService = emailAlertService;
    }

    public void sendAnomalyAlert(NginxLogWatcher.WatcherPrediction prediction) {
        String body = "🚨 Anomaly Detected!\n\n"
                + "IP Address: " + prediction.entry().ip() + "\n"
                + "Timestamp: " + prediction.entry().timestamp() + "\n"
                + "Path: " + prediction.entry().getPath() + "\n"
                + "User Agent: " + prediction.entry().userAgent();

        notificationService.send("🚨 Anomaly Alert!", body);
        emailAlertService.send("🚨 noginx Anomaly Detected", body);
    }
}
