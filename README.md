# NoGinx

NoGinx is a Java application that watches NGINX access logs and identifies unusual
requests. When a request looks anomalous, the application can show a desktop
notification and send an email alert.

The application is written in Java 21 with Spring Boot and runs on port `8000`.
It also provides REST endpoints, so requests can be checked manually or by
another program.

## What this application does

For every new line written to an NGINX access log:

1. The log watcher reads the new line.
2. The parser reads fields such as IP address, request method, URL, status,
   response size, timestamp, and user agent.
3. The application converts text values into numeric values.
4. The Isolation Forest model decides whether the request is normal or anomalous.
5. Normal requests are ignored.
6. Anomalous requests create a desktop notification and an email alert when email
   is configured.

The application starts from the end of the log file. Existing older log entries
are not processed again. Only newly appended entries are considered.

## Simple project flow

```text
NGINX access.log
      |
      v
NginxLogWatcher
      |
      v
NginxLogParser
      |
      v
FeatureExtractor
      |
      v
EncoderMappings
      |
      v
PredictionService
      |
      v
IsolationForest
      |
      v
Normal or anomaly
      |
      v
Desktop notification + email
```

The REST API follows the same prediction service:

```text
HTTP client
  |
  v
PredictionController
  |
  v
PredictionService
  |
  v
IsolationForest
  |
  v
[{"anomaly":true}]
```

## Features

### REST API

The application provides:

| Method | URL | Purpose |
|---|---|---|
| `GET` | `/` | Check that the server is running |
| `POST` | `/predict` | Predict multiple feature records |
| `POST` | `/predict_one` | Predict one feature record |

### NGINX log monitoring

The watcher:

- Starts monitoring automatically.
- Watches the configured access log.
- Starts at the end of the file.
- Processes new complete lines.
- Continues processing when more lines are appended.
- Handles malformed lines without stopping.
- Handles file truncation.
- Handles file replacement or log rotation.
- Handles file deletion and recreation.
- Stops cleanly when the application shuts down.

### Alerts

For an anomalous request, NoGinx prepares:

```text
🚨 Anomaly Detected!

IP Address: <IP address>
Timestamp: <NGINX timestamp>
Path: <request path>
User Agent: <user agent>
```

Desktop alerts use Java AWT/SystemTray. Email alerts use Jakarta Mail and Gmail
SMTP STARTTLS.

Desktop notifications depend on operating-system support. Email alerts depend on
valid Gmail credentials. If either is unavailable, the application continues
running and records the reason in the logs.

### Model

The application uses an Isolation Forest model. It is designed to separate
unusual requests from the requests used for training.

The Java model is trained from:

```text
data/features.csv
```

The trained Java model is saved as:

```text
data/model.ser
```

Model and training paths can be changed through configuration.

## Requirements

Before running NoGinx, install:

- Java 21 or newer
- Maven 3.9 or newer
- Optional: NGINX for real access-log testing
- Optional: Gmail account with a Gmail App Password

Check Java and Maven:

```bash
java -version
mvn -version
```

## Project structure

```text
src/main/java/com/noginx/
├── NoGinxApplication.java       Application entry point
├── controller/                  REST endpoints and HTTP errors
├── email/                       SMTP email alerts
├── ml/                          Isolation Forest and model trainer
├── model/                       JSON and feature model classes
├── notification/                Desktop notifications and alert composition
├── parser/                      NGINX log parser
├── preprocessing/               Feature extraction and label mappings
├── service/                     Model loading and prediction
└── watcher/                     NGINX access-log watcher

src/main/resources/
├── application.properties       Runtime configuration
└── encoder_mappings.json        Method, path, and user-agent mappings

src/test/java/com/noginx/
├── controller/                  REST controller tests
├── email/                       Email test
├── notification/                Alert composition test
├── service/                     Prediction and model tests
├── watcher/                     Log watcher tests
└── PredictionApiIntegrationTest.java

data/
├── features.csv                 Training dataset
├── encoder_mappings.json        Same mappings used by preprocessing
└── model.ser                    Serialized Java model
```

