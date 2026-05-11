# Testing Guide

How to verify the ReplyMind system end-to-end. Three test surfaces: the
backend (Python), the Android client, and the live wire-up between them.

## 1. Backend (pytest)

```bash
cd backend
source .venv/bin/activate
pytest -q
# 19 passed, 1 skipped
```

Covers:

| Test file                    | What it exercises |
|------------------------------|-------------------|
| `tests/test_api.py`          | FastAPI routes: `/healthz`, `/classify`, `/profile`, `/history/sync`, bearer-auth required on protected endpoints |
| `tests/test_classifier.py`   | End-to-end classify path on the trained model — sanity checks for urgent / work / promotional / spam / family-friends inputs, plus the confidence-gated backstop |
| `tests/test_rag.py`          | ChromaDB cold start, per-sender retrieval, per-user isolation |
| `tests/test_data_mapping.py` | Dataset loader smoke tests (sources reachable, per-category cap honoured, stratified split keeps class distribution) |

If a test is skipped, it's because the trained model artifact isn't on
disk yet — run `python -m backend.train` first.

## 2. Android (Gradle unit tests)

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDefaultDebugUnitTest
# 442 tests passing
```

Most relevant suites for this project:

| Test class                                | What it exercises |
|-------------------------------------------|-------------------|
| `BackendMessageClassifierTest`            | Happy path, not-configured, misconfigured, HTTP error, network error, unknown-category fallback |
| `CategoryActionRouterTest`                | Routes each category to the correct action (escalate / template / suppress / default) |
| `PreferencesManagerTest`                  | Backend / classification / snippet flags default correctly with and without baked `.env` values |
| `MessageLogTest`                          | Room entity nullability after the v3 schema migration |

## 3. End-to-end on a real device

Pre-requisites: backend already trained (`models/classifier.pt` exists),
phone in developer mode with USB debugging, `.env` populated.

### Start the backend

```bash
cd backend && uvicorn backend.main:app --host 0.0.0.0 --port 8765 &
curl -H "Authorization: Bearer $(grep DEMO_TOKEN .env | cut -d= -f2)" \
     http://127.0.0.1:8765/healthz
# expect {"ok":true,"model_loaded":true,"chroma_ready":true}
```

### Connect the phone

```bash
adb devices                                  # confirm phone is listed
adb -s <serial> reverse tcp:8765 tcp:8765    # forward laptop port to phone
```

### Install and configure

```bash
./gradlew assembleDefaultDebug
adb -s <serial> install -r app/build/outputs/apk/Default/debug/app-Default-debug.apk
adb -s <serial> shell cmd notification allow_listener \
    com.parishod.watomatic/com.parishod.watomatic.service.NotificationService
```

### Watch traffic during the test

```bash
adb -s <serial> logcat -v time | grep -iE "okhttp|classify|Automatic AI|backend"
```

### What to send

| Message                                            | Expected category | Expected action |
|----------------------------------------------------|--------------------|-----------------|
| "want to grab dinner tonight?"                     | family_friends     | reply (warm)    |
| "Please send the Q1 report by EOD"                 | work               | reply (template)|
| "URGENT: warehouse on fire, call 911"              | urgent             | no reply (escalate) |
| "50% OFF! Click to claim your prize"               | promotional / spam | no reply (suppress) |

Each successful classify shows up in logcat as:

```
--> POST http://127.0.0.1:8765/classify
<-- 200 OK ... (28ms)
Classification: family_friends conf=0.84 — ...
Automatic AI: using backend-suggested reply
```

Open the ReplyMind Inbox tab on the device to confirm the classification
metadata was persisted.

## 4. Cascade fallback test

Verifies that turning off the backend mid-conversation doesn't break
auto-reply.

```bash
# In the terminal running uvicorn:
Ctrl-C                                       # or `kill $(cat /tmp/uvicorn.pid)`
```

Send another message from the test sender. Expected behaviour:

- Logcat: `backend non-2xx` or `backend call failed`
- Logcat: `delegateToFallback` followed by the BYOK / template path
- Phone: still receives an auto-reply, just from the next-tier classifier

Restart uvicorn → next message goes back through the backend path.

## 5. Latency benchmark

```bash
cd backend
python -m backend.eval_latency --base-url http://127.0.0.1:8765 --n 200
```

Writes `models/eval_latency.json` with p50 / p95 / p99 split by cold-start
vs warm. Current readings on a 2024 MacBook Pro (CPU-only):

```
p50  12 ms
p95  43 ms
p99 138 ms
```
