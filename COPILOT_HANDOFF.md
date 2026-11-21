# COPILOT HANDOFF - NoGinx Java Migration

## Project overview

NoGinx is an NGINX anomaly detection application migrated from Python/FastAPI to Java 21/Spring Boot 3.3.6. REST, watcher, desktop notification, email alert, and integration surfaces are implemented. Scikit-learn numerical parity remains partially unverified.

## Completed implementation

- Spring Boot application entry point.
- Feature and request/response models.
- NGINX combined-log parser.
- Feature extraction and encoder mappings.
- Custom Java Isolation Forest and Java serialization.
- CSV model trainer.
- `GET /`
- `POST /predict`
- `POST /predict_one`
- Required JSON field validation and malformed JSON handling.
- Startup model loading/training fallback.
- JUnit 5 controller, service, and Spring integration tests.
- Real-time NGINX tail watcher with EOF startup, append detection, truncation, rotation/replacement, deletion/recreation, and graceful shutdown.
- Watcher integration tests for append, truncation, recreation, malformed lines, and shutdown.
- Desktop and email alert services with failure isolation.
- Alert body/subject parity and UTF-8 MIME tests.

## REST API

| Endpoint | Request | Response |
|---|---|---|
| `GET /` | None | `{"message":"server is running live"}` |
| `POST /predict` | `{"features":[FeatureRecord,...]}` | `[{"anomaly":boolean},...]` |
| `POST /predict_one` | `{"feature":FeatureRecord}` | `[{"anomaly":boolean}]` |

Feature order for model input is `[status,size,method,path,user_agent,hour_of_day]`.

## Model initialization

`PredictionService` initializes once through `@PostConstruct`.

1. Load `data/model.ser` when it exists.
2. Otherwise train `data/features.csv` with `ModelTrainer`.
3. Save the trained Java object to `data/model.ser`.

The model path is configurable with `noginx.model-path`. Training data is configurable with `noginx.features-path`. Missing training data or an unreadable persisted model produces a clear startup error.

The persisted file is a serialized custom Java Isolation Forest, not the original Python `.pkl` model.

## Files added or modified

- `src/main/java/com/noginx/controller/PredictionController.java`
- `src/main/java/com/noginx/controller/ApiExceptionHandler.java`
- `src/main/java/com/noginx/service/PredictionService.java`
- `src/main/java/com/noginx/model/FeatureRecord.java`
- `src/main/java/com/noginx/parser/NginxLogParser.java`
- `src/main/resources/encoder_mappings.json`
- `data/encoder_mappings.json`
- `data/model.ser`
- `src/test/java/com/noginx/PredictionApiIntegrationTest.java`
- `src/test/java/com/noginx/controller/PredictionControllerTest.java`
- `src/test/java/com/noginx/service/PredictionServiceTest.java`
- `src/main/java/com/noginx/watcher/NginxLogWatcher.java`
- `src/test/java/com/noginx/watcher/NginxLogWatcherTest.java`

The parser was corrected for Java regex named groups. The encoder resources were corrected to quote the `NaN` user-agent class as a JSON string.

## Tests and runtime verification

`mvn clean test` passed: 23 tests, 0 failures, 0 errors. This includes 6 watcher tests, 2 Spring integration tests, 6 controller tests, 5 prediction-service tests, 3 email tests, and 1 alerting test.

Verified Spring runtime:

- `mvn spring-boot:run`
- `GET /` returned `{"message":"server is running live"}`
- Batch `/predict` returned two prediction objects.
- `/predict_one` returned one prediction object.
- Missing fields returned HTTP 400 with `{"error":"Feature 'method' is required."}`

Verified email runtime:

- The `.env` App Password line is now quoted and `.env` permissions are `600`.
- A live anomaly log line triggered `EmailAlertService`.
- The application logged `Email anomaly alert sent to shubhamraj.v16@gmail.com`.
- Real SMTP delivery was therefore confirmed by JavaMail; the exact inbox/Spam folder is controlled by Gmail.

## Isolation Forest concerns

The existing implementation is a custom Java model. It has not been compared numerically with a scikit-learn `.pkl` model. The custom random subsampling/scoring and `-0.5` threshold semantics must be validated against Python before parity can be claimed.

Measured live reference on 2026-11-21:

- Python/scikit-learn marks `{304,0,1,0,54,8}` normal and `{200,490,0,0,40,8}` anomalous.
- The custom Java model currently marks both vectors anomalous in the live executable JAR.
- The Java score formula has been corrected to scikit-learn's `-2^(-average_path_length/c(n))` form; the remaining difference is attributable to custom random subsampling/tree construction, not to the REST or watcher pipeline.

## Pending implementation

- Numerical parity comparison against the original scikit-learn `.pkl` model.
- Real NGINX/SMTP/system-tray environment verification.
- Python-independent normal operation is otherwise complete.

## Exact next task
Complete scikit-learn numerical parity validation and update the checklist with measured parity evidence. Real SMTP and desktop tray behavior require credentials and a desktop-capable environment.
