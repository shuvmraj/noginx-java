package com.noginx.service;

import com.noginx.ml.IsolationForest;
import com.noginx.ml.ModelTrainer;
import com.noginx.model.FeatureRecord;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class PredictionService {

    private static final Logger log = LoggerFactory.getLogger(PredictionService.class);

    private final String modelPath;
    private final String featuresPath;
    private volatile IsolationForest model;

    public PredictionService(
            @Value("${noginx.model-path:${user.dir}/data/model.ser}") String modelPath,
            @Value("${noginx.features-path:${user.dir}/data/features.csv}") String featuresPath) {
        this.modelPath = modelPath;
        this.featuresPath = featuresPath;
    }

    @PostConstruct
    public void initialize() {
        Path serializedModel = Path.of(modelPath);

        if (Files.isRegularFile(serializedModel)) {
            try {
                model = IsolationForest.load(serializedModel.toString());
                log.info("Loaded trained model from {}", serializedModel);
                return;
            } catch (IOException | ClassNotFoundException e) {
                throw new IllegalStateException(
                        "Unable to load persisted model '" + serializedModel
                                + "'. Remove it or restore a valid model file: " + e.getMessage(), e);
            }
        }

        Path trainingData = Path.of(featuresPath);
        if (!Files.isRegularFile(trainingData)) {
            throw new IllegalStateException(
                    "No trained model exists at '" + serializedModel
                            + "' and training data was not found at '" + trainingData
                            + "'. Provide data/features.csv or noginx.features-path.");
        }

        try {
            log.info("No persisted model found; training from {}", trainingData);
            model = ModelTrainer.trainAndSave(trainingData.toString(), serializedModel.toString());
            log.info("Initialized model and saved it to {}", serializedModel);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(
                    "Unable to initialize Isolation Forest from '" + trainingData
                            + "' and save it to '" + serializedModel + "': " + e.getMessage(), e);
        }
    }

    public int predict(FeatureRecord feature) {
        validateFeature(feature);
        IsolationForest initializedModel = model;
        if (initializedModel == null) {
            throw new IllegalStateException("Prediction model is not initialized.");
        }
        return initializedModel.predictOne(feature.toDoubleArray());
    }

    public List<Integer> predict(List<FeatureRecord> features) {
        if (features == null) {
            throw new IllegalArgumentException("The 'features' field is required.");
        }
        if (features.isEmpty()) {
            throw new IllegalArgumentException("The 'features' list must contain at least one feature.");
        }

        List<Integer> predictions = new ArrayList<>(features.size());
        for (FeatureRecord feature : features) {
            predictions.add(predict(feature));
        }
        return predictions;
    }

    public void validateFeature(FeatureRecord feature) {
        if (feature == null) {
            throw new IllegalArgumentException("A feature object is required.");
        }
        if (feature.status() == null || feature.status() < 100 || feature.status() > 599) {
            throw new IllegalArgumentException("Feature 'status' is required and must be a valid HTTP status code.");
        }
        if (feature.size() == null || feature.size() < 0) {
            throw new IllegalArgumentException("Feature 'size' is required and must be non-negative.");
        }
        if (feature.method() == null) {
            throw new IllegalArgumentException("Feature 'method' is required.");
        }
        if (feature.path() == null) {
            throw new IllegalArgumentException("Feature 'path' is required.");
        }
        if (feature.userAgent() == null) {
            throw new IllegalArgumentException("Feature 'user_agent' is required.");
        }
        if (feature.hourOfDay() == null || feature.hourOfDay() < 0 || feature.hourOfDay() > 23) {
            throw new IllegalArgumentException("Feature 'hour_of_day' is required and must be between 0 and 23.");
        }
    }

    public IsolationForest getModel() {
        return model;
    }
}
