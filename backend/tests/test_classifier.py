"""B3 — end-to-end /classify sanity test with the trained model.

These tests are skipped automatically if the model artifact isn't present
(useful in CI before B2 has run).
"""
from __future__ import annotations

from pathlib import Path

import pytest

MODEL_PATH = Path(__file__).resolve().parents[1] / "models" / "classifier.pt"
LABEL_MAP_PATH = Path(__file__).resolve().parents[1] / "models" / "label_map.json"


pytestmark = pytest.mark.skipif(
    not (MODEL_PATH.exists() and LABEL_MAP_PATH.exists()),
    reason="trained model not available — run `python -m backend.train` first",
)


def _payload(message: str, *, user_id: str = "test-user", sender: str = "alice") -> dict:
    return {
        "user_id": user_id,
        "sender": sender,
        "package": "org.telegram.messenger",
        "message": message,
        "profile": {"display_name": "Test", "tone": "CASUAL"},
        "categories": [
            {"id": "urgent", "name": "Urgent", "description": ""},
            {"id": "work", "name": "Work", "description": ""},
            {"id": "family_friends", "name": "Family", "description": ""},
            {"id": "promotional", "name": "Promo", "description": ""},
            {"id": "spam", "name": "Spam", "description": ""},
            {"id": "other", "name": "Other", "description": ""},
        ],
    }


def _make_client():
    from fastapi.testclient import TestClient

    from backend.main import app

    return TestClient(app), {"Authorization": "Bearer test-token"}


def test_classify_urgent_signature() -> None:
    """A short emergency-shaped message should not classify as work or promotional."""
    client, headers = _make_client()
    r = client.post(
        "/classify",
        json=_payload("FIRE BREAKING OUT in the warehouse, please call 911 NOW!"),
        headers=headers,
    )
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["category_id"] in {"urgent", "spam"}  # disaster-tweet bias may push to spam
    assert 0.0 <= body["confidence"] <= 1.0
    assert "latency_ms" in body


def test_classify_promotional_signature() -> None:
    client, headers = _make_client()
    r = client.post(
        "/classify",
        json=_payload("Hey everyone please subscribe to my channel and check out my latest video!"),
        headers=headers,
    )
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["category_id"] in {"promotional", "spam"}


def test_classify_spam_signature() -> None:
    client, headers = _make_client()
    r = client.post(
        "/classify",
        json=_payload("Congratulations! You have WON a £2000 prize! Reply CLAIM to 88080 to collect now!"),
        headers=headers,
    )
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["category_id"] in {"spam", "promotional"}


def test_classify_work_signature() -> None:
    client, headers = _make_client()
    r = client.post(
        "/classify",
        json=_payload("Hi team, please review the attached quarterly report and send your feedback by EOD."),
        headers=headers,
    )
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["category_id"] in {"work", "other"}


def test_classify_response_shape() -> None:
    client, headers = _make_client()
    r = client.post("/classify", json=_payload("Just saying hi, how are you?"), headers=headers)
    assert r.status_code == 200
    body = r.json()
    for key in ("category_id", "confidence", "reasoning", "rag_evidence", "cold_start", "latency_ms"):
        assert key in body, f"missing key {key}"
    assert isinstance(body["rag_evidence"], list)


def test_backstop_with_rich_descriptions() -> None:
    """When categories carry rich descriptions, an ambiguous casual invite like
    'Hey, doing a party today, are you in?' should land on family_friends via
    the cosine backstop — even though the raw MLP head splits other/family."""
    client, headers = _make_client()
    payload = _payload(
        "Hey Komron, we are doing a party today, are you in?",
        user_id="backstop-user-1",
        sender="Friend",
    )
    payload["categories"] = [
        {"id": "urgent", "name": "Urgent",
         "description": "Time-sensitive emergencies, deadlines today, urgent requests."},
        {"id": "work", "name": "Work",
         "description": "Messages from colleagues, clients, projects, meetings."},
        {"id": "family_friends", "name": "Family",
         "description": "Personal messages from close relationships: family, "
                        "close friends, casual invitations to meet up, hang out, "
                        "party, grab a coffee."},
        {"id": "promotional", "name": "Promo",
         "description": "Marketing, deals, newsletters, automated content."},
        {"id": "spam", "name": "Spam",
         "description": "Suspicious, scam, phishing, unsolicited from unknown senders."},
        {"id": "other", "name": "Other",
         "description": "Messages that don't fit the above categories."},
    ]
    r = client.post("/classify", json=payload, headers=headers)
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["category_id"] == "family_friends", (
        f"backstop should have pulled to family_friends, got {body['category_id']} "
        f"(reasoning: {body['reasoning']})"
    )
