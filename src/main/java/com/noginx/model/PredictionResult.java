package com.noginx.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response model for prediction endpoints.
 *
 * <p>Mirrors the original Python response:
 * <pre>
 * [{"anomaly": true}]
 * </pre>
 *
 * <p>anomaly=true when IsolationForest prediction == -1 (outlier)
 * anomaly=false when IsolationForest prediction == 1 (inlier)
 */
public record PredictionResult(
        @JsonProperty("anomaly") boolean anomaly
) {

    /**
     * Factory method: creates result from raw IsolationForest prediction.
     * Python: bool(pred == -1)
     *
     * @param prediction -1 for anomaly, 1 for normal
     * @return PredictionResult
     */
    public static PredictionResult fromPrediction(int prediction) {
        return new PredictionResult(prediction == -1);
    }
}
