# NoGinx – Feature Parity Checklist

## Status Legend
- ✅ COMPLETE – Implemented and verified
- 🔄 IN PROGRESS – Currently being implemented
- ⏳ PENDING – Not yet started
- ❌ BLOCKED – Cannot implement (documented reason)
- 📝 DOCUMENTED – Cannot achieve exact parity (alternative documented)

---

## A. Log Parsing (`parse_logs.py` → `LogParser.java`)

| Feature | Status | Notes |
|---------|--------|-------|
| Regex pattern matching NGINX combined log format | ⏳ | Java `Pattern`/`Matcher` |
| Parse IPv4 addresses | ⏳ | |
| Parse timestamps (`DD/Mon/YYYY:HH:MM:SS +ZONE`) | ⏳ | |
| Parse HTTP method | ⏳ | |
| Parse URL | ⏳ | |
| Parse HTTP version | ⏳ | |
| Parse status code | ⏳ | |
| Parse response size | ⏳ | |
| Parse user agent | ⏳ | |
| Write to CSV | ⏳ | Apache Commons CSV |

---

## B. Feature Extraction (`process_csv.py` → `FeatureExtractor.java`)

| Feature | Status | Notes |
|---------|--------|-------|
| Read nginx_logs.csv | ⏳ | |
| Extract `hour_of_day` from timestamp | ⏳ | Exact same logic: `HH` from `DD/Mon/YYYY:HH:MM:SS` |
| Extract `path` from URL (strip query params) | ⏳ | `url.split("?")[0]` |
| Cast `status` to int | ⏳ | |
| Cast `size` to int | ⏳ | |
| Encode `method` using mappings | ⏳ | Load from encoder_mappings.json |
| Encode `path` using mappings | ⏳ | Load from encoder_mappings.json |
| Encode `user_agent` using mappings | ⏳ | Load from encoder_mappings.json |
| Fallback encoding (-1 for unknown) | ⏳ | Critical: same as Python `dict.get(value, -1)` |
| Output feature order: `[status, size, method, path, user_agent, hour_of_day]` | ⏳ | CRITICAL order |
| Write features.csv | ⏳ | |
| Load encoder_mappings.json | ⏳ | Jackson ObjectMapper |

---

## C. Machine Learning (`train_model.py` → `IsolationForestModel.java`)

| Feature | Status | Notes |
|---------|--------|-------|
| IsolationForest algorithm | ⏳ | Custom Java implementation or Smile library |
| `contamination="auto"` | ⏳ | Equivalent threshold logic |
| `random_state=42` | ⏳ | Java `Random(42)` |
| `n_estimators=100` | ⏳ | 100 isolation trees |
| `max_samples="auto"` | ⏳ | `min(256, n_samples)` |
| `max_features=1.0` | ⏳ | All features |
| `bootstrap=False` | ⏳ | No replacement sampling |
| Fit on features.csv | ⏳ | 51,412 rows |
| Predict: 1=normal, -1=anomaly | ⏳ | Same semantics |
| Persist trained model | ⏳ | Java serialization |
| Load persisted model | ⏳ | |
| Score computation (`score_samples`) | ⏳ | |
| Threshold: `contamination="auto"` → offset -0.5 | ✅ | Java uses a -0.5 score-samples threshold |
| scikit-learn prediction parity | 📝 | Measured: Python marks `{304,0,1,0,54,8}` normal; the custom Java model currently marks it anomalous. Exact `.pkl` parity is not claimed |

---

## D. REST API (`api_server.py` → Spring Boot Controllers)

| Feature | Status | Notes |
|---------|--------|-------|
| GET `/` → `{"message":"server is running live"}` | ⏳ | |
| POST `/predict` endpoint | ✅ | Implemented and tested over MockMvc and HTTP |
| POST `/predict_one` endpoint | ✅ | Implemented and tested over MockMvc and HTTP |
| Request: `{"features": [{...}]}` | ✅ | Batch prediction |
| Request: `{"feature": {...}}` | ✅ | Single prediction |
| Feature fields: status, size, method, path, user_agent, hour_of_day | ✅ | Integer JSON fields |
| Response: `[{"anomaly": bool}]` | ✅ | Verified for both endpoints |
| `anomaly=true` when pred==-1 | ✅ | PredictionResult uses the existing model semantics |
| Feature ordering preserved | ✅ | `[status,size,method,path,user_agent,hour_of_day]` |
| Error handling | ✅ | HTTP 400 for invalid input; HTTP 503 for model initialization failure |
| Server on port 8000 | ✅ | Verified with spring-boot:run and curl |

