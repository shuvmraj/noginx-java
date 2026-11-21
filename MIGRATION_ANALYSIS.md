# NoGinx – Migration Analysis

## Original Project: Python → Java Migration

**Repository:** https://github.com/AbhinavXJ/noginx  
**Migration Date:** 2026-11-21  
**Target:** Java 21 + Spring Boot 3.x

---

## Original Project Structure

```
noginx/
├── README.md
├── requirements.txt
├── .env (not committed)
├── assets/
│   ├── Figure_1.png
│   └── Figure_2.png
├── data/
│   ├── nginx.log                 # Raw NGINX log file (training data)
│   ├── nginx_logs.csv            # Parsed logs CSV
│   ├── features.csv              # Extracted features CSV (51,412 rows)
│   ├── encoder_mappings.json     # LabelEncoder mappings (19KB)
│   ├── model.pkl                 # Trained IsolationForest (scikit-learn)
│   ├── parse_logs.py             # Log parser (regex → CSV)
│   ├── process_csv.py            # Feature extractor + encoder
│   ├── train_model.py            # IsolationForest training script
│   └── plot_anomaly.py           # Anomaly visualization
├── fastapi_backend/
│   └── api_server.py             # FastAPI REST server
└── watcher/
    └── watcher.py                # Real-time log watcher + alerting
```

---

## Detailed Analysis of Each Python File

### 1. `data/parse_logs.py`

**Purpose:** Parse raw NGINX log file → CSV

**Regex Pattern:**
```
(?P<ip>\d+\.\d+\.\d+\.\d+).*?\[(?P<timestamp>[^\]]+)\]\s"(?P<method>\w+)\s(?P<url>\S+)\s(?P<http_version>[^"]+)"\s(?P<status>\d+)\s(?P<size>\d+)\s"[^"]*"\s"(?P<user_agent>[^"]*)"
```

**Output CSV columns:** `ip, timestamp, method, url, http_version, status, size, user_agent`

**Input:** `nginx.log` (raw log file)  
**Output:** `nginx_logs.csv`

---

### 2. `data/process_csv.py`

**Purpose:** Feature extraction + encoding from parsed CSV

**Steps:**
1. Read `nginx_logs.csv`
2. Extract `hour_of_day` from timestamp: `datetime.strptime(x.split()[0], "%d/%b/%Y:%H:%M:%S").hour`
3. Extract `path` from URL: `x.split('?')[0]` (strip query params)
4. Cast `status` and `size` to int
5. Apply `LabelEncoder` (scikit-learn) to `method`, `path`, `user_agent`
   - Encoding is alphabetically sorted (sklearn LabelEncoder default)
   - Mapping stored in `encoder_mappings.json`
6. Output: `features.csv` with columns `[status, size, method, path, user_agent, hour_of_day]`

**Encoder mappings structure:**
```json
{
  "method": {
    "classes": ["GET", "HEAD"],
    "mapping": {"GET": 0, "HEAD": 1}
  },
  "path": {
    "classes": ["/downloads/product_1", "/downloads/product_2", "/downloads/product_3"],
    "mapping": {"/downloads/product_1": 0, "/downloads/product_2": 1, "/downloads/product_3": 2}
  },
  "user_agent": {
    "classes": [...],
    "mapping": {...}
  }
}
```

---

### 3. `data/train_model.py`

**Purpose:** Train Isolation Forest model

**Parameters:**
```python
IsolationForest(contamination="auto", random_state=42)
```

**Input:** `features.csv` (all 6 features, 51,412 rows)  
**Output:** `model.pkl` (joblib pickle)

**Key notes:**
- `contamination="auto"` → uses 0.1 (10%) as contamination rate internally
- `random_state=42` → reproducible results
- No scaling (raw integer values used directly)
- All 6 features used together

---

### 4. `fastapi_backend/api_server.py`

**Purpose:** REST API server for predictions

**Framework:** FastAPI + Uvicorn

**Endpoints:**

#### GET `/`
- Returns: `{"message": "server is running live"}`

#### POST `/predict`
- Request: `{"features": [{"status": int, "size": int, "method": int, "path": int, "user_agent": int, "hour_of_day": int}]}`
- Loads features into DataFrame, converts to int, selects in `EXPECTED_FEATURES` order
- Calls `model.predict(df)` → returns -1 (anomaly) or 1 (normal)
- Response: `[{"anomaly": true/false}]`

#### POST `/predict_one`
- Request: `{"feature": {"status": int, "size": int, "method": int, "path": int, "user_agent": int, "hour_of_day": int}}`
- Same logic as `/predict` but for single record
- Response: `[{"anomaly": true/false}]`

**Anomaly logic:** `pred == -1` → anomaly=true

---

### 5. `watcher/watcher.py`

**Purpose:** Real-time NGINX log monitoring + alerting

**Log file:** `/var/log/nginx/access.log`

