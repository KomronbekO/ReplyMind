"""Per-user retrieval-augmented inference: combine the current message
embedding with embeddings from the user's history before running the MLP.

Strategy:
1. Pull top-K from this sender's history.
2. Pull top-K from the user's whole history (cross-sender).
3. Compute weighted average of the L2-normalised vectors:
       combined = w_cur * cur + w_send * mean(sender) + w_glob * mean(global)
4. Re-normalise so the classifier sees a unit vector (same distribution as training).

If the user has no history, `cold_start=True` and `combined == cur`.
"""
from __future__ import annotations

import logging

import numpy as np

from . import vector_store
from .config import get_settings
from .schemas import RagEvidenceItem

log = logging.getLogger("replymind.rag")


def _mean_unit(vectors: list[np.ndarray]) -> np.ndarray | None:
    if not vectors:
        return None
    stack = np.stack(vectors)
    mean = stack.mean(axis=0)
    n = np.linalg.norm(mean)
    if n < 1e-9:
        return None
    return mean / n


def _normalise(vec: np.ndarray) -> np.ndarray:
    n = np.linalg.norm(vec)
    if n < 1e-9:
        return vec
    return vec / n


def retrieve_and_combine(
    user_id: str,
    sender: str,
    current_embedding: np.ndarray,
) -> tuple[np.ndarray, list[RagEvidenceItem], bool]:
    """Return (combined_embedding, evidence_list, cold_start)."""
    settings = get_settings()
    cur = _normalise(current_embedding.astype(np.float32))

    per_sender_hits: list[dict] = []
    if sender:
        per_sender_hits = vector_store.query(
            user_id,
            embedding=cur,
            n=settings.rag_k_sender,
            where={"sender": sender},
        )
    global_hits = vector_store.query(user_id, embedding=cur, n=settings.rag_k_global)

    all_ids = [h["id"] for h in (per_sender_hits + global_hits)]
    cold_start = len(all_ids) == 0
    if cold_start:
        return cur, [], True

    # Chroma sometimes omits embeddings from query() — fetch them by id.
    embeddings_by_id = vector_store.fetch_embeddings(user_id, list(set(all_ids)))

    sender_vecs = [embeddings_by_id[h["id"]] for h in per_sender_hits if h["id"] in embeddings_by_id]
    global_vecs = [embeddings_by_id[h["id"]] for h in global_hits if h["id"] in embeddings_by_id]

    sender_mean = _mean_unit(sender_vecs)
    global_mean = _mean_unit(global_vecs)

    w_cur = settings.rag_weight_current
    w_send = settings.rag_weight_sender if sender_mean is not None else 0.0
    w_glob = settings.rag_weight_global if global_mean is not None else 0.0
    total = w_cur + w_send + w_glob
    if total < 1e-9:
        return cur, [], cold_start

    combined = cur * w_cur
    if sender_mean is not None:
        combined = combined + sender_mean * w_send
    if global_mean is not None:
        combined = combined + global_mean * w_glob
    combined = _normalise(combined / total)

    evidence: list[RagEvidenceItem] = []
    seen: set[str] = set()
    for h in per_sender_hits + global_hits:
        if h["id"] in seen:
            continue
        seen.add(h["id"])
        meta = h.get("metadata") or {}
        evidence.append(RagEvidenceItem(
            message_id=h["id"],
            sender=str(meta.get("sender", "")),
            snippet=str(h.get("document") or "")[:200],
            category=meta.get("category") or None,
            score=max(0.0, 1.0 - h.get("distance", 1.0)),  # rough cosine sim
        ))
    return combined, evidence, cold_start
