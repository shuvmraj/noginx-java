package com.noginx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Request model for the POST /predict endpoint (batch prediction).
 *
 * <p>Mirrors the original Python model:
 * <pre>
 * class Features(BaseModel):
 *     features: List[Dict[str, int]]
 * </pre>
 *
 * <p>Example request JSON:
 * <pre>
 * {
 *   "features": [
 *     {"status": 304, "size": 0, "method": 1, "path": 0, "user_agent": 54, "hour_of_day": 8}
 *   ]
 * }
 * </pre>
 */
public record FeaturesRequest(
        @JsonProperty("features") List<FeatureRecord> features
) {}
