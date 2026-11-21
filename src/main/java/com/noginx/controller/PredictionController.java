package com.noginx.controller;

import com.noginx.model.FeatureRequest;
import com.noginx.model.FeaturesRequest;
import com.noginx.model.PredictionResult;
import com.noginx.service.PredictionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
public class PredictionController {

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @GetMapping("/")
    public Map<String, String> health() {
        return Map.of("message", "server is running live");
    }

    @PostMapping("/predict")
    public List<PredictionResult> predict(@RequestBody FeaturesRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("A request body is required.");
        }

        List<Integer> predictions = predictionService.predict(request.features());
        List<PredictionResult> results = new ArrayList<>(predictions.size());
        for (int prediction : predictions) {
            results.add(PredictionResult.fromPrediction(prediction));
        }
        return results;
    }

    @PostMapping("/predict_one")
    public List<PredictionResult> predictOne(@RequestBody FeatureRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("A request body is required.");
        }
        if (request.feature() == null) {
            throw new IllegalArgumentException("The 'feature' field is required.");
        }

        int prediction = predictionService.predict(request.feature());
        return List.of(PredictionResult.fromPrediction(prediction));
    }
}
