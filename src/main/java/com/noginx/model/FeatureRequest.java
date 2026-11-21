package com.noginx.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request model for the POST /predict_one endpoint (single prediction).
 *
 * <p>Mirrors the original Python model:
 * <pre>
 * class Feature(BaseModel):
 *     feature: Dict[str, int]
 * </pre>
 *
 * <p>Example request JSON:
 * <pre>
 * {
 *   "feature": {"status": 304, "size": 0, "method": 1, "path": 0, "user_agent": 54, "hour_of_day": 8}
 * }
 * </pre>
 */
public record FeatureRequest(
        @JsonProperty("feature") FeatureRecord feature
) {}
