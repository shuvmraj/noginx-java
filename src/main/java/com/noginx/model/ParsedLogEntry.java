package com.noginx.model;

/**
 * Represents a fully parsed NGINX access log entry.
 *
 * <p>Corresponds to the groups captured by the regex pattern in watcher.py:
 * <pre>
 * (?P&lt;ip&gt;[\d.:a-fA-F]+)\s+- - \[(?P&lt;timestamp&gt;[^\]]+)\]\s+"(?P&lt;method&gt;\w+)\s(?P&lt;url&gt;\S+)
 * \s(?P&lt;http_version&gt;[^"]+)"\s(?P&lt;status&gt;\d+)\s(?P&lt;size&gt;\d+)\s"[^"]*"\s"(?P&lt;user_agent&gt;[^"]*)"
 * </pre>
 */
public record ParsedLogEntry(
        String ip,
        String timestamp,
        String method,
        String url,
        String httpVersion,
        int status,
        int size,
        String userAgent
) {

    /**
     * Extracts the path from the URL by stripping query parameters.
     * Equivalent to Python: url.split('?')[0]
     */
    public String getPath() {
        int qIdx = url.indexOf('?');
        return qIdx >= 0 ? url.substring(0, qIdx) : url;
    }
}