## Configuration

Default settings are in [application.properties](src/main/resources/application.properties).

### NGINX log settings

```properties
noginx.watcher.enabled=true
noginx.watcher.log-path=/var/log/nginx/access.log
noginx.watcher.poll-interval-ms=1000
```

The log path can be overridden with `NGINX_ACCESS_LOG`:

```bash
NGINX_ACCESS_LOG=/tmp/noginx-access.log
```

The watcher is disabled with:

```properties
noginx.watcher.enabled=false
```

The polling interval is in milliseconds. The application uses `1000` milliseconds
by default, matching a one-second poll.

### Model settings

```properties
noginx.model-path=${user.dir}/data/model.ser
noginx.features-path=${user.dir}/data/features.csv
noginx.encoder-mappings-path=classpath:encoder_mappings.json
```

At startup:

1. The application looks for `data/model.ser`.
2. If it exists, the application loads it.
3. If it does not exist, it trains the model from `data/features.csv`.
4. It saves the trained model to `data/model.ser`.

Missing training data and unreadable model files cause a clear startup error.

### Email settings

Copy `.env.example` to `.env`:

```bash
cp .env.example .env
```

Configure:

```properties
NOGINX_MAIL_ID=sender@gmail.com
NOGINX_MAIL_PASSWORD="your gmail app password"
RECEIVER_MAIL_ID=receiver@example.com
```

Notes:

- `NOGINX_MAIL_ID` is the sender.
- `NOGINX_MAIL_PASSWORD` must be a Gmail App Password, not the normal account password.
- `RECEIVER_MAIL_ID` receives alerts.
- Gmail App Passwords often contain spaces, so quote the value.
- Never commit or share `.env`.

The current `.env` is ignored by Git.

You can disable email or desktop notifications:

```properties
noginx.email.enabled=false
noginx.notifications.enabled=false
```

## Build and run

### Run the test suite

```bash
mvn clean test
```

### Build the executable JAR

```bash
mvn package
```

The JAR is created in:

```text
target/noginx-1.0.0.jar
```

### Run directly

Export `.env` values:

```bash
set -a
source .env
set +a
java -jar target/noginx-1.0.0.jar
```

Or run through Maven:

```bash
set -a
source .env
set +a
mvn spring-boot:run
```

The server starts on:

```text
http://127.0.0.1:8000
```

## Test the REST API

### Health check

```bash
curl http://127.0.0.1:8000/
```

Result:

```json
{"message":"server is running live"}
```

### Predict one request

```bash
curl -H "Content-Type: application/json" \
  -d '{"feature":{"status":304,"size":0,"method":1,"path":0,"user_agent":54,"hour_of_day":8}}' \
  http://127.0.0.1:8000/predict_one
```

Result shape:

```json
[{"anomaly":true}]
```

### Predict multiple requests

```bash
curl -H "Content-Type: application/json" \
  -d '{"features":[{"status":304,"size":0,"method":1,"path":0,"user_agent":54,"hour_of_day":8},{"status":200,"size":490,"method":0,"path":0,"user_agent":40,"hour_of_day":8}]}' \
  http://127.0.0.1:8000/predict
```

Result shape:

```json
[{"anomaly":true},{"anomaly":true}]
```

### Test invalid input

```bash
curl -i -H "Content-Type: application/json" \
  -d '{"feature":{"status":304,"size":0}}' \
  http://127.0.0.1:8000/predict_one
```

The result is HTTP `400` with a JSON error describing the missing field.

## Test the watcher

Use a temporary log instead of the real NGINX log:

```bash
touch /tmp/noginx-access.log

set -a
source .env
set +a

java -jar target/noginx-1.0.0.jar \
  --noginx.watcher.log-path=/tmp/noginx-access.log \
  --noginx.watcher.poll-interval-ms=500
```

In a second terminal, append a valid combined-format log line:

