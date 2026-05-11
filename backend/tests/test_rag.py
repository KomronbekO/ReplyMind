"""B3 — RAG retrieval correctness.

Uses a deterministic fake embedder so we don't depend on the real
sentence-transformer for unit-level retrieval tests.
"""
from __future__ import annotations

import numpy as np
import pytest

from backend import rag, vector_store


@pytest.fixture(autouse=True)
def _isolated_chroma(tmp_path, monkeypatch):
    """Each test gets its own chroma dir."""
    monkeypatch.setenv("CHROMA_DIR", str(tmp_path / "chroma"))
    # Bust the get_settings + chroma client caches.
    from backend import config

    config.get_settings.cache_clear()
    vector_store.reset_for_tests()
    yield


def _one_hot(dim: int, idx: int) -> np.ndarray:
    v = np.zeros(dim, dtype=np.float32)
    v[idx % dim] = 1.0
    return v


def test_cold_start_returns_current_only():
    vec = _one_hot(384, 0)
    combined, evidence, cold = rag.retrieve_and_combine("u1", "Alice", vec)
    assert cold is True
    assert evidence == []
    np.testing.assert_allclose(combined, vec, atol=1e-6)


def test_retrieves_per_sender_first():
    """Same-sender history should rank above unrelated entries."""
    dim = 384
    target = _one_hot(dim, 5)

    # Mom: 3 messages all similar to target.
    for i in range(3):
        vec = target.copy()
        vec[10 + i] = 0.05  # tiny perturbation
        vec = vec / np.linalg.norm(vec)
        vector_store.upsert_message(
            "u1",
            message_id=f"mom-{i}",
            sender="Mom",
            package="tg",
            snippet=f"mom msg {i}",
            embedding=vec,
            category="family_friends",
        )

    # Boss: 3 messages orthogonal to target.
    for i in range(3):
        vec = _one_hot(dim, 100 + i)
        vector_store.upsert_message(
            "u1",
            message_id=f"boss-{i}",
            sender="Boss",
            package="tg",
            snippet=f"boss msg {i}",
            embedding=vec,
            category="work",
        )

    combined, evidence, cold = rag.retrieve_and_combine("u1", "Mom", target)
    assert cold is False
    assert any(e.sender == "Mom" for e in evidence), "should retrieve Mom's history"

    # The Mom-filtered retrieval should come before the global retrieval and
    # contribute heavily — combined vector should still be very close to target.
    cosine = float(np.dot(combined, target) / (np.linalg.norm(combined) * np.linalg.norm(target)))
    assert cosine > 0.9, f"combined should align with target, got cosine={cosine}"


def test_unknown_sender_falls_back_to_global():
    """When `sender` matches nothing, retrieval still works via the global pool."""
    dim = 384
    target = _one_hot(dim, 7)

    # Seed only Bob's history.
    for i in range(2):
        vec = target.copy()
        vec[20 + i] = 0.1
        vec = vec / np.linalg.norm(vec)
        vector_store.upsert_message(
            "u2",
            message_id=f"bob-{i}",
            sender="Bob",
            package="tg",
            snippet=f"bob msg {i}",
            embedding=vec,
            category="family_friends",
        )

    combined, evidence, cold = rag.retrieve_and_combine("u2", "NewSender", target)
    assert cold is False
    assert evidence, "global retrieval should still return something"
    cosine = float(np.dot(combined, target) / (np.linalg.norm(combined) * np.linalg.norm(target)))
    assert cosine > 0.9


def test_collection_isolation_between_users():
    dim = 384
    target = _one_hot(dim, 11)

    vector_store.upsert_message(
        "user-A",
        message_id="a1",
        sender="Mom",
        package="tg",
        snippet="user A message",
        embedding=target,
    )
    # User B has no history — should still be cold start.
    _, evidence_b, cold_b = rag.retrieve_and_combine("user-B", "Mom", target)
    assert cold_b is True
    assert evidence_b == []

    # User A retrieves their own message.
    _, evidence_a, cold_a = rag.retrieve_and_combine("user-A", "Mom", target)
    assert cold_a is False
    assert any(e.message_id == "a1" for e in evidence_a)
