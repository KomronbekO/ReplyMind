"""End-to-end latency benchmark against a running backend instance.

Picks a sample of rows from the frozen test set, POSTs each to /classify on
a running uvicorn, records server-reported latency and wall-clock round-trip,
and reports p50 / p95 / p99 broken down by cold-start vs warm and per category.

Run:
    uvicorn backend.main:app --host 127.0.0.1 --port 8765 &
    python -m backend.eval_latency --base-url http://127.0.0.1:8765 --n 200
"""
from __future__ import annotations

import argparse
import json
import os
import random
import statistics
import sys
import time
from pathlib import Path

import pandas as pd
import requests

from .config import get_settings
from .data._common import DATA_ROOT


CATEGORY_LIST = [
    {"id": "urgent", "name": "Urgent", "description": "time critical"},
    {"id": "work", "name": "Work", "description": "work related"},
    {"id": "family_friends", "name": "Family & Friends", "description": "people you know"},
    {"id": "promotional", "name": "Promotional", "description": "ads, offers, sales"},
    {"id": "spam", "name": "Spam", "description": "unsolicited"},
    {"id": "other", "name": "Other", "description": "miscellaneous"},
]


def _percentile(xs: list[float], pct: float) -> float:
    if not xs:
        return 0.0
    s = sorted(xs)
    k = max(0, min(len(s) - 1, int(round(pct * (len(s) - 1)))))
    return s[k]


def run(base_url: str, n: int, seed: int) -> dict:
    settings = get_settings()
    token = settings.demo_token
    test_path = DATA_ROOT / "test.csv"
    if not test_path.exists():
        raise SystemExit(f"missing {test_path}; run `python -m backend.data.build` first")
    df = pd.read_csv(test_path).sample(n=min(n, 10_000), random_state=seed).reset_index(drop=True)
    if len(df) > n:
        df = df.head(n)

    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    profile = {
        "display_name": "Bench",
        "occupation": "",
        "tone": "CASUAL",
        "working_hours_start_min": 540,
        "working_hours_end_min": 1080,
        "communication_style": "",
        "key_relationships": [],
        "additional_context": "",
    }

    server_latencies: list[int] = []
    wall_latencies: list[float] = []
    cold_latencies: list[int] = []
    warm_latencies: list[int] = []
    correct = 0
    total = 0
    confusion: dict[str, dict[str, int]] = {}

    # Use a fresh per-run user_id so the per-user vector store starts cold and
    # we can measure the cold→warm transition cleanly.
    user_id = f"bench-{seed}-{int(time.time())}"

    print(f"benchmarking {len(df)} rows against {base_url} (user_id={user_id})...")
    t_run_start = time.time()
    for i, row in df.iterrows():
        body = {
            "user_id": user_id,
            "sender": f"sender-{i % 7}",
            "package": "bench",
            "message": str(row["text"]),
            "profile": profile,
            "categories": CATEGORY_LIST,
        }
        t0 = time.perf_counter()
        resp = requests.post(f"{base_url}/classify", headers=headers, json=body, timeout=10)
        t1 = time.perf_counter()
        if resp.status_code != 200:
            print(f"  row {i}: HTTP {resp.status_code} — {resp.text[:120]}", file=sys.stderr)
            continue
        data = resp.json()
        server_ms = int(data.get("latency_ms", 0))
        wall_ms = (t1 - t0) * 1000.0
        server_latencies.append(server_ms)
        wall_latencies.append(wall_ms)
        (cold_latencies if data.get("cold_start") else warm_latencies).append(server_ms)
        true_cat = str(row["category"])
        pred_cat = str(data.get("category_id"))
        total += 1
        if true_cat == pred_cat:
            correct += 1
        confusion.setdefault(true_cat, {}).setdefault(pred_cat, 0)
        confusion[true_cat][pred_cat] += 1
    t_run_end = time.time()

    report = {
        "base_url": base_url,
        "n_sent": int(total),
        "wall_seconds": round(t_run_end - t_run_start, 2),
        "accuracy": round(correct / total, 4) if total else 0.0,
        "server_latency_ms": {
            "p50": _percentile(server_latencies, 0.50),
            "p95": _percentile(server_latencies, 0.95),
            "p99": _percentile(server_latencies, 0.99),
            "mean": round(statistics.mean(server_latencies), 1) if server_latencies else 0.0,
            "max": max(server_latencies) if server_latencies else 0,
        },
        "wall_latency_ms": {
            "p50": round(_percentile(wall_latencies, 0.50), 1),
            "p95": round(_percentile(wall_latencies, 0.95), 1),
            "p99": round(_percentile(wall_latencies, 0.99), 1),
            "mean": round(statistics.mean(wall_latencies), 1) if wall_latencies else 0.0,
        },
        "cold_start": {
            "count": len(cold_latencies),
            "p50_ms": _percentile(cold_latencies, 0.50),
            "p95_ms": _percentile(cold_latencies, 0.95),
        },
        "warm": {
            "count": len(warm_latencies),
            "p50_ms": _percentile(warm_latencies, 0.50),
            "p95_ms": _percentile(warm_latencies, 0.95),
        },
        "confusion": confusion,
    }
    out = Path(get_settings().model_path).parent / "eval_latency.json"
    out.write_text(json.dumps(report, indent=2))
    print(json.dumps(report, indent=2))
    print(f"\nwrote {out}")
    return report


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--base-url", default=os.environ.get("BACKEND_URL", "http://127.0.0.1:8765"))
    p.add_argument("--n", type=int, default=200)
    p.add_argument("--seed", type=int, default=42)
    args = p.parse_args()
    run(args.base_url, args.n, args.seed)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