```bash
echo '127.0.0.1 - - [21/Nov/2026:08:30:00 +0530] "GET /downloads/product_1?item=1 HTTP/1.1" 200 490 "-" "Mozilla/5.0"' \
  >> /tmp/noginx-access.log
```

What happens:

1. The watcher detects the appended line.
2. The parser extracts the fields.
3. The feature extractor encodes categorical values.
4. `PredictionService` runs the model.
5. Normal requests are ignored.
6. Anomalous requests trigger desktop and email alerts.

Stop the application with `Ctrl+C`.

## Test email alerts

1. Revoke any previously exposed App Password.
2. Create a fresh Gmail App Password.
3. Add it to `.env`.
4. Export `.env`.
5. Start the application.
6. Append an anomalous log line.
7. Check the application log and the recipient inbox or Spam folder.

If email is working, the application log includes:

```text
Email anomaly alert sent to <receiver>
```

If SMTP fails, the application logs the SMTP error but continues watching and
continues serving the REST API.

## Test desktop notifications

Desktop notifications require a graphical desktop session.

In headless environments, containers, SSH sessions, or CI:

```text
Desktop system notifications are unavailable on this platform
```

is expected. That is a platform limitation, not a watcher failure.

## Test suite

The test suite covers:

- REST endpoints.
- Invalid JSON and missing fields.
- Prediction service initialization.
- Model loading and training fallback.
- NGINX parser behavior.
- Feature extraction.
- NGINX log append handling.
- Truncation.
- File recreation.
- Malformed lines.
- Watcher shutdown.
- Alert message composition.
- Email configuration and MIME behavior.

Run:

```bash
mvn clean test
```

Current verification: 23 tests pass with 0 failures and 0 errors.

## Machine-learning limitation

The Java application does not load the original Python `.pkl` file directly.
It uses a custom Java Isolation Forest trained from `data/features.csv`.

The original scikit-learn model uses:

- 100 estimators.
- `max_samples=256`.
- `random_state=42`.
- No bootstrapping.
- Contamination mode `auto`.
- Offset `-0.5`.

A measured live comparison found:

- Python marks `{304,0,1,0,54,8}` normal.
- Java currently marks the same vector anomalous.
- Python marks `{200,490,0,0,40,8}` anomalous.

Consequently, exact numerical parity with the original `.pkl` model is not
claimed. The REST API, watcher, encodings, and alert workflow are implemented;
model parity remains an unresolved verification item.

## Troubleshooting

### Port 8000 is already in use

```bash
lsof -nP -iTCP:8000 -sTCP:LISTEN
```

Stop the process shown or run on another port:

```bash
java -jar target/noginx-1.0.0.jar --server.port=8001
```

### Email is not received

- Revoke and replace an exposed Gmail App Password.
- Check Gmail Spam and All Mail.
- Use a Gmail App Password.
- Quote App Password values containing spaces.
- Confirm sender, password, and receiver are exported.
- Check application logs for `Email anomaly alert failed`.

### Watcher does not process lines

- Confirm the path exists and is readable.
- Confirm `noginx.watcher.enabled=true`.
- Confirm the line is in combined-log format.
- Append a newline at the end.
- The watcher starts at EOF and intentionally ignores existing entries.

### Unknown encoder values

Unknown method, path, or user-agent values receive `-1`, matching the original
fallback behavior.

## Security

- Never commit `.env`.
- Never place credentials in `application.properties`.
- Revoke credentials that have been exposed in chat or source control.
- Restrict `.env` permissions:

```bash
chmod 600 .env
```

## Documentation

Additional project records:

- [Migration analysis](MIGRATION_ANALYSIS.md)
- [Original architecture](ORIGINAL_PROJECT_ARCHITECTURE.md)
- [Parity checklist](FEATURE_PARITY_CHECKLIST.md)
- [Handoff](COPILOT_HANDOFF.md)
- [Continuation prompt](COPILOT_CONTINUATION_PROMPT.md)
