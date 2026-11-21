package com.noginx.watcher;

import com.noginx.model.FeatureRecord;
import com.noginx.model.ParsedLogEntry;
import com.noginx.notification.AlertingService;
import com.noginx.parser.NginxLogParser;
import com.noginx.preprocessing.FeatureExtractor;
import com.noginx.service.PredictionService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Component
public class NginxLogWatcher {

    private static final Logger log = LoggerFactory.getLogger(NginxLogWatcher.class);

    private final NginxLogParser parser;
    private final FeatureExtractor featureExtractor;
    private final PredictionService predictionService;
    private final AlertingService alertingService;
    private final Path logPath;
    private final long pollIntervalMs;
    private final boolean enabled;

    private final Object lifecycleLock = new Object();
    private volatile ScheduledExecutorService executor;
    private volatile WatchService watchService;
    private volatile FileChannel channel;
    private volatile Object fileKey;
    private volatile long filePosition;
    private volatile byte[] pendingBytes = new byte[0];
    private volatile boolean awaitingRecreation;
    private final java.util.concurrent.atomic.AtomicInteger processedLineCount =
            new java.util.concurrent.atomic.AtomicInteger();
    private volatile Consumer<WatcherPrediction> listener = prediction ->
            log.info("Anomaly detected for IP={}, timestamp={}, path={}, user_agent={}",
                    prediction.entry().ip(), prediction.entry().timestamp(),
                    prediction.entry().getPath(), prediction.entry().userAgent());

    public NginxLogWatcher(
            NginxLogParser parser,
            FeatureExtractor featureExtractor,
            PredictionService predictionService,
            @Value("${noginx.watcher.log-path:${noginx.log-file:/var/log/nginx/access.log}}") String logPath,
            @Value("${noginx.watcher.poll-interval-ms:1000}") long pollIntervalMs,
            @Value("${noginx.watcher.enabled:true}") boolean enabled) {
        this.parser = parser;
        this.featureExtractor = featureExtractor;
        this.predictionService = predictionService;
        this.alertingService = null;
        this.logPath = Path.of(logPath).toAbsolutePath().normalize();
        this.pollIntervalMs = Math.max(100L, pollIntervalMs);
        this.enabled = enabled;
    }

    @Autowired
    public NginxLogWatcher(
            NginxLogParser parser,
            FeatureExtractor featureExtractor,
            PredictionService predictionService,
            AlertingService alertingService,
            @Value("${noginx.watcher.log-path:${noginx.log-file:/var/log/nginx/access.log}}") String logPath,
            @Value("${noginx.watcher.poll-interval-ms:1000}") long pollIntervalMs,
            @Value("${noginx.watcher.enabled:true}") boolean enabled) {
        this.parser = parser;
        this.featureExtractor = featureExtractor;
        this.predictionService = predictionService;
        this.alertingService = alertingService;
        this.logPath = Path.of(logPath).toAbsolutePath().normalize();
        this.pollIntervalMs = Math.max(100L, pollIntervalMs);
        this.enabled = enabled;
    }

