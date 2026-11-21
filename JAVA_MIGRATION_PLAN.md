# NoGinx – Java Migration Plan

## Technology Mapping

| Python Component | Java Equivalent | Rationale |
|-----------------|-----------------|-----------|
| FastAPI | Spring Boot 3.3.x | Drop-in REST server replacement |
| Uvicorn | Embedded Tomcat (Spring Boot) | Auto-included |
| Pydantic models | Java Records + Jackson | Same JSON validation |
| pandas DataFrame | Java arrays / List<Map> | Simple feature processing |
| scikit-learn IsolationForest | Custom Java IF impl / Smile ML | No exact Java equivalent of sklearn |
| joblib pickle | Java ObjectOutputStream serialization | Custom model serialization |
| numpy | Standard Java math | Simple array ops only |
| plyer notifications | Java AWT SystemTray / Desktop | Cross-platform desktop notification |
| smtplib | Jakarta Mail (Jakarta EE) | Direct SMTP equivalent |
| python-dotenv | Spring Boot env var binding | Native support |
| watchdog / polling | Java WatchService / Thread polling | Equivalent file monitoring |
| re (regex) | java.util.regex.Pattern | Direct equivalent |
| csv module | Apache Commons CSV | Full CSV support |
| json module | Jackson ObjectMapper | Full JSON support |

---

## Dependency Strategy: Isolation Forest

The original uses `sklearn.ensemble.IsolationForest` with `contamination="auto"` and `random_state=42`.

**Options evaluated:**
1. **Smile ML library** – Has `IsolationForest` implementation in Java. Available on Maven. ✅ CHOSEN
   - `com.github.haifengl:smile-core:3.1.1`
   - Supports fit + predict
   - Compatible with Java 21
2. **Weka** – Has outdated implementations, less suitable
3. **Custom implementation** – Too risky for parity, avoid

**Smile IsolationForest API:**
```java
smile.anomaly.IsolationForest model = smile.anomaly.IsolationForest.fit(data, ntrees, maxSamples);
double[] scores = model.score(sample);
// score < threshold → anomaly
```

**Note:** Smile's scoring may differ slightly from sklearn. We will:
1. Train on the same `features.csv` dataset
2. Use identical parameters (n_trees=100, maxSamples=256)
3. Calibrate threshold to match sklearn's `contamination="auto"` behavior
4. Document any numerical differences

---

## Maven Dependency Plan

```xml
<!-- Spring Boot -->
<dependency>spring-boot-starter-web:3.3.6</dependency>
<dependency>spring-boot-starter-mail:3.3.6</dependency>  <!-- Jakarta Mail -->

<!-- JSON -->
<dependency>jackson-databind (via spring-boot)</dependency>

<!-- CSV -->
<dependency>commons-csv:1.11.0</dependency>

<!-- ML -->
<dependency>smile-core:3.1.1</dependency>
<dependency>smile-nlp:3.1.1</dependency>

<!-- Logging -->
<dependency>slf4j-api (via spring-boot)</dependency>
<dependency>logback-classic (via spring-boot)</dependency>

<!-- HTTP Client (for watcher → API calls) -->
<dependency>spring-boot-starter-web (RestTemplate)</dependency>

<!-- Env vars -->
<dependency>spring-dotenv:4.0.0</dependency>  <!-- for .env file support -->
```

---

## Project Structure

