# NoGinx - Java Migration

NoGinx is a Java 21/Spring Boot 3 migration of the original Python NGINX anomaly detector.

## Run

```bash
mvn clean test
mvn package
mvn spring-boot:run
```

The server listens on port `8000`.

## Configuration

Copy `.env.example` to `.env` and configure credentials:

```bash
cp .env.example .env
```

Supported settings:

- `NGINX_ACCESS_LOG` or `noginx.watcher.log-path`
- `PREDICT_ONE_URL` for compatibility/documentation; the watcher uses `PredictionService` directly
- `NOGINX_MAIL_ID`
- `NOGINX_MAIL_PASSWORD`
- `RECEIVER_MAIL_ID`
- `noginx.watcher.enabled`
- `noginx.notifications.enabled`
- `noginx.email.enabled`
- `noginx.model-path`
- `noginx.features-path`

Do not commit real credentials.

The Gmail App Password may contain spaces. Keep it quoted in `.env`:

```properties
NOGINX_MAIL_PASSWORD="xxxx xxxx xxxx xxxx"
```

Run the application with `.env` exported:

```bash
set -a
source .env
set +a
java -jar target/noginx-1.0.0.jar
```

## Endpoints

- `GET /`
- `POST /predict`
- `POST /predict_one`

Both prediction endpoints preserve the original JSON shapes.

## Alert behavior

The watcher sends anomalies to `SystemNotificationService` and `EmailAlertService`.
Desktop notifications use Java AWT/SystemTray. Email alerts use Jakarta Mail with
Gmail SMTP STARTTLS. Missing credentials or unavailable desktop notification
support cause a logged skip, not a watcher failure.

## ML status

The original scikit-learn model is available as `model.pkl`, but Java does not
load it directly. The Java runtime uses a serialized custom Isolation Forest
trained from `data/features.csv`. The scikit-learn parameters are 100 estimators,
`max_samples=256`, `random_state=42`, and contamination `auto`. Exact numerical
parity has not been verified, and the custom implementation remains subject to
random-subsampling and tree-construction differences.

Measured reference comparison on 2026-11-21:

- Python/scikit-learn predicts `{304,0,1,0,54,8}` as normal.
- The custom Java model currently predicts that vector as anomalous.
- Python/scikit-learn predicts `{200,490,0,0,40,8}` as anomalous.

The Java model therefore is not numerically 100% equivalent to the original
`.pkl` model. Its REST and watcher integrations are complete, but parity remains
an explicit unresolved verification item.

Measured live reference result: the original scikit-learn model predicts
`{304,0,1,0,54,8}` as normal, while the custom Java model currently predicts
it as anomalous. Python marks `{200,490,0,0,40,8}` anomalous. The Java/model
parity result is therefore not 100%; the custom model is not a direct loader
for the original `.pkl`.