    @PostConstruct
    public void start() {
        if (!enabled) {
            log.info("NGINX log watcher is disabled");
            return;
        }

        if (alertingService != null) {
            setAnomalyListener(alertingService::sendAnomalyAlert);
        }

        synchronized (lifecycleLock) {
            if (executor != null && !executor.isShutdown()) {
                return;
            }

            try {
                watchService = FileSystems.getDefault().newWatchService();
            } catch (IOException e) {
                log.warn("Unable to create WatchService; continuing with bounded polling: {}", e.getMessage());
                watchService = null;
            }

            executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "noginx-log-watcher");
                thread.setDaemon(true);
                return thread;
            });
            try {
                processAppendedBytes();
            } catch (IOException | SecurityException e) {
                log.warn("Unable to initialize NGINX log tail for {}: {}", logPath, e.getMessage());
            }
            executor.scheduleWithFixedDelay(this::poll, 0, pollIntervalMs, TimeUnit.MILLISECONDS);
            log.info("NGINX log watcher started for {}", logPath);
        }
    }

    @PreDestroy
    public void stop() {
        synchronized (lifecycleLock) {
            if (executor != null) {
                executor.shutdownNow();
                try {
                    executor.awaitTermination(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                executor = null;
            }
            closeChannel();
            if (watchService != null) {
                try {
                    watchService.close();
                } catch (IOException e) {
                    log.debug("Error closing WatchService", e);
                }
                watchService = null;
            }
            pendingBytes = new byte[0];
            log.info("NGINX log watcher stopped");
        }
    }

    public void setAnomalyListener(Consumer<WatcherPrediction> listener) {
        this.listener = listener == null
                ? prediction -> { }
                : listener;
    }

    public int getProcessedLineCount() {
        return processedLineCount.get();
    }

    public boolean isRunning() {
        return executor != null && !executor.isShutdown();
    }

    public Optional<WatcherPrediction> processLine(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }

        Optional<ParsedLogEntry> parsed = parser.parseWatcherLine(line);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        processedLineCount.incrementAndGet();

        try {
            FeatureRecord feature = featureExtractor.extract(parsed.get());
            int prediction = predictionService.predict(feature);
            WatcherPrediction result = new WatcherPrediction(parsed.get(), feature, prediction);
            if (prediction == -1) {
                listener.accept(result);
            }
            return Optional.of(result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Unable to process parsed NGINX line: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void poll() {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }

        try {
            drainWatchEvents();
            processAppendedBytes();
        } catch (ClosedWatchServiceException e) {
            log.debug("WatchService closed; stopping current poll");
        } catch (IOException | SecurityException e) {
            log.warn("Temporary NGINX log read error for {}: {}", logPath, e.getMessage());
        }
    }

    private void drainWatchEvents() throws IOException {
        WatchService current = watchService;
        if (current == null) {
            return;
        }

        Path directory = logPath.getParent();
        if (directory != null && Files.isDirectory(directory) && !isDirectoryRegistered(directory)) {
            try {
                directory.register(current, StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE,
                        StandardWatchEventKinds.OVERFLOW);
                watchedDirectory = directory;
            } catch (IOException e) {
                log.warn("Unable to watch NGINX log directory {}: {}", directory, e.getMessage());
            }
        }

        WatchKey key;
        while ((key = current.poll()) != null) {
            key.pollEvents();
            key.reset();
        }
    }

    private volatile Path watchedDirectory;

    private boolean isDirectoryRegistered(Path directory) {
        return directory.equals(watchedDirectory);
    }

    private void processAppendedBytes() throws IOException {
        if (!Files.isRegularFile(logPath)) {
            closeChannel();
            fileKey = null;
            filePosition = 0;
            pendingBytes = new byte[0];
            awaitingRecreation = true;
            return;
        }

        BasicFileAttributes attributes = Files.readAttributes(logPath, BasicFileAttributes.class);
        Object currentFileKey = attributes.fileKey();
        if (currentFileKey == null) {
            currentFileKey = logPath.toRealPath();
        }

        if (channel == null) {
            if (awaitingRecreation) {
                awaitingRecreation = false;
                openAtZero(currentFileKey);
            } else {
                openAtEnd(currentFileKey, attributes);
            }
            return;
        }

        if (!currentFileKey.equals(fileKey)) {
            closeChannel();
            openAtZero(currentFileKey);
            return;
        }

        if (attributes.size() < filePosition) {
            closeChannel();
            openAtZero(currentFileKey);
            return;
        }

        readCompleteLines();
    }

    private void openAtEnd(Object currentFileKey, BasicFileAttributes attributes) throws IOException {
        closeChannel();
        channel = FileChannel.open(logPath, StandardOpenOption.READ);
        fileKey = currentFileKey;
        filePosition = channel.size();
        channel.position(filePosition);
        pendingBytes = new byte[0];
    }

    private void openAtZero(Object currentFileKey) throws IOException {
        closeChannel();
        channel = FileChannel.open(logPath, StandardOpenOption.READ);
        fileKey = currentFileKey;
        filePosition = 0;
        channel.position(0);
        pendingBytes = new byte[0];
    }

    private void readCompleteLines() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(8192);
        while (channel.read(buffer) > 0) {
            buffer.flip();
            byte[] chunk = new byte[buffer.remaining()];
            buffer.get(chunk);
            buffer.clear();
            processBytes(chunk);
        }
    }

    private void processBytes(byte[] chunk) {
        byte[] combined = new byte[pendingBytes.length + chunk.length];
        System.arraycopy(pendingBytes, 0, combined, 0, pendingBytes.length);
        System.arraycopy(chunk, 0, combined, pendingBytes.length, chunk.length);

        int start = 0;
        for (int i = 0; i < combined.length; i++) {
            if (combined[i] == '\n') {
                int length = i - start;
                if (length > 0 && combined[start + length - 1] == '\r') {
                    length--;
                }
                String line = new String(combined, start, length, StandardCharsets.UTF_8);
                processLine(line);
                start = i + 1;
            }
        }

        int remaining = combined.length - start;
        pendingBytes = new byte[remaining];
        System.arraycopy(combined, start, pendingBytes, 0, remaining);

        try {
            filePosition = channel.position();
        } catch (IOException e) {
            log.debug("Unable to update NGINX tail position", e);
        }
    }

    private void closeChannel() {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
                log.debug("Unable to close NGINX log channel", e);
            }
            channel = null;
        }
    }

    public record WatcherPrediction(
            ParsedLogEntry entry,
            FeatureRecord feature,
            int prediction) {

        public boolean isAnomaly() {
            return prediction == -1;
        }
    }
}