```
noginx-java/
├── pom.xml
├── README.md
├── .env.example
├── MIGRATION_ANALYSIS.md
├── ORIGINAL_PROJECT_ARCHITECTURE.md
├── FEATURE_PARITY_CHECKLIST.md
├── JAVA_MIGRATION_PLAN.md
├── COPILOT_HANDOFF.md
│
├── src/
│   ├── main/
│   │   ├── java/com/noginx/
│   │   │   ├── NoGinxApplication.java          # Main Spring Boot app
│   │   │   ├── config/
│   │   │   │   ├── AppConfig.java              # Bean config
│   │   │   │   └── EncoderMappingsConfig.java  # Load encoder_mappings.json
│   │   │   ├── controller/
│   │   │   │   └── PredictionController.java   # /predict, /predict_one, GET /
│   │   │   ├── service/
│   │   │   │   └── PredictionService.java      # Model loading + prediction logic
│   │   │   ├── ml/
│   │   │   │   ├── IsolationForestModel.java   # Wrapper around Smile IF
│   │   │   │   └── ModelTrainer.java           # Train + save model
│   │   │   ├── parser/
│   │   │   │   └── NginxLogParser.java         # Regex log parsing
│   │   │   ├── preprocessing/
│   │   │   │   ├── FeatureExtractor.java       # CSV → features
│   │   │   │   └── EncoderMappings.java        # Load + apply mappings
│   │   │   ├── watcher/
│   │   │   │   └── NginxLogWatcher.java        # Real-time log monitor
│   │   │   ├── notification/
│   │   │   │   └── NotificationService.java    # Desktop notifications
│   │   │   ├── email/
│   │   │   │   └── EmailAlertService.java      # Jakarta Mail SMTP
│   │   │   ├── model/
│   │   │   │   ├── FeatureRecord.java          # Single feature vector
│   │   │   │   ├── FeaturesRequest.java        # /predict request
│   │   │   │   ├── FeatureRequest.java         # /predict_one request
│   │   │   │   ├── PredictionResult.java       # {"anomaly": bool}
│   │   │   │   └── ParsedLogEntry.java         # Parsed NGINX log line
│   │   │   └── util/
│   │   │       └── CsvUtils.java               # CSV read/write utilities
│   │   │
│   │   └── resources/
│   │       ├── application.properties          # Spring Boot config
│   │       └── encoder_mappings.json           # Copied from original
│   │
│   └── test/java/com/noginx/
│       ├── NginxLogParserTest.java
│       ├── FeatureExtractorTest.java
│       ├── EncoderMappingsTest.java
│       ├── IsolationForestModelTest.java
│       ├── PredictionControllerTest.java
│       └── NginxLogWatcherTest.java
│
└── data/
    ├── encoder_mappings.json    # Original mappings
    ├── features.csv             # Training data
    └── model.ser                # Serialized Java model
```

---

## Implementation Phases

### Phase 1: Repository Analysis ✅ COMPLETE
- Analyzed all Python source files
- Created migration documents
- Fetched training data

### Phase 2: Java Project Setup 🔄 NEXT
- Create pom.xml with all dependencies
- Create NoGinxApplication.java
- Configure application.properties
- Set up package structure

### Phase 3: Data Models
- Create all Java record/POJO classes
- Match JSON structures exactly

### Phase 4: Encoding
- Load encoder_mappings.json
- Implement encode_with_fallback logic

### Phase 5: Log Parser
- Implement NGINX log regex parser
- Match both parse_logs.py and watcher.py patterns

### Phase 6: ML Model
- Implement IsolationForest using Smile library
- Train on features.csv
- Serialize trained model
- Validate predictions

### Phase 7: REST API
- Implement GET /, POST /predict, POST /predict_one
- Match exact request/response JSON

### Phase 8: Log Watcher
- Implement real-time file monitoring
- Connect to prediction API
- Handle edge cases

### Phase 9: Notifications + Email
- Desktop notification via Java AWT
- Email via Jakarta Mail

### Phase 10: Integration + Testing
- End-to-end test
- Feature parity validation

---

## Critical Implementation Notes

### 1. Feature Order
ALWAYS process features in this exact order:
```java
static final String[] EXPECTED_FEATURES = {"status", "size", "method", "path", "user_agent", "hour_of_day"};
```

### 2. Anomaly Semantics
```java
boolean isAnomaly = (prediction == -1);  // Exactly as in Python
```

### 3. Fallback Encoding
```java
// Python: dict.get(value, -1)
// Java equivalent:
int encoded = mappings.getOrDefault(value, -1);
```

### 4. Timestamp Parsing
```java
// Python: datetime.strptime(x.split()[0], "%d/%b/%Y:%H:%M:%S").hour
// Java:
DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss", Locale.ENGLISH);
LocalDateTime dt = LocalDateTime.parse(timestampPart, fmt);
int hourOfDay = dt.getHour();
```

### 5. Path Extraction
```java
// Python: url.split('?')[0]
// Java:
String path = url.split("\\?")[0];
```

### 6. Watcher Polling
```java
// Python: file.seek(0, os.SEEK_END) then readline() + sleep(1)
// Java: RandomAccessFile, seek to end, poll readline()
// OR: BufferedReader with continuous read + Thread.sleep(1000)
```

### 7. SMTP Configuration
```java
// smtp.gmail.com:587, STARTTLS
Properties props = new Properties();
props.put("mail.smtp.auth", "true");
props.put("mail.smtp.starttls.enable", "true");
props.put("mail.smtp.host", "smtp.gmail.com");
props.put("mail.smtp.port", "587");
```

### 8. Server Port
```properties
# application.properties
server.port=8000  # Match original uvicorn port
```
