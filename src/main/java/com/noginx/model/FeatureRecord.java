package com.noginx.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a single feature vector for anomaly prediction.
 *
 * <p>Mirrors the original Python model:
 * <pre>
 * class Feature(BaseModel):
 *     feature: Dict[str, int]
 * </pre>
 *
 * <p>Feature order for model prediction (CRITICAL – must match EXPECTED_FEATURES):
 * status, size, method, path, user_agent, hour_of_day
 */
public record FeatureRecord(
        @JsonProperty("status") Integer status,
        @JsonProperty("size") Integer size,
        @JsonProperty("method") Integer method,
        @JsonProperty("path") Integer path,
        @JsonProperty("user_agent") Integer userAgent,
        @JsonProperty("hour_of_day") Integer hourOfDay
) {

    /**
     * Returns feature values in EXPECTED_FEATURES order:
     * [status, size, method, path, user_agent, hour_of_day]
     * This order MUST match the original Python implementation.
     */
    public double[] toDoubleArray() {
        return new double[]{status, size, method, path, userAgent, hourOfDay};
    }

    /**
     * Returns feature values as int array in EXPECTED_FEATURES order.
     */
    public int[] toIntArray() {
        return new int[]{status, size, method, path, userAgent, hourOfDay};
    }
}
