"""End-to-end classification: embed → RAG retrieve → MLP head → response.

`classify_message(req)` is the function imported by `main.py`'s /classify route.
It also persists the current message into the user's vector store so future
calls have richer retrieval signal.
"""
from __future__ import annotations

import json
import logging
import threading
import uuid
from pathlib import Path

import numpy as np
import torch
import torch.nn.functional as F

from . import rag, vector_store
from .config import get_settings
from .models.embedder import embed, embed_one
from .models.mlp import MLPClassifier
from .schemas import CategoryDescriptor, ClassifyRequest, ClassifyResponse

log = logging.getLogger("replymind.classifier")

_model = None
_idx_to_label: dict[int, str] | None = None
_lock = threading.Lock()

# Cache of (id-tuple, desc-tuple) → np.ndarray (n, 384). Descriptions don't
# change across calls in a session, so we embed them once and reuse.
_DESC_CACHE: dict[tuple, tuple[list[str], np.ndarray]] = {}
_DESC_CACHE_LOCK = threading.Lock()


def _description_vectors(cats: list[CategoryDescriptor]) -> tuple[list[str], np.ndarray] | None:
    """Embed the category descriptions, cached. Returns (ids, vectors) or None
    if descriptions are missing/blank for every category."""
    if not cats:
        return None
    pairs = [(c.id, (c.description or "").strip()) for c in cats]
    if not any(d for _, d in pairs):
        return None
    key = tuple(pairs)
    with _DESC_CACHE_LOCK:
        cached = _DESC_CACHE.get(key)
        if cached is not None:
            return cached
        ids = [cid for cid, _ in pairs]
        texts = [d if d else cid for _, d in pairs]  # fall back to id if no description
        vecs = embed(texts)
        _DESC_CACHE[key] = (ids, vecs)
        return ids, vecs


def _backstop_pick(
    current: np.ndarray,
    cats: list[CategoryDescriptor],
    probs: np.ndarray,
    idx_to_label: dict[int, str],
    requested_ids: set[str],
) -> tuple[int, str, float, str] | None:
    """Cosine-match the raw current embedding against category descriptions.
    Returns (idx, label, confidence, reason) of the best description match,
    or None if descriptions are absent."""
    desc = _description_vectors(cats)
    if desc is None:
        return None
    ids, desc_vecs = desc
    # current is already L2-normalised by the embedder; desc_vecs too.
    sims = desc_vecs @ current  # (n,) cosine similarities
    order = np.argsort(-sims)
    for j in order:
        cid = ids[int(j)]
        # Backstop only picks among labels the MLP head knows AND the caller
        # asked for — keeps the response inside the agreed taxonomy.
        if requested_ids and cid not in requested_ids:
            continue
        # Map label-id back to the MLP idx so confidence comparison is fair.
        for mlp_idx, mlp_label in idx_to_label.items():
            if mlp_label == cid:
                cosine = float(sims[int(j)])
                # Re-blend with the MLP probability so we don't completely
                # override the head when it had any opinion — softens edge cases.
                blended = 0.5 * cosine + 0.5 * float(probs[mlp_idx])
                return mlp_idx, mlp_label, blended, (
                    f"low-confidence head ({probs[mlp_idx]:.2f}); "
                    f"description-similarity picked {mlp_label} (cos={cosine:.2f})"
                )
    return None


def _load_model():
    global _model, _idx_to_label
    if _model is not None and _idx_to_label is not None:
        return _model, _idx_to_label
    with _lock:
        if _model is not None and _idx_to_label is not None:
            return _model, _idx_to_label
        settings = get_settings()
        model_path = Path(settings.model_path)
        label_path = Path(settings.label_map_path)
        if not model_path.exists() or not label_path.exists():
            raise FileNotFoundError(
                f"trained model not found at {model_path} / {label_path}; run `python -m backend.train`"
            )
        label_map = json.loads(label_path.read_text())
        idx_to_label = {int(k): v for k, v in label_map["idx_to_label"].items()}
        model = MLPClassifier(in_dim=384, hidden=256, num_classes=len(idx_to_label))
        model.load_state_dict(torch.load(model_path, map_location="cpu"))
        model.eval()
        log.info("loaded classifier with %d classes", len(idx_to_label))
        _model = model
        _idx_to_label = idx_to_label
    return _model, _idx_to_label


