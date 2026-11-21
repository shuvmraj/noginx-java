# NoGinx – Original Project Architecture

## System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                        NoGinx System                                │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                  OFFLINE PIPELINE (One-time)                  │
│  │                                                              │  │
│  │  nginx.log ──► parse_logs.py ──► nginx_logs.csv             │  │
│  │                                        │                    │  │
│  │                                        ▼                    │  │
│  │                              process_csv.py                  │  │
│  │                              ├── features.csv               │  │
│  │                              └── encoder_mappings.json      │  │
│  │                                        │                    │  │
│  │                                        ▼                    │  │
│  │                              train_model.py                  │  │
│  │                              └── model.pkl                  │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  ┌────────────────────────┐    ┌──────────────────────────────────┐ │
│  │   RUNTIME: FastAPI     │    │      RUNTIME: Watcher            │ │
│  │   api_server.py        │◄───│      watcher.py                  │ │
│  │                        │    │                                  │ │
│  │  GET  /                │    │  - Opens access.log              │ │
│  │  POST /predict         │    │  - Seeks to EOF                  │ │
│  │  POST /predict_one     │    │  - Polls readline() + sleep(1s)  │ │
│  │                        │    │  - Parses line with regex        │ │
│  │  Loads model.pkl       │    │  - Encodes features              │ │
│  │  Uses DataFrame        │    │  - POSTs to /predict_one         │ │
│  │  Returns anomaly bool  │    │                                  │ │
│  └────────────────────────┘    │  On anomaly=true:                │ │
│                                │  ├── plyer.notification.notify() │ │
│                                │  └── smtplib Gmail email         │ │
│                                └──────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

## Component Responsibilities

### Component 1: Log Parser (`parse_logs.py`)
- **Input:** Raw NGINX access log (`nginx.log`)
- **Process:** Regex extraction of fields
- **Output:** Structured CSV (`nginx_logs.csv`)
- **Runs:** Once, to prepare training data

### Component 2: Feature Processor (`process_csv.py`)
- **Input:** `nginx_logs.csv`
- **Process:** 
  - Timestamp → hour_of_day
  - URL → path (strip query string)
  - LabelEncode: method, path, user_agent
- **Output:** `features.csv`, `encoder_mappings.json`
- **Runs:** Once, to prepare training data

### Component 3: Model Trainer (`train_model.py`)
- **Input:** `features.csv`
- **Process:** Fit IsolationForest
- **Output:** `model.pkl`
- **Runs:** Once (or when retraining)

### Component 4: REST API (`api_server.py`)
- **Input:** HTTP POST requests with feature vectors
- **Process:** Load model, predict anomalies
- **Output:** JSON `[{"anomaly": bool}]`
- **Runs:** Continuously (long-running server)
- **Port:** 8000 (default uvicorn)

### Component 5: Log Watcher (`watcher.py`)
- **Input:** Live NGINX access log
- **Process:** Parse → Encode → Predict → Alert
- **Output:** Desktop notifications + emails
- **Runs:** Continuously (long-running process)

## Runtime Dependencies

```
watcher.py ──→ PREDICT_ONE_URL (api_server.py must be running)
watcher.py ──→ encoder_mappings.json (file must exist)
api_server.py ──→ model.pkl (file must exist)
```

## Process Startup Order

1. Start `api_server.py` (FastAPI)
2. Start `watcher.py` (Log Watcher)
3. NGINX generates logs → watcher detects → alerts sent

## Data Formats

### NGINX Log Format (Combined Log)
```
IP - - [DD/Mon/YYYY:HH:MM:SS +ZONE] "METHOD /path?query HTTP/1.1" STATUS SIZE "referer" "user_agent"
```

### Feature Vector (Prediction Input)
```json
{
  "status": 304,     // HTTP status code (integer, raw)
  "size": 0,         // Response size in bytes (integer, raw)
  "method": 1,       // HTTP method (LabelEncoded integer)
  "path": 0,         // URL path (LabelEncoded integer)
  "user_agent": 54,  // User agent string (LabelEncoded integer)
  "hour_of_day": 8   // Hour of request (0-23)
}
```

### EXPECTED_FEATURES Order
```python
["status", "size", "method", "path", "user_agent", "hour_of_day"]
```
This order is CRITICAL for correct predictions.

## Environment Configuration

```bash
PREDICT_ONE_URL=http://127.0.0.1:8000/predict_one
NOGINX_MAIL_ID=sender@gmail.com
NOGINX_MAIL_PASSWORD=xxxx xxxx xxxx xxxx  # Gmail App Password
RECEIVER_MAIL_ID=receiver@gmail.com
```

## Alert Message Format

```
🚨 Anomaly Detected!

IP Address: {ip}
Timestamp: {timestamp}
Path: {path}
User Agent: {user_agent}
```

## IsolationForest Parameters

| Parameter | Value |
|-----------|-------|
| n_estimators | 100 (default) |
| contamination | "auto" |
| random_state | 42 |
| max_samples | "auto" (min(256, n_samples)) |
| max_features | 1.0 (use all features) |
| bootstrap | False (default) |

## LabelEncoder Behavior

- Encodes string labels to integers 0 to n-1
- Sorted alphabetically by default
- `classes_` attribute stores sorted unique values
- Unknown values: `dict.get(value, -1)` → returns -1 if not found

## No UI

The original project has NO web frontend or graphical interface.
- `assets/Figure_1.png` and `Figure_2.png` are matplotlib plot outputs (offline charts)
- The only user interaction is:
  - Running FastAPI server via CLI
  - Running watcher via CLI
  - Receiving desktop notifications
  - Receiving email alerts
