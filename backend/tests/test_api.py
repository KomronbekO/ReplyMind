from __future__ import annotations

from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from backend.main import app

_MODEL_FILE = Path(__file__).resolve().parents[1] / "models" / "classifier.pt"

client = TestClient(app)
BEARER = {"Authorization": "Bearer test-token"}


def test_healthz_open() -> None:
    r = client.get("/healthz")
    assert r.status_code == 200
    body = r.json()
    assert body["ok"] is True
    assert "model_loaded" in body and "chroma_ready" in body


def test_profile_requires_auth() -> None:
    r = client.post("/profile", json={"user_id": "u1", "profile": {}})
    assert r.status_code == 401


def test_profile_upsert_roundtrip() -> None:
    body = {
        "user_id": "u-alice",
        "profile": {
            "display_name": "Alice",
            "occupation": "PM",
            "tone": "PROFESSIONAL",
        },
    }
    r = client.post("/profile", json=body, headers=BEARER)
    assert r.status_code == 200
    assert r.json()["ok"] is True


def test_history_sync_dedup() -> None:
    payload = {
        "user_id": "u-bob",
        "items": [
            {"message_id": "m1", "sender": "Mom", "snippet": "Call me", "timestamp_ms": 1},
            {"message_id": "m1", "sender": "Mom", "snippet": "Call me", "timestamp_ms": 1},
            {"message_id": "m2", "sender": "Boss", "snippet": "EOD report", "timestamp_ms": 2},
        ],
    }
    r = client.post("/history/sync", json=payload, headers=BEARER)
    assert r.status_code == 200
    body = r.json()
    assert body["received"] == 3
    assert body["inserted"] == 2

    # Second call with same payload should insert nothing.
    r2 = client.post("/history/sync", json=payload, headers=BEARER)
    assert r2.status_code == 200
    assert r2.json()["inserted"] == 0


@pytest.mark.skipif(_MODEL_FILE.exists(), reason="model is trained; classifier is wired (covered by test_classifier.py)")
def test_classify_returns_503_before_model_trained() -> None:
    payload = {
        "user_id": "u-charlie",
        "sender": "Dad",
        "package": "org.telegram.messenger",
        "message": "Hey are you free tonight?",
        "profile": {"display_name": "Charlie"},
        "categories": [{"id": "family", "name": "Family", "description": "Personal"}],
    }
    r = client.post("/classify", json=payload, headers=BEARER)
    assert r.status_code == 503
    assert "classifier" in r.json()["detail"].lower()


def test_classify_requires_auth() -> None:
    r = client.post("/classify", json={"user_id": "u", "message": "hi"})
    assert r.status_code == 401
