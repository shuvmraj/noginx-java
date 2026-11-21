package com.noginx.ml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.util.*;

/**
 * Isolation Forest implementation in pure Java.
 *
 * <p>Implements the same algorithm as sklearn's IsolationForest with:
 * <ul>
 *   <li>n_estimators=100 (default)</li>
 *   <li>contamination="auto" (threshold = -0.5 offset from mean depth)</li>
 *   <li>random_state=42 (reproducible)</li>
 *   <li>max_samples="auto" → min(256, n_samples)</li>
 *   <li>max_features=1.0 (use all features)</li>
 *   <li>bootstrap=False (no replacement sampling)</li>
 * </ul>
 *
 * <p>Algorithm description:
 * <ol>
 *   <li>Build n_estimators isolation trees, each on a random subsample of max_samples records</li>
 *   <li>Each tree isolates points by randomly selecting a feature and a split value</li>
 *   <li>Anomaly score = average path length to reach a data point (normalized)</li>
 *   <li>Shorter average path → more isolated → more anomalous</li>
 * </ol>
 *
 * <p>Prediction semantics (matches Python):
 * <ul>
 *   <li>predict() returns -1 (anomaly) or +1 (normal)</li>
 *   <li>score_samples() returns negative anomaly score (lower = more anomalous)</li>
 * </ul>
 *
 * <p>Reference: Liu, Fei Tony, Ting, Kai Ming, and Zhou, Zhi-Hua. "Isolation forest."
 * Eighth IEEE International Conference on Data Mining, 2008.
 */
