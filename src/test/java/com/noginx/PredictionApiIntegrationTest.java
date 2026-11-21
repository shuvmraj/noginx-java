package com.noginx;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PredictionApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void completePredictionPipelineIsAvailable() {
        ResponseEntity<Map> health = restTemplate.getForEntity("/", Map.class);
        assertEquals(HttpStatus.OK, health.getStatusCode());
        assertEquals("server is running live", health.getBody().get("message"));

        Map<String, Object> feature = Map.of(
                "status", 304,
                "size", 0,
                "method", 1,
                "path", 0,
                "user_agent", 54,
                "hour_of_day", 8);
        Map<String, Object> request = Map.of("feature", feature);

        ResponseEntity<List> response = restTemplate.postForEntity("/predict_one", request, List.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertInstanceOf(Boolean.class, ((Map<?, ?>) response.getBody().get(0)).get("anomaly"));
    }

    @Test
    void batchEndpointReturnsOneResultPerInput() {
        Map<String, Object> feature = Map.of(
                "status", 304, "size", 0, "method", 1, "path", 0,
                "user_agent", 54, "hour_of_day", 8);
        Map<String, Object> request = Map.of("features", List.of(feature, feature));

        ResponseEntity<List> response = restTemplate.postForEntity("/predict", request, List.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
    }
}
