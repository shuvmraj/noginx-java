package com.noginx.service;

import com.noginx.model.FeatureRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PredictionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void validFeatureProducesValidPrediction() throws Exception {
        PredictionService service = createTrainedService();
        int prediction = service.predict(validFeature());
        assertTrue(prediction == -1 || prediction == 1);
    }

    @Test
    void batchPredictionReturnsOneResultPerFeature() throws Exception {
        PredictionService service = createTrainedService();
        List<Integer> predictions = service.predict(List.of(validFeature(), validFeature()));
        assertEquals(2, predictions.size());
    }

    @Test
    void missingFieldsAreRejected() throws Exception {
        PredictionService service = createTrainedService();
        FeatureRecord missingHour = new FeatureRecord(304, 0, 1, 0, 54, null);
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> service.predict(missingHour));
        assertTrue(error.getMessage().contains("hour_of_day"));
    }

    @Test
    void persistedModelLoads() throws Exception {
        Path model = tempDir.resolve("model.ser");
        PredictionService first = new PredictionService(model.toString(),
                tempDir.resolve("features.csv").toString());
        writeSmallTrainingData(tempDir.resolve("features.csv"));
        first.initialize();
        assertTrue(Files.isRegularFile(model));

        PredictionService loaded = new PredictionService(model.toString(),
                tempDir.resolve("missing.csv").toString());
        loaded.initialize();
        assertTrue(loaded.getModel().predictOne(validFeature().toDoubleArray()) == -1
                || loaded.getModel().predictOne(validFeature().toDoubleArray()) == 1);
    }

    @Test
    void missingTrainingDataProducesClearError() {
        PredictionService service = new PredictionService(
                tempDir.resolve("missing.ser").toString(),
                tempDir.resolve("missing.csv").toString());

        IllegalStateException error = assertThrows(IllegalStateException.class, service::initialize);
        assertTrue(error.getMessage().contains("training data"));
        assertTrue(error.getMessage().contains("noginx.features-path"));
    }

    private PredictionService createTrainedService() throws Exception {
        Path features = tempDir.resolve("features.csv");
        writeSmallTrainingData(features);
        PredictionService service = new PredictionService(
                tempDir.resolve("model.ser").toString(), features.toString());
        service.initialize();
        return service;
    }

    private void writeSmallTrainingData(Path features) throws Exception {
        String csv = """
                status,size,method,path,user_agent,hour_of_day
                200,100,0,0,40,8
                304,0,1,0,54,8
                200,200,0,1,41,9
                404,50,0,2,42,10
                """;
        Files.writeString(features, csv, StandardCharsets.UTF_8);
    }

    private FeatureRecord validFeature() {
        return new FeatureRecord(304, 0, 1, 0, 54, 8);
    }
}