def _synthesise_reasoning(top_cat: str, confidence: float, evidence) -> str:
    if confidence < 0.45:
        return f"weak match; best guess {top_cat} (conf={confidence:.2f})"
    if not evidence:
        return f"classified as {top_cat} based on message text alone (no prior history)"
    same_cat = [e for e in evidence if (e.category or "").lower() == top_cat]
    if same_cat:
        e = same_cat[0]
        sender = e.sender or "unknown sender"
        return f"similar to a prior message from {sender} categorised as {top_cat}"
    return f"closest match in your history was a {top_cat}-style message"


def classify_message(req: ClassifyRequest) -> ClassifyResponse:
    model, idx_to_label = _load_model()

    current = embed_one(req.message)
    combined, evidence, cold_start = rag.retrieve_and_combine(
        req.user_id, req.sender, current
    )

    with torch.no_grad():
        logits = model(torch.from_numpy(combined.astype(np.float32)).unsqueeze(0))
        probs = F.softmax(logits, dim=1).squeeze(0).numpy()
    top_idx = int(probs.argmax())
    top_label = idx_to_label[top_idx]
    confidence = float(probs[top_idx])

    requested_ids = {c.id for c in (req.categories or [])}
    if requested_ids and top_label not in requested_ids:
        for i in np.argsort(-probs):
            cand = idx_to_label[int(i)]
            if cand in requested_ids:
                top_idx = int(i)
                top_label = cand
                confidence = float(probs[top_idx])
                break

    # Confidence-gated semantic backstop. The MLP is trained on a stitched
    # corpus where the `other` slice acts as a catch-all dump — short casual
    # texts (e.g., real WhatsApp invitations) tend to land here even when a
    # better-fitting category exists. Only fire the backstop when:
    #   - the head's top guess is `other` (the failure mode we care about), AND
    #   - it's not overwhelmingly confident, AND
    #   - the runner-up is within striking distance.
    # This scoping avoids over-correcting on classes where the head is reliable
    # (work / spam / promotional all stay untouched).
    settings = get_settings()
    sorted_probs = np.sort(probs)[::-1]
    margin = float(sorted_probs[0] - sorted_probs[1]) if len(sorted_probs) >= 2 else 1.0
    backstop_reason: str | None = None
    if (
        top_label == "other"
        and confidence < settings.low_confidence_threshold
        and margin < settings.ambiguous_margin
    ):
        picked = _backstop_pick(current, req.categories or [], probs, idx_to_label, requested_ids)
        if picked is not None:
            new_idx, new_label, new_conf, backstop_reason = picked
            if new_label != top_label:
                log.info(
                    "backstop override: %s (%.2f) → %s (cos-blend %.2f)",
                    top_label, confidence, new_label, new_conf,
                )
                top_idx, top_label, confidence = new_idx, new_label, new_conf
            else:
                # Same label, no override needed — drop the backstop reasoning.
                backstop_reason = None

    reasoning = backstop_reason or _synthesise_reasoning(top_label, confidence, evidence)

    response = ClassifyResponse(
        category_id=top_label,
        confidence=confidence,
        reasoning=reasoning,
        rag_evidence=evidence,
        cold_start=cold_start,
        latency_ms=0,
    )

    # Persist the current message so the next call benefits.
    try:
        vector_store.upsert_message(
            req.user_id,
            message_id=str(uuid.uuid4()),
            sender=req.sender or "",
            package=req.package or "",
            snippet=req.message[:280],
            embedding=current,
            category=top_label,
            timestamp_ms=0,
        )
    except Exception as e:  # don't break inference on persistence failure
        log.warning("vector_store.upsert_message failed: %s", e)

    return response
