package com.noginx.ml;

import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IsolationForestParityTest {

    @Test
    void loadsFittedSklearnModelAndClassifiesKnownVectors() throws Exception {
        Path modelJson = Path.of("data/model.json");
        assertTrue(modelJson.toFile().isFile(), "data/model.json must contain the fitted sklearn export");

        IsolationForest model = IsolationForest.load(modelJson.toString());

        assertEquals(1, model.predictOne(new double[]{304, 0, 1, 0, 54, 8}),
                "Previously mismatched normal vector");
        assertEquals(-1, model.predictOne(new double[]{200, 490, 0, 0, 40, 8}),
                "Previously mismatched anomalous vector");
        assertEquals(-0.49159672954830297,
                model.scoreSample(new double[]{304, 0, 1, 0, 54, 8}),
                1e-12);
    }

    @Test
    void matchesReferenceClassificationsAcrossFixedDataset() throws Exception {
        Path modelJson = Path.of("data/model.json");
        Path referenceCsv = Path.of("data/parity_reference.csv");
        assertTrue(modelJson.toFile().isFile());
        assertTrue(referenceCsv.toFile().isFile());

        IsolationForest model = IsolationForest.load(modelJson.toString());
        int compared = 0;
        int mismatches = 0;
        StringBuilder mismatchDetails = new StringBuilder();

        try (Reader reader = Files.newBufferedReader(referenceCsv, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .build()
                     .parse(reader)) {
            for (CSVRecord record : parser) {
                double[] vector = new double[]{
                        Double.parseDouble(record.get("status")),
                        Double.parseDouble(record.get("size")),
                        Double.parseDouble(record.get("method")),
                        Double.parseDouble(record.get("path")),
                        Double.parseDouble(record.get("user_agent")),
                        Double.parseDouble(record.get("hour_of_day"))
                };
                int reference = Integer.parseInt(record.get("python_prediction"));
                int javaPrediction = model.predictOne(vector);
                if (javaPrediction != reference) {
                    mismatches++;
                    double javaDepthSum = 0.0;
                    for (IsolationForest.IsolationTree tree : model.getTrees()) {
                        javaDepthSum += tree.pathLength(vector);
                    }
                    mismatchDetails.append(record.get("dataset_index"))
                            .append(" vector=").append(record.toString())
                            .append(" python=").append(reference)
                            .append(" java=").append(javaPrediction)
                            .append(" java_score=").append(model.scoreSample(vector))
                            .append(" python_score=").append(record.get("python_score_samples"))
                            .append(" java_depth_sum=").append(javaDepthSum)
                            .append("; ");
                }
                compared++;
            }
        }

        assertEquals(0, mismatches,
                "Expected exact fitted-tree classification parity across " + compared
                        + " vectors; mismatches: " + mismatchDetails);
    }
}
