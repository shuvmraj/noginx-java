package com.noginx.ml;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Trains the Isolation Forest model on the features.csv dataset.
 *
 * <p>Implements the logic from train_model.py:
 * <pre>
 * df = pd.read_csv("features.csv")
 * X_train = df
 * model = IsolationForest(contamination="auto", random_state=42)
 * model.fit(X_train)
 * joblib.dump(model, "model.pkl")
 * </pre>
 *
 * <p>Feature order: status, size, method, path, user_agent, hour_of_day (CRITICAL)
 */
public class ModelTrainer {

    private static final Logger log = LoggerFactory.getLogger(ModelTrainer.class);

    private static final String[] EXPECTED_FEATURES = {
            "status", "size", "method", "path", "user_agent", "hour_of_day"
    };

    /**
     * Train the model on features.csv and save it.
     *
     * @param featuresPath path to features.csv
     * @param modelPath    path to save the trained model (.ser file)
     * @return trained IsolationForest
     */
    public static IsolationForest trainAndSave(String featuresPath, String modelPath) throws IOException {
        log.info("Loading training data from: {}", featuresPath);

        double[][] data = loadFeatures(featuresPath);
        log.info("Loaded {} training samples with {} features", data.length,
                data.length > 0 ? data[0].length : 0);

        // Create model with same params as Python:
        // IsolationForest(contamination="auto", random_state=42)
        // n_estimators=100 (default), max_samples="auto" (default)
        IsolationForest model = new IsolationForest(100, -1, 42L, Double.NaN);
        model.fit(data);

        // Save the trained model
        Path modelDir = Path.of(modelPath).getParent();
        if (modelDir != null) {
            Files.createDirectories(modelDir);
        }
        model.save(modelPath);

        log.info("Model training complete and saved to: {}", modelPath);
        return model;
    }

    /**
     * Load features from a CSV file.
     * The CSV must have the header: status,size,method,path,user_agent,hour_of_day
     *
     * @param featuresPath path to features.csv
     * @return 2D array [n_samples][n_features] in EXPECTED_FEATURES order
     */
    public static double[][] loadFeatures(String featuresPath) throws IOException {
        List<double[]> rows = new ArrayList<>();

        try (Reader reader = new FileReader(featuresPath);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord record : parser) {
                try {
                    double[] row = new double[EXPECTED_FEATURES.length];
                    for (int i = 0; i < EXPECTED_FEATURES.length; i++) {
                        row[i] = Double.parseDouble(record.get(EXPECTED_FEATURES[i]));
                    }
                    rows.add(row);
                } catch (NumberFormatException e) {
                    log.warn("Skipping malformed CSV record: {}", e.getMessage());
                }
            }
        }

        return rows.toArray(new double[0][]);
    }

    /**
     * Main method: train model from command line.
     * Usage: java ModelTrainer [features.csv] [model.ser]
     */
    public static void main(String[] args) throws IOException {
        String featuresPath = args.length > 0 ? args[0] : "data/features.csv";
        String modelPath = args.length > 1 ? args[1] : "data/model.ser";

        log.info("NoGinx Model Trainer");
        log.info("Features: {}", featuresPath);
        log.info("Output: {}", modelPath);

        IsolationForest model = trainAndSave(featuresPath, modelPath);
        log.info("Training complete! Model saved to {}", modelPath);
        log.info("n_estimators={}, threshold={}", model.getNEstimators(), model.getThreshold());
    }
}
