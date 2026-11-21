package com.noginx.preprocessing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

/**
 * Loads and applies encoder mappings from encoder_mappings.json.
 *
 * <p>Equivalent to the Python encoder_mappings loading in watcher.py:
 * <pre>
 * with open(encoding_path, "r") as f:
 *     encoder_mappings = json.load(f)
 * </pre>
 *
 * <p>The encoder_mappings.json structure:
 * <pre>
 * {
 *   "method": {"classes": [...], "mapping": {"GET": 0, "HEAD": 1}},
 *   "path":   {"classes": [...], "mapping": {"/downloads/product_1": 0, ...}},
 *   "user_agent": {"classes": [...], "mapping": {"...": 0, ...}}
 * }
 * </pre>
 *
 * <p>Fallback encoding: returns -1 for unknown values, exactly as:
 * <pre>
 * def encode_with_fallback(value, dict):
 *     return dict.get(value, -1)
 * </pre>
 */
@Component
public class EncoderMappings {

    private static final Logger log = LoggerFactory.getLogger(EncoderMappings.class);

    @Value("${noginx.encoder-mappings-path:classpath:encoder_mappings.json}")
    private Resource encoderMappingsResource;

    private Map<String, Integer> methodMapping = Collections.emptyMap();
    private Map<String, Integer> pathMapping = Collections.emptyMap();
    private Map<String, Integer> userAgentMapping = Collections.emptyMap();

    private final ObjectMapper objectMapper;

    public EncoderMappings(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void load() throws IOException {
        log.info("Loading encoder mappings from: {}", encoderMappingsResource);
        try (InputStream is = encoderMappingsResource.getInputStream()) {
            Map<String, Map<String, Object>> fullMap = objectMapper.readValue(
                    is, new TypeReference<Map<String, Map<String, Object>>>() {});

            methodMapping = extractMapping(fullMap, "method");
            pathMapping = extractMapping(fullMap, "path");
            userAgentMapping = extractMapping(fullMap, "user_agent");

            log.info("Encoder mappings loaded: method={} entries, path={} entries, user_agent={} entries",
                    methodMapping.size(), pathMapping.size(), userAgentMapping.size());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> extractMapping(Map<String, Map<String, Object>> fullMap, String key) {
        Map<String, Object> section = fullMap.get(key);
        if (section == null) {
            log.warn("No '{}' section found in encoder_mappings.json", key);
            return Collections.emptyMap();
        }
        Object rawMapping = section.get("mapping");
        if (rawMapping instanceof Map<?, ?> m) {
            // Convert Map<String, Object> to Map<String, Integer>
            Map<String, Integer> result = new java.util.HashMap<>();
            for (Map.Entry<?, ?> entry : m.entrySet()) {
                result.put((String) entry.getKey(), ((Number) entry.getValue()).intValue());
            }
            return Collections.unmodifiableMap(result);
        }
        return Collections.emptyMap();
    }

    /**
     * Encode a method string to its integer code.
     * Python: encode_with_fallback(method, encoder_mappings['method']['mapping'])
     *
     * @param method HTTP method string (e.g., "GET", "POST")
     * @return encoded integer, or -1 if not found
     */
    public int encodeMethod(String method) {
        return methodMapping.getOrDefault(method, -1);
    }

    /**
     * Encode a URL path to its integer code.
     * Python: encode_with_fallback(path, encoder_mappings['path']['mapping'])
     *
     * @param path URL path (query params already stripped)
     * @return encoded integer, or -1 if not found
     */
    public int encodePath(String path) {
        return pathMapping.getOrDefault(path, -1);
    }

    /**
     * Encode a user agent string to its integer code.
     * Python: encode_with_fallback(user_agent, encoder_mappings['user_agent']['mapping'])
     *
     * @param userAgent user agent string
     * @return encoded integer, or -1 if not found
     */
    public int encodeUserAgent(String userAgent) {
        return userAgentMapping.getOrDefault(userAgent, -1);
    }

    // Getters for testing
    public Map<String, Integer> getMethodMapping() { return methodMapping; }
    public Map<String, Integer> getPathMapping() { return pathMapping; }
    public Map<String, Integer> getUserAgentMapping() { return userAgentMapping; }
}