public class IsolationForest implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(IsolationForest.class);

    // Parameters (matching sklearn defaults for the original project)
    private final int nEstimators;       // = 100
    private final int maxSamples;        // = min(256, n_samples)
    private final long randomState;      // = 42
    private final double contamination;  // = "auto" → -0.5 threshold offset

    // Trained trees
    private List<IsolationTree> trees;
    private int nFeatures;
    private int nSamples;
    private double threshold;  // Decision threshold for predict()

    // Feature index names (for logging)
    private static final String[] FEATURE_NAMES = {
            "status", "size", "method", "path", "user_agent", "hour_of_day"
    };

    /**
     * Create an IsolationForest with the same parameters as the original Python model.
     * Python: IsolationForest(contamination="auto", random_state=42)
     */
    public IsolationForest() {
        this(100, -1, 42L, Double.NaN);  // -1 means "auto" for maxSamples
    }

    /**
     * Create an IsolationForest with custom parameters.
     *
     * @param nEstimators  number of trees (default: 100)
     * @param maxSamples   max samples per tree (-1 = "auto" = min(256, n_samples))
     * @param randomState  random seed (42 for reproducibility)
     * @param contamination fraction of outliers (NaN = "auto")
     */
    public IsolationForest(int nEstimators, int maxSamples, long randomState, double contamination) {
        this.nEstimators = nEstimators;
        this.maxSamples = maxSamples;
        this.randomState = randomState;
        this.contamination = contamination;
    }

    /**
     * Train the Isolation Forest on the given dataset.
     * Python: model.fit(X_train)
     *
     * @param data 2D array [n_samples][n_features]
     */
    public void fit(double[][] data) {
        this.nSamples = data.length;
        this.nFeatures = data[0].length;

        // Determine actual max samples (sklearn "auto" = min(256, n_samples))
        int actualMaxSamples = (maxSamples == -1)
                ? Math.min(256, nSamples)
                : Math.min(maxSamples, nSamples);

        log.info("Training IsolationForest: n_estimators={}, max_samples={}, n_features={}, n_samples={}",
                nEstimators, actualMaxSamples, nFeatures, nSamples);

        Random random = new Random(randomState);
        trees = new ArrayList<>(nEstimators);

        // Build each isolation tree
        for (int i = 0; i < nEstimators; i++) {
            // Subsample WITHOUT replacement (bootstrap=False)
            double[][] subsample = subsampleWithoutReplacement(data, actualMaxSamples, random);
            int maxDepth = (int) Math.ceil(Math.log(actualMaxSamples) / Math.log(2));
            IsolationTree tree = new IsolationTree(maxDepth, random.nextLong());
            tree.fit(subsample);
            trees.add(tree);
        }

        // Compute decision threshold
        // sklearn "auto" contamination: threshold = -0.5 (in terms of score_samples offset)
        // We compute the actual threshold by scoring all training data
        this.threshold = computeThreshold(data);

        log.info("IsolationForest training complete. Threshold: {}", threshold);
    }

    /**
     * Compute the decision threshold.
     *
     * <p>sklearn with contamination="auto" uses score threshold such that the
     * fraction of outliers equals contamination. With "auto", sklearn internally
     * uses -0.5 as the offset_fit_ parameter.
     *
     * <p>Score formula: score = -E[path_length] / c(n) where c(n) is the
     * expected path length of an unsuccessful BST search.
     * Anomaly: score < threshold
     *
     * <p>For contamination="auto": threshold = -0.5 (matches sklearn behavior)
     */
    private double computeThreshold(double[][] data) {
        if (Double.isNaN(contamination)) {
            // "auto" contamination: sklearn uses offset -0.5
            // threshold = -0.5 in normalized score space
            // We compute scores on training data and find the percentile threshold
            // that corresponds to -0.5 offset in sklearn's internal scoring
            // For exact parity: sklearn contamination="auto" → offset_ = -0.5
            // threshold = offset_ (scores < offset_ are anomalies)
            // We score our data and use percentile approach
            double[] scores = scoreAll(data);
            Arrays.sort(scores);
            // contamination_rate = 0.1 as sklearn fallback for "auto"
            // Actually sklearn "auto" does NOT use percentile - it uses score offset -0.5
            // We use the -0.5 normalized threshold
            return -0.5;
        } else {
            // Explicit contamination: find the (contamination) percentile of scores
            double[] scores = scoreAll(data);
            Arrays.sort(scores);
            int idx = (int) Math.floor(contamination * scores.length);
            return scores[Math.min(idx, scores.length - 1)];
        }
    }

    /**
     * Score all samples. Returns raw anomaly scores.
     */
    private double[] scoreAll(double[][] data) {
        double[] scores = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            scores[i] = scoreSample(data[i]);
        }
        return scores;
    }

    /**
     * Predict anomaly labels for a batch of samples.
     * Python: model.predict(df)
     *
     * <p>Returns:
     * <ul>
     *   <li>-1 for anomaly (outlier)</li>
     *   <li>+1 for normal (inlier)</li>
     * </ul>
     *
     * @param data 2D array [n_samples][n_features]
     * @return int array of predictions (-1 or +1)
     */
    public int[] predict(double[][] data) {
        int[] predictions = new int[data.length];
        for (int i = 0; i < data.length; i++) {
            double score = scoreSample(data[i]);
            predictions[i] = (score < threshold) ? -1 : 1;
        }
        return predictions;
    }

    /**
     * Predict anomaly label for a single sample.
     * Used in /predict_one endpoint.
     *
     * @param sample feature vector [n_features]
     * @return -1 (anomaly) or +1 (normal)
     */
    public int predictOne(double[] sample) {
        double score = scoreSample(sample);
        return (score < threshold) ? -1 : 1;
    }

    /**
     * Compute anomaly score for a single sample.
     * Lower score = more anomalous.
     *
     * <p>Score formula (sklearn equivalent):
     * score = -2^(-mean(path_lengths) / c(max_samples))
     * where c(n) = 2*H(n-1) - 2*(n-1)/n is the expected path length in a BST.
     *
     * @param sample feature vector
     * @return normalized anomaly score (< threshold = anomaly)
     */
    public double scoreSample(double[] sample) {
        double totalPathLength = 0.0;
        for (IsolationTree tree : trees) {
            totalPathLength += tree.pathLength(sample);
        }
        double avgPathLength = totalPathLength / trees.size();

        // Normalize by c(max_samples)
        int actualMaxSamples = (maxSamples == -1)
                ? Math.min(256, nSamples)
                : Math.min(maxSamples, nSamples);
        double cn = cFactor(actualMaxSamples);

        // sklearn score_samples() returns the negative normality score:
        // score_samples = -2^(-E[h(x)] / c(n)).
        return -Math.pow(2.0, -avgPathLength / cn);
    }

    /**
     * Expected path length of an unsuccessful search in a BST with n nodes.
     * c(n) = 2*H(n-1) - 2*(n-1)/n
     * where H(i) = ln(i) + 0.5772156649 (Euler-Mascheroni constant)
     */
    private static double cFactor(int n) {
        if (n <= 1) return 0.0;
        if (n == 2) return 1.0;
        double h = Math.log(n - 1) + 0.5772156649;  // Harmonic number approximation
        return 2.0 * h - 2.0 * (n - 1.0) / n;
    }

    /**
     * Subsample data without replacement (bootstrap=False in sklearn).
     */
    private static double[][] subsampleWithoutReplacement(double[][] data, int k, Random random) {
        int n = data.length;
        // Fisher-Yates shuffle to pick k unique indices
        int[] indices = new int[n];
        for (int i = 0; i < n; i++) indices[i] = i;
        for (int i = 0; i < k; i++) {
            int j = i + random.nextInt(n - i);
            int tmp = indices[i];
            indices[i] = indices[j];
            indices[j] = tmp;
        }
        double[][] subsample = new double[k][];
        for (int i = 0; i < k; i++) {
            subsample[i] = data[indices[i]];
        }
        return subsample;
    }

    // Serialization support
    public List<IsolationTree> getTrees() { return trees; }
    public int getNFeatures() { return nFeatures; }
    public int getNSamples() { return nSamples; }
    public double getThreshold() { return threshold; }
    public int getNEstimators() { return nEstimators; }

    /**
     * Save the trained model to a file.
     *
     * @param modelPath path to save the model
     */
    public void save(String modelPath) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(modelPath))) {
            oos.writeObject(this);
            log.info("Model saved to: {}", modelPath);
        }
    }

    /**
     * Load a trained model from a file.
     *
     * @param modelPath path to load the model from
     * @return loaded IsolationForest
     */
    public static IsolationForest load(String modelPath) throws IOException, ClassNotFoundException {
        if (modelPath.toLowerCase(Locale.ROOT).endsWith(".json")) {
            return loadSklearnJson(modelPath);
        }
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(modelPath))) {
            IsolationForest model = (IsolationForest) ois.readObject();
            log.info("Model loaded from: {}", modelPath);
            return model;
        }
    }

    /**
     * Load a fitted scikit-learn IsolationForest exported to Java-readable JSON.
     * The JSON contains each fitted ExtraTree node, split, feature, and leaf size.
     */
    public static IsolationForest loadSklearnJson(String modelPath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(new File(modelPath));
        int nEstimators = root.path("n_estimators").asInt();
        int maxSamples = root.path("max_samples").asInt(256);
        int nFeatures = root.path("n_features").asInt();
        double offset = root.path("offset").asDouble(-0.5);

        IsolationForest model = new IsolationForest(nEstimators, maxSamples, 42L, Double.NaN);
        model.nSamples = maxSamples;
        model.nFeatures = nFeatures;
        model.threshold = offset;
        model.trees = new ArrayList<>(nEstimators);

        JsonNode trees = root.path("trees");
        JsonNode estimatorsFeatures = root.path("estimators_features");
        for (int i = 0; i < trees.size(); i++) {
            JsonNode treeNode = trees.get(i);
            int[] featureMap = new int[nFeatures];
            if (estimatorsFeatures != null && i < estimatorsFeatures.size()) {
                JsonNode mapNode = estimatorsFeatures.get(i);
                for (int j = 0; j < mapNode.size(); j++) {
                    featureMap[j] = mapNode.get(j).asInt();
                }
            } else {
                for (int j = 0; j < nFeatures; j++) {
                    featureMap[j] = j;
                }
            }
            model.trees.add(IsolationTree.fromSklearn(treeNode, featureMap));
        }

        log.info("Loaded fitted scikit-learn IsolationForest JSON from: {}", modelPath);
        return model;
    }

    // ===================================================================
    // Inner class: Isolation Tree
    // ===================================================================

    /**
     * A single isolation tree in the Isolation Forest.
     * Each tree randomly partitions the feature space until all points
     * are isolated or the maximum depth is reached.
     */
    public static class IsolationTree implements Serializable {

        private static final long serialVersionUID = 1L;

        private final int maxDepth;
        private final Random random;
        private Node root;

        public IsolationTree(int maxDepth, long seed) {
            this.maxDepth = maxDepth;
            this.random = new Random(seed);
        }

        public void fit(double[][] data) {
            root = buildTree(data, 0);
        }

        private IsolationTree(int maxDepth, Node fittedRoot) {
            this.maxDepth = maxDepth;
            this.random = null;
            this.root = fittedRoot;
        }

        public static IsolationTree fromSklearn(JsonNode treeNode, int[] featureMap) {
            JsonNode leftNodes = treeNode.path("children_left");
            JsonNode rightNodes = treeNode.path("children_right");
            JsonNode features = treeNode.path("feature");
            JsonNode thresholds = treeNode.path("threshold");
            JsonNode nodeSamples = treeNode.path("n_node_samples");

            Node root = buildSklearnNode(
                    0, leftNodes, rightNodes, features, thresholds, nodeSamples, featureMap);
            return new IsolationTree(64, root);
        }

        private static Node buildSklearnNode(
                int nodeIndex,
                JsonNode leftNodes,
                JsonNode rightNodes,
                JsonNode features,
                JsonNode thresholds,
                JsonNode nodeSamples,
                int[] featureMap) {
            int leftIndex = leftNodes.get(nodeIndex).asInt();
            int rightIndex = rightNodes.get(nodeIndex).asInt();
            int sampleCount = nodeSamples.get(nodeIndex).asInt();

            if (leftIndex == -1 && rightIndex == -1) {
                return new Node(sampleCount);
            }

            int relativeFeature = features.get(nodeIndex).asInt();
            int featureIndex = relativeFeature >= 0 && relativeFeature < featureMap.length
                    ? featureMap[relativeFeature]
                    : relativeFeature;
            double threshold = thresholds.get(nodeIndex).asDouble();
            Node node = new Node(featureIndex, threshold);
            node.left = buildSklearnNode(
                    leftIndex, leftNodes, rightNodes, features, thresholds, nodeSamples, featureMap);
            node.right = buildSklearnNode(
                    rightIndex, leftNodes, rightNodes, features, thresholds, nodeSamples, featureMap);
            return node;
        }

        private Node buildTree(double[][] data, int depth) {
            int n = data.length;
            int nf = data[0].length;

            // Stop conditions
            if (n <= 1 || depth >= maxDepth) {
                return new Node(n);
            }

            // Randomly select a feature
            int featureIdx = random.nextInt(nf);

            // Find min and max values for the selected feature
            double minVal = data[0][featureIdx];
            double maxVal = data[0][featureIdx];
            for (double[] row : data) {
                if (row[featureIdx] < minVal) minVal = row[featureIdx];
                if (row[featureIdx] > maxVal) maxVal = row[featureIdx];
            }

            // If all values are the same, can't split
            if (minVal == maxVal) {
                return new Node(n);
            }

            // Random split value between min and max
            double splitValue = minVal + random.nextDouble() * (maxVal - minVal);

            // Partition data
            List<double[]> leftList = new ArrayList<>();
            List<double[]> rightList = new ArrayList<>();
            for (double[] row : data) {
                if (row[featureIdx] < splitValue) {
                    leftList.add(row);
                } else {
                    rightList.add(row);
                }
            }

            double[][] left = leftList.toArray(new double[0][]);
            double[][] right = rightList.toArray(new double[0][]);

            Node node = new Node(featureIdx, splitValue);
            node.left = buildTree(left, depth + 1);
            node.right = buildTree(right, depth + 1);
            return node;
        }

        /**
         * Compute the path length for a given sample in this tree.
         * Path length = number of edges traversed + adjustment for external node size.
         */
        public double pathLength(double[] sample) {
            return pathLength(sample, root, 0);
        }

        private double pathLength(double[] sample, Node node, int depth) {
            if (node.isLeaf) {
                // Adjust for the size of the external node
                return depth + cFactor(node.size);
            }
            if (sample[node.featureIndex] < node.splitValue) {
                return pathLength(sample, node.left, depth + 1);
            } else {
                return pathLength(sample, node.right, depth + 1);
            }
        }

        // Reuse the cFactor from the outer class
        private static double cFactor(int n) {
            if (n <= 1) return 0.0;
            if (n == 2) return 1.0;
            double h = Math.log(n - 1) + 0.5772156649;
            return 2.0 * h - 2.0 * (n - 1.0) / n;
        }
    }

    // ===================================================================
    // Inner class: Tree Node
    // ===================================================================

    public static class Node implements Serializable {
        private static final long serialVersionUID = 1L;

        // Internal node fields
        final int featureIndex;
        final double splitValue;
        Node left;
        Node right;

        // External (leaf) node fields
        final boolean isLeaf;
        final int size;

        // Internal node constructor
        Node(int featureIndex, double splitValue) {
            this.featureIndex = featureIndex;
            this.splitValue = splitValue;
            this.isLeaf = false;
            this.size = 0;
        }

        // Leaf node constructor
        Node(int size) {
            this.featureIndex = -1;
            this.splitValue = 0;
            this.isLeaf = true;
            this.size = size;
        }
    }
}