**Regex Pattern (different from parse_logs.py – supports IPv6):**
```
(?P<ip>[\d.:a-fA-F]+)\s+- - \[(?P<timestamp>[^\]]+)\]\s+"(?P<method>\w+)\s(?P<url>\S+)\s(?P<http_version>[^"]+)"\s(?P<status>\d+)\s(?P<size>\d+)\s"[^"]*"\s"(?P<user_agent>[^"]*)"
```

**Monitoring approach:** Seek to end of file on startup, then `readline()` in a loop with `time.sleep(1)` polling

**Feature extraction:**
1. Extract `hour_of_day` from timestamp
2. Extract `path` from URL (strip query params)
3. Cast `status`, `size` to int
4. Encode `method`, `path`, `user_agent` using `encoder_mappings.json`
5. Use fallback -1 for unknown values: `encode_with_fallback(value, dict)` → `dict.get(value, -1)`

**Prediction:** POST to `PREDICT_ONE_URL` (env var, default `http://127.0.0.1:8000/predict_one`)

**Alert workflow when anomaly detected:**
1. Send desktop notification via `plyer.notification.notify()`
   - title: "🚨 Anomaly Alert!"
   - message: IP, timestamp, path, user_agent
   - app_name: "noginx", timeout: 10, ticker: "Anomaly detected", toast: False
2. Send email via SMTP Gmail:
   - SMTP host: `smtp.gmail.com`, port: 587, STARTTLS
   - Login: `NOGINX_MAIL_ID`, `NOGINX_MAIL_PASSWORD`
   - Recipient: `RECEIVER_MAIL_ID`
   - Subject: "🚨 noginx Anomaly Detected"
   - Body: same message as notification (UTF-8)

**Environment variables:**
- `PREDICT_ONE_URL` = `http://127.0.0.1:8000/predict_one`
- `NOGINX_MAIL_ID` = sender Gmail address
- `NOGINX_MAIL_PASSWORD` = Gmail App Password
- `RECEIVER_MAIL_ID` = recipient email

---

## Dependencies (Python)

| Package | Version | Java Equivalent |
|---------|---------|-----------------|
| fastapi | 0.115.12 | Spring Boot 3.x |
| uvicorn | 0.34.3 | Embedded Tomcat (Spring Boot) |
| pydantic | 2.11.7 | Record/POJO + Jackson |
| pandas | 2.3.0 | Manual list/array processing |
| scikit-learn | 1.7.0 | Custom Isolation Forest implementation |
| joblib | 1.5.1 | Model serialization (Java serialization) |
| numpy | 2.2.6 | Standard Java arrays/math |
| plyer | 2.1.0 | Java system notifications (AWT/jnotify) |
| python-dotenv | 1.1.0 | Spring Boot `application.properties` + env vars |
| requests | 2.32.4 | Spring Boot RestTemplate / WebClient |
| watchdog | 6.0.0 | Java WatchService |
| smtplib (stdlib) | - | Jakarta Mail |
| email (stdlib) | - | Jakarta Mail MimeMessage |
| re (stdlib) | - | java.util.regex |
| csv (stdlib) | - | OpenCSV / Apache Commons CSV |
| json (stdlib) | - | Jackson ObjectMapper |

---

## Data Flow

```
nginx.log
    ↓ parse_logs.py (regex)
nginx_logs.csv
    ↓ process_csv.py (feature extraction + LabelEncoder)
features.csv + encoder_mappings.json
    ↓ train_model.py (IsolationForest)
model.pkl
    ↓ api_server.py (FastAPI)
REST API (/predict, /predict_one)
    ↑ 
watcher.py ──→ parse log line ──→ encode features ──→ POST /predict_one
                                                            ↓
                                                    if anomaly=true
                                                            ↓
                                              notification + email alert
```

---

## Key Technical Findings

### Isolation Forest Algorithm
- scikit-learn's implementation: `n_estimators=100` (default), `contamination="auto"`, `random_state=42`
- `contamination="auto"` sets threshold at `score_samples` offset of -0.5 (based on expected score of -0.5)
- Prediction: `predict()` returns 1 (inlier) or -1 (outlier)
- Must be reimplemented in Java with identical mathematical behavior

### Feature Encoding
- `method`: Only "GET"=0, "HEAD"=1 in training data
- `path`: Only 3 paths: "/downloads/product_1"=0, "/downloads/product_2"=1, "/downloads/product_3"=2
- `user_agent`: 157+ unique user agents, alphabetically sorted
- Unknown values → `-1` (fallback encoding)

### Watcher Polling Strategy
- Opens file, seeks to EOF
- Polls with `readline()` + `sleep(1)` - no inotify/watchdog used in watcher.py
- This is a simple polling approach to be reproduced in Java

### No UI/Frontend
- The original project has no web UI
- It is a backend service + CLI watcher
- Assets folder only contains matplotlib charts (Figure_1.png, Figure_2.png)
