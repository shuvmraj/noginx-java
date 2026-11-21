package com.noginx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * NoGinx – Real-Time NGINX Anomaly Detection and Alert System
 *
 * <p>Java migration of the original Python project (https://github.com/AbhinavXJ/noginx).
 * Preserves 100% functional equivalence with the original implementation.
 *
 * <p>This application provides:
 * <ul>
 *   <li>POST /predict  – batch anomaly prediction for multiple NGINX log entries</li>
 *   <li>POST /predict_one – single-entry anomaly prediction</li>
 *   <li>GET /  – health check endpoint</li>
 *   <li>Real-time NGINX log watcher (started as a background thread)</li>
 *   <li>Desktop notifications on anomaly detection</li>
 *   <li>Email alerts on anomaly detection (Gmail SMTP)</li>
 * </ul>
 *
 * <p>Required environment variables (or .env file):
 * <ul>
 *   <li>PREDICT_ONE_URL – URL of the /predict_one endpoint (default: http://127.0.0.1:8000/predict_one)</li>
 *   <li>NOGINX_MAIL_ID – Gmail address to send alerts from</li>
 *   <li>NOGINX_MAIL_PASSWORD – Gmail App Password</li>
 *   <li>RECEIVER_MAIL_ID – Email address to receive alerts</li>
 * </ul>
 */
@SpringBootApplication
public class NoGinxApplication {

    private static final Logger log = LoggerFactory.getLogger(NoGinxApplication.class);

    public static void main(String[] args) {
        log.info("Starting NoGinx – Real-Time NGINX Anomaly Detection System");
        ConfigurableApplicationContext ctx = SpringApplication.run(NoGinxApplication.class, args);
        log.info("NoGinx server started successfully on port {}",
                ctx.getEnvironment().getProperty("server.port", "8000"));
        log.info("Endpoints available:");
        log.info("  GET  /");
        log.info("  POST /predict");
        log.info("  POST /predict_one");
    }
}
