"""Thin wrapper around ChromaDB so the rest of the code doesn't have to think
about collection naming, sanitisation, or the persistent client singleton."""
from __future__ import annotations

import hashlib
import logging
import re
import threading

import numpy as np

from .config import get_settings

log = logging.getLogger("replymind.vector_store")

_client = None
_lock = threading.Lock()


def _safe_collection_name(user_id: str) -> str:
    """Chroma collection names must be [3, 63] chars, [a-zA-Z0-9._-], start+end alphanumeric."""
    base = re.sub(r"[^a-zA-Z0-9_-]", "_", user_id) or "u"
    if len(base) > 40:
        digest = hashlib.sha1(user_id.encode("utf-8")).hexdigest()[:8]
        base = base[:30] + "_" + digest
    if not re.match(r"^[a-zA-Z0-9]", base):
        base = "u" + base
    if not re.search(r"[a-zA-Z0-9]$", base):
        base = base + "x"
    return f"user_{base}"


def _get_client():
    global _client
    if _client is not None:
        return _client
    with _lock:
        if _client is None:
            import chromadb

            settings = get_settings()
            _client = chromadb.PersistentClient(path=settings.chroma_dir)
            log.info("opened chroma client at %s", settings.chroma_dir)
    return _client


def get_collection(user_id: str):
    """Return (create if needed) the persistent collection for this user."""
    client = _get_client()
    name = _safe_collection_name(user_id)
    return client.get_or_create_collection(name=name)


def upsert_message(
    user_id: str,
    *,
    message_id: str,
    sender: str,
    package: str,
    snippet: str,
    embedding: np.ndarray,
    category: str | None = None,
    timestamp_ms: int = 0,
) -> None:
    col = get_collection(user_id)
    col.upsert(
        ids=[message_id],
        embeddings=[embedding.tolist()],
        documents=[snippet],
        metadatas=[{
            "sender": sender or "",
            "package": package or "",
            "category": category or "",
            "timestamp_ms": int(timestamp_ms or 0),
        }],
    )


def _safe_first_list(res: dict, key: str) -> list:
    """Chroma 1.x returns either None, a list of lists, or a numpy array for
    nested fields. Normalise to a plain Python list for the first (only) query."""
    val = res.get(key)
    if val is None:
        return []
    arr = list(val)
    if not arr:
        return []
    inner = arr[0]
    if inner is None:
        return []
    return list(inner)


def query(
    user_id: str,
    *,
    embedding: np.ndarray,
    n: int,
    where: dict | None = None,
) -> list[dict]:
    """Return a flat list of matches: [{id, distance, document, metadata}]."""
    col = get_collection(user_id)
    if col.count() == 0:
        return []
    res = col.query(
        query_embeddings=[embedding.tolist()],
        n_results=min(n, max(1, col.count())),
        where=where,
    )
    ids = _safe_first_list(res, "ids")
    dists = _safe_first_list(res, "distances")
    docs = _safe_first_list(res, "documents")
    metas = _safe_first_list(res, "metadatas")
    out: list[dict] = []
    for i in range(len(ids)):
        out.append({
            "id": ids[i],
            "distance": float(dists[i]) if i < len(dists) else 1.0,
            "document": docs[i] if i < len(docs) else "",
            "metadata": metas[i] if i < len(metas) else {},
        })
    return out


def fetch_embeddings(user_id: str, ids: list[str]) -> dict[str, np.ndarray]:
    """Pull stored embeddings by id (Chroma query() doesn't always return them)."""
    if not ids:
        return {}
    col = get_collection(user_id)
    res = col.get(ids=ids, include=["embeddings"])
    out: dict[str, np.ndarray] = {}
    got_ids = list(res.get("ids") or [])
    got_embs_raw = res.get("embeddings")
    got_embs: list = [] if got_embs_raw is None else list(got_embs_raw)
    for i, mid in enumerate(got_ids):
        if i < len(got_embs) and got_embs[i] is not None:
            out[mid] = np.array(got_embs[i], dtype=np.float32)
    return out


def reset_for_tests() -> None:
    """Wipe the singleton — only for tests."""
    global _client
    with _lock:
        _client = None
