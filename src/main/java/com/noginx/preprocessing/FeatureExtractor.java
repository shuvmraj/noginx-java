package com.noginx.preprocessing;

import com.noginx.model.FeatureRecord;
import com.noginx.model.ParsedLogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Extracts and encodes feature vectors from parsed NGINX log entries.
 *
 * <p>Implements the feature extraction logic from watcher.py:
 * <pre>
 * df['hour_of_day'] = df['timestamp'].apply(
 *     lambda x: int(datetime.strptime(x.split()[0], "%d/%b/%Y:%H:%M:%S").hour))
 * df['path'] = df['url'].apply(lambda x: x.split('?')[0])
 * df['status'] = df['status'].astype(int)
 * df['size'] = df['size'].astype(int)
 * df['method'] = df['method'].apply(
 *     lambda x: encode_with_fallback(x, encoder_mappings['method']['mapping']))
 * df['path'] = df['path'].apply(
 *     lambda x: encode_with_fallback(x, encoder_mappings['path']['mapping']))
 * df['user_agent'] = df['user_agent'].apply(
 *     lambda x: encode_with_fallback(x, encoder_mappings['user_agent']['mapping']))
 * </pre>
 *
 * <p>Feature order (CRITICAL – must match EXPECTED_FEATURES in api_server.py):
 * status, size, method, path, user_agent, hour_of_day
 */
@Component
public class FeatureExtractor {

    private static final Logger log = LoggerFactory.getLogger(FeatureExtractor.class);

    /**
     * Timestamp format used in NGINX logs.
     * Python: datetime.strptime(x.split()[0], "%d/%b/%Y:%H:%M:%S")
     * The x.split()[0] extracts the date+time part before the timezone offset.
     */
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss", Locale.ENGLISH);

    private final EncoderMappings encoderMappings;

    public FeatureExtractor(EncoderMappings encoderMappings) {
        this.encoderMappings = encoderMappings;
    }

    /**
     * Extract a complete feature vector from a parsed log entry.
     *
     * <p>Applies the same transformations as the Python watcher:
     * <ol>
     *   <li>status – raw integer (already parsed)</li>
     *   <li>size – raw integer (already parsed)</li>
     *   <li>method – LabelEncoded via mappings (fallback: -1)</li>
     *   <li>path – strip query params, then LabelEncoded (fallback: -1)</li>
     *   <li>user_agent – LabelEncoded via mappings (fallback: -1)</li>
     *   <li>hour_of_day – extracted from timestamp</li>
     * </ol>
     *
     * @param entry parsed NGINX log entry
     * @return FeatureRecord with all 6 features
     */
    public FeatureRecord extract(ParsedLogEntry entry) {
        // 1. hour_of_day: Python: datetime.strptime(x.split()[0], "%d/%b/%Y:%H:%M:%S").hour
        int hourOfDay = extractHourOfDay(entry.timestamp());

        // 2. path: Python: url.split('?')[0]
        String rawPath = entry.getPath();

        // 3. Encode method, path, user_agent using mappings (fallback -1 for unknown)
        int encodedMethod = encoderMappings.encodeMethod(entry.method());
        int encodedPath = encoderMappings.encodePath(rawPath);
        int encodedUserAgent = encoderMappings.encodeUserAgent(entry.userAgent());

        log.debug("Feature extraction: status={}, size={}, method={}({}), path={}({}), ua={}({}), hour={}",
                entry.status(), entry.size(),
                entry.method(), encodedMethod,
                rawPath, encodedPath,
                entry.userAgent().length() > 30 ? entry.userAgent().substring(0, 30) + "..." : entry.userAgent(),
                encodedUserAgent,
                hourOfDay);

        return new FeatureRecord(
                entry.status(),      // status (raw int)
                entry.size(),        // size (raw int)
                encodedMethod,       // method (encoded)
                encodedPath,         // path (encoded)
                encodedUserAgent,    // user_agent (encoded)
                hourOfDay            // hour_of_day
        );
    }

    /**
     * Extract hour of day from NGINX timestamp string.
     *
     * <p>Python equivalent:
     * <pre>
     * datetime.strptime(x.split()[0], "%d/%b/%Y:%H:%M:%S").hour
     * </pre>
     *
     * <p>NGINX timestamp format: "21/Nov/2026:14:30:00 +0530"
     * x.split()[0] extracts: "21/Nov/2026:14:30:00"
     *
     * @param timestamp NGINX timestamp string
     * @return hour of day (0-23), or 0 if parsing fails
     */
    public int extractHourOfDay(String timestamp) {
        try {
            // Equivalent to Python: x.split()[0] – splits on whitespace, takes first token
            String dateTimePart = timestamp.split("\\s+")[0];
            LocalDateTime dt = LocalDateTime.parse(dateTimePart, TIMESTAMP_FORMATTER);
            return dt.getHour();
        } catch (Exception e) {
            log.warn("Failed to parse timestamp '{}': {}", timestamp, e.getMessage());
            return 0;
        }
    }

    /**
     * Extract path from URL by stripping query parameters.
     * Python: url.split('?')[0]
     *
     * @param url full URL including potential query string
     * @return path without query string
     */
    public String extractPath(String url) {
        int qIdx = url.indexOf('?');
        return qIdx >= 0 ? url.substring(0, qIdx) : url;
    }
}
