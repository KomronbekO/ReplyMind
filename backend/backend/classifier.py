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
from .models.embedder import embed_one
from .models.mlp import MLPClassifier
from .schemas import ClassifyRequest, ClassifyResponse

log = logging.getLogger("replymind.classifier")

_model = None
_idx_to_label: dict[int, str] | None = None
_lock = threading.Lock()


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

    reasoning = _synthesise_reasoning(top_label, confidence, evidence)

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
