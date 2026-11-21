package com.noginx.notification;

import com.noginx.watcher.NginxLogWatcher;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;

@Service
public class SystemNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SystemNotificationService.class);

    private final boolean enabled;

    public SystemNotificationService(
            @Value("${noginx.notifications.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    @PostConstruct
    void initialize() {
        if (enabled && !SystemTray.isSupported()) {
            log.info("Desktop system notifications are unavailable on this platform");
        }
    }

    public void send(String title, String message) {
        if (!enabled) {
            return;
        }
        if (!SystemTray.isSupported()) {
            log.info("Desktop notification skipped: SystemTray is unavailable");
            return;
        }

        try {
            SystemTray tray = SystemTray.getSystemTray();
            TrayIcon trayIcon = new TrayIcon(createIcon(), "noginx");
            trayIcon.setToolTip("noginx");
            trayIcon.setImageAutoSize(true);
            tray.add(trayIcon);
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.WARNING);

            EventQueue.invokeLater(() -> {
                try {
                    tray.remove(trayIcon);
                } catch (Exception e) {
                    log.debug("Unable to remove temporary tray icon", e);
                }
            });
        } catch (HeadlessException e) {
            log.info("Desktop notification skipped in headless environment");
        } catch (AWTException | SecurityException e) {
            log.warn("Desktop notification failed: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Desktop notification failed: {}", e.getMessage());
        }
    }

    private Image createIcon() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillOval(1, 1, 14, 14);
        graphics.setColor(Color.WHITE);
        graphics.drawString("!", 6, 12);
        graphics.dispose();
        return image;
    }
}
