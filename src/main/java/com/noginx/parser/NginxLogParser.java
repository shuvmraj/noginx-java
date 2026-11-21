package com.noginx.parser;

import com.noginx.model.ParsedLogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses NGINX access log lines into structured {@link ParsedLogEntry} objects.
 *
 * <p>Implements the regex from watcher.py (supports both IPv4 and IPv6):
 * <pre>
 * pattern = r'(?P&lt;ip&gt;[\d.:a-fA-F]+)\s+- - \[(?P&lt;timestamp&gt;[^\]]+)\]\s+
 *            "(?P&lt;method&gt;\w+)\s(?P&lt;url&gt;\S+)\s(?P&lt;http_version&gt;[^"]+)"
 *            \s(?P&lt;status&gt;\d+)\s(?P&lt;size&gt;\d+)\s"[^"]*"\s"(?P&lt;user_agent&gt;[^"]*)"'
 * </pre>
 *
 * <p>Also implements the parse_logs.py pattern (IPv4 only, used for training data):
 * <pre>
 * pattern = r'(?P&lt;ip&gt;\d+\.\d+\.\d+\.\d+).*?\[(?P&lt;timestamp&gt;[^\]]+)\]\s
 *            "(?P&lt;method&gt;\w+)\s(?P&lt;url&gt;\S+)\s(?P&lt;http_version&gt;[^"]+)"
 *            \s(?P&lt;status&gt;\d+)\s(?P&lt;size&gt;\d+)\s"[^"]*"\s"(?P&lt;user_agent&gt;[^"]*)"'
 * </pre>
 */
@Component
public class NginxLogParser {

    private static final Logger log = LoggerFactory.getLogger(NginxLogParser.class);

    /**
     * Watcher pattern: supports IPv4 and IPv6 (used in real-time watcher).
     * Equivalent to watcher.py regex pattern.
     */
    private static final Pattern WATCHER_PATTERN = Pattern.compile(
            "(?<ip>[\\d.:a-fA-F]+)\\s+- - \\[(?<timestamp>[^\\]]+)\\]\\s+" +
            "\"(?<method>\\w+)\\s(?<url>\\S+)\\s(?<httpVersion>[^\"]+)\"" +
            "\\s(?<status>\\d+)\\s(?<size>\\d+)\\s\"[^\"]*\"\\s\"(?<userAgent>[^\"]*)\""
    );

    /**
     * Training data pattern: IPv4 only (used in parse_logs.py).
     * Equivalent to parse_logs.py regex pattern.
     */
    private static final Pattern TRAINING_PATTERN = Pattern.compile(
            "(?<ip>\\d+\\.\\d+\\.\\d+\\.\\d+).*?\\[(?<timestamp>[^\\]]+)\\]\\s" +
            "\"(?<method>\\w+)\\s(?<url>\\S+)\\s(?<httpVersion>[^\"]+)\"" +
            "\\s(?<status>\\d+)\\s(?<size>\\d+)\\s\"[^\"]*\"\\s\"(?<userAgent>[^\"]*)\""
    );

    /**
     * Parse a single NGINX log line using the watcher pattern (IPv4 + IPv6).
     * Used by the real-time log watcher.
     *
     * <p>Equivalent to:
     * <pre>
     * match = re.search(pattern, line)
     * if match:
     *     df = pd.DataFrame([match.groupdict()])
     * </pre>
     *
     * @param line raw NGINX log line
     * @return Optional containing parsed entry if matched, empty otherwise
     */
    public Optional<ParsedLogEntry> parseWatcherLine(String line) {
        return parseLine(line, WATCHER_PATTERN);
    }

    /**
     * Parse a single NGINX log line using the training data pattern (IPv4 only).
     * Used for processing nginx.log → nginx_logs.csv.
     *
     * @param line raw NGINX log line
     * @return Optional containing parsed entry if matched, empty otherwise
     */
    public Optional<ParsedLogEntry> parseTrainingLine(String line) {
        return parseLine(line, TRAINING_PATTERN);
    }

    private Optional<ParsedLogEntry> parseLine(String line, Pattern pattern) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = pattern.matcher(line);
        if (!matcher.find()) {
            log.debug("Log line did not match expected format: {}", 
                    line.length() > 100 ? line.substring(0, 100) + "..." : line);
            return Optional.empty();
        }

        try {
            int status = Integer.parseInt(matcher.group("status"));
            int size = Integer.parseInt(matcher.group("size"));

            return Optional.of(new ParsedLogEntry(
                    matcher.group("ip"),
                    matcher.group("timestamp"),
                    matcher.group("method"),
                    matcher.group("url"),
                    matcher.group("httpVersion"),
                    status,
                    size,
                    matcher.group("userAgent")
            ));
        } catch (NumberFormatException e) {
            log.warn("Failed to parse numeric fields in log line: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
