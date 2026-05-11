# ReplyMind Classification Backend

Self-hosted FastAPI service that classifies inbound messages for the Watomatic /
ReplyMind Android client. Uses a sentence-transformer encoder (`all-MiniLM-L6-v2`)
+ a 2-layer MLP head, with per-user retrieval-augmented context backed by ChromaDB.

## Layout

```
backend/                Python package
  main.py               FastAPI app + routes
  config.py             env-driven settings
  auth.py               Bearer-token dependency
  schemas.py            Pydantic request / response models
  db.py                 SQLAlchemy (profiles + audit log)
  vector_store.py       ChromaDB wrapper (per-user collections)
  rag.py                retrieve_and_combine — weighted RAG fusion
  classifier.py         embed → RAG → MLP → category orchestration
  train.py              CLI training pipeline
  eval.py               Frozen-test eval → models/eval_report.json
  eval_latency.py       End-to-end p50/p95 against running uvicorn
  models/               nn.Module + embedder loader
  data/                 dataset stitcher + per-source loaders
tests/                  pytest (api / classifier / rag / data mapping)
data/                   generated CSVs + chroma + sqlite (gitignored)
models/                 trained .pt + label_map.json + reports (gitignored)
```

## Setup

```bash
python3.12 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env       # set DEMO_TOKEN
```

## Build the corpus + train

```bash
python -m backend.data.build      # downloads + stitches the 5 public datasets
python -m backend.train           # writes models/classifier.pt
python -m backend.eval            # writes models/eval_report.json
```

## Run

```bash
uvicorn backend.main:app --host 0.0.0.0 --port 8765
curl -H "Authorization: Bearer $DEMO_TOKEN" http://127.0.0.1:8765/healthz
# {"ok":true,"model_loaded":true,"chroma_ready":true}
```

For the Android emulator the host laptop is reachable at `http://10.0.2.2:8765`.
For a physical device on the same WiFi, use the laptop LAN IP, or
`adb reverse tcp:8765 tcp:8765` to map the device's `localhost` to the laptop.

## Latency benchmark

```bash
python -m backend.eval_latency --base-url http://127.0.0.1:8765 --n 200
```

Writes `models/eval_latency.json` with p50 / p95 / p99 split by cold-start vs warm
and a confusion matrix on the sampled rows.

## Tests

```bash
pytest -q
```

## Datasets and licences

See `backend/data/mapping.md` for per-source category mapping rules, downsampling
logic, and licence disclosure. All datasets are downloaded at build time and
written to a gitignored `data/raw/` — none are redistributed in this repo.