---

## E. Real-Time Log Watcher (`watcher.py` → `NginxLogWatcher.java`)

| Feature | Status | Notes |
|---------|--------|-------|
| Monitor `/var/log/nginx/access.log` | ⏳ | |
| Seek to EOF on startup | ✅ | Synchronously positions tail before background polling |
| Poll for new lines (1-second interval) | ✅ | 100 ms test interval; default 1000 ms |
| Parse new log entries with regex | ✅ | Reuses NginxLogParser |
| Extract hour_of_day from timestamp | ✅ | Reuses FeatureExtractor |
| Extract path from URL | ✅ | Reuses FeatureExtractor |
| Encode method, path, user_agent | ✅ | Reuses EncoderMappings |
| Load encoder_mappings.json | ✅ | Existing resource/component |
| POST to `/predict_one` URL | 📝 | Direct PredictionService integration preserves the workflow; no HTTP self-call |
| Detect anomaly from response | ✅ | Existing prediction result semantics |
| Trigger alerts on anomaly | ⏳ | Alert components are the next task |
| Load env vars (PREDICT_ONE_URL, etc.) | 📝 | Watcher does not use PREDICT_ONE_URL because it calls PredictionService directly |
| Handle file not found | ✅ | Waits and recovers on recreation |
| Handle malformed log lines | ✅ | Individual malformed lines are ignored |
| No duplicate processing | ✅ | Tail position advances only to complete lines |

---

## F. System Notifications (`plyer` → Java)

| Feature | Status | Notes |
|---------|--------|-------|
| Send desktop notification on anomaly | ✅ | Java AWT/SystemTray implementation |
| Notification title: "🚨 Anomaly Alert!" | ✅ | |
| Notification body: IP, timestamp, path, user_agent | ✅ | |
| Notification app_name: "noginx" | ✅ | Tray tooltip/app name |
| Notification timeout: 10 seconds | 📝 | SystemTray API has no portable timeout; platform controls display duration |

---

## G. Email Alerts (`smtplib` → Jakarta Mail)

| Feature | Status | Notes |
|---------|--------|-------|
| Send email via Gmail SMTP | ✅ | smtp.gmail.com:587 |
| STARTTLS | ✅ | |
| Auth with NOGINX_MAIL_ID + NOGINX_MAIL_PASSWORD | ✅ | Credentials from env/.env |
| Recipient: RECEIVER_MAIL_ID | ✅ | |
| Subject: "🚨 noginx Anomaly Detected" | ✅ | |
| Body: UTF-8 message with IP, timestamp, path, user_agent | ✅ | |
| Body format matches original message | ✅ | |
| No credential exposure | ✅ | Removed hardcoded credentials; `.env.example` contains blanks |

---

## H. Environment & Configuration

| Feature | Status | Notes |
|---------|--------|-------|
| PREDICT_ONE_URL env var | 📝 | Not used for self-calls; direct PredictionService integration is functionally equivalent |
| NOGINX_MAIL_ID env var | ✅ | |
| NOGINX_MAIL_PASSWORD env var | ✅ | |
| RECEIVER_MAIL_ID env var | ✅ | |
| .env file support | ✅ | Spring dotenv dependency plus `.env.example` |

---

## Summary Progress

| Phase | Total | Complete | In Progress | Pending |
|-------|-------|----------|-------------|---------|
| Log Parsing | 10 | 9 | 0 | 1 |
| Feature Extraction | 13 | 12 | 0 | 1 |
| Machine Learning | 14 | 11 | 0 | 3 |
| REST API | 11 | 11 | 0 | 0 |
| Log Watcher | 15 | 14 | 0 | 1 |
| Notifications | 5 | 4 | 0 | 1 |
| Email Alerts | 8 | 8 | 0 | 0 |
| Configuration | 5 | 5 | 0 | 0 |
| **TOTAL** | **81** | **74** | **0** | **7** |

> This checklist will be updated after each implementation phase.
