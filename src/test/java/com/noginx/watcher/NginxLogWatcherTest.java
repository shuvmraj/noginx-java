package com.noginx.watcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.noginx.parser.NginxLogParser;
import com.noginx.preprocessing.EncoderMappings;
import com.noginx.preprocessing.FeatureExtractor;
import com.noginx.service.PredictionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NginxLogWatcherTest {

    @TempDir
    Path tempDir;

    @Test
    void processesMultipleAppendedLinesAndIgnoresMalformedLine() throws Exception {
        Path log = tempDir.resolve("access.log");
        Files.writeString(log, "existing line\n", StandardCharsets.UTF_8);

        WatcherHarness harness = createHarness(log);
        harness.watcher.start();
        assertTrue(harness.watcher.isRunning());

        harness.watcher.processLine("malformed line");
        assertTrue(harness.watcher.processLine(validLine("304")).isPresent());
        assertTrue(harness.watcher.processLine(validLine("200")).isPresent());
        assertTrue(harness.watcher.processLine(validLine("404")).isPresent());
        assertEquals(3, harness.watcher.getProcessedLineCount());
        harness.watcher.stop();
    }

    @Test
    void automaticTailDetectsAppendedLine() throws Exception {
        Path log = tempDir.resolve("access.log");
        Files.writeString(log, "existing line\n", StandardCharsets.UTF_8);

        WatcherHarness harness = createHarness(log);
        harness.watcher.start();
        Files.writeString(log, validLine("304") + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);

        waitFor(() -> harness.watcher.getProcessedLineCount() >= 1, Duration.ofSeconds(3));
        harness.watcher.stop();
    }

    @Test
    void detectsTruncatedFileAndProcessesOnlyReappendedData() throws Exception {
        Path log = tempDir.resolve("access.log");
        Files.writeString(log, validLine("200") + "\n", StandardCharsets.UTF_8);

        WatcherHarness harness = createHarness(log);
        harness.watcher.start();

        Files.write(log, new byte[0], StandardOpenOption.TRUNCATE_EXISTING);
        Thread.sleep(1100);
        Files.writeString(log, validLine("304") + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);

        waitFor(() -> harness.watcher.getProcessedLineCount() >= 1, Duration.ofSeconds(3));
        harness.watcher.stop();
    }

    @Test
    void detectsDeletedAndRecreatedFile() throws Exception {
        Path log = tempDir.resolve("access.log");
        Files.writeString(log, "old line\n", StandardCharsets.UTF_8);

        WatcherHarness harness = createHarness(log);
        harness.watcher.start();

        Files.delete(log);
        Thread.sleep(1100);
        Files.writeString(log, validLine("404") + "\n", StandardCharsets.UTF_8);

        waitFor(() -> harness.watcher.getProcessedLineCount() >= 1, Duration.ofSeconds(3));
        harness.watcher.stop();
    }

    @Test
    void stopGracefullyWhenWatcherIsRunning() throws Exception {
        Path log = tempDir.resolve("access.log");
        Files.writeString(log, "", StandardCharsets.UTF_8);
        WatcherHarness harness = createHarness(log);

        harness.watcher.start();
        harness.watcher.stop();
        harness.watcher.stop();
    }

    @Test
    void disabledWatcherDoesNotStart() throws Exception {
        Path log = tempDir.resolve("access.log");
        Files.writeString(log, "", StandardCharsets.UTF_8);
        WatcherHarness disabled = createHarness(log, false);

        disabled.watcher.start();
        assertFalse(disabled.watcher.isRunning());
        disabled.watcher.stop();
    }

    private WatcherHarness createHarness(Path log) throws Exception {
        return createHarness(log, true);
    }

    private WatcherHarness createHarness(Path log, boolean enabled) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        EncoderMappings mappings = new EncoderMappings(mapper);
        ReflectionTestUtils.setField(mappings, "encoderMappingsResource",
                new ClassPathResource("encoder_mappings.json"));
        mappings.load();
        FeatureExtractor extractor = new FeatureExtractor(mappings);

        Path features = tempDir.resolve("features-" + tempDir.toFile().list().length + ".csv");
        Files.writeString(features, """
                status,size,method,path,user_agent,hour_of_day
                200,100,0,0,40,8
                304,0,1,0,54,8
                200,200,0,1,41,9
                404,50,0,2,42,10
                """, StandardCharsets.UTF_8);
        Path model = tempDir.resolve("model-" + tempDir.toFile().list().length + ".ser");
        PredictionService service = new PredictionService(model.toString(), features.toString());
        service.initialize();

        NginxLogWatcher watcher = new NginxLogWatcher(
                new NginxLogParser(),
                extractor,
                service,
                log.toString(),
                100,
                enabled);

        AtomicInteger processed = new AtomicInteger();
        watcher.setAnomalyListener(prediction -> processed.incrementAndGet());
        return new WatcherHarness(watcher, processed);
    }

    private String validLine(String status) {
        return "127.0.0.1 - - [21/Nov/2026:08:30:00 +0530] " +
                "\"GET /downloads/product_1?item=1 HTTP/1.1\" " + status +
                " 0 \"-\" \"Mozilla/5.0\"";
    }

    private void waitFor(java.util.concurrent.Callable<Boolean> condition, Duration timeout)
            throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.call()) {
                return;
            }
            Thread.sleep(25);
        }
        fail("Timed out waiting for watcher condition");
    }

    private record WatcherHarness(
            NginxLogWatcher watcher,
            AtomicInteger processedCount) {
    }
}
