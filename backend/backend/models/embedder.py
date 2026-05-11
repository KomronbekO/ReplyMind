from __future__ import annotations

import logging

import numpy as np

from ..config import get_settings

log = logging.getLogger("replymind.embedder")

_model = None


def get_embedder():
    """Load the sentence-transformer once and cache it process-wide."""
    global _model
    if _model is not None:
        return _model
    from sentence_transformers import SentenceTransformer  # heavy import — keep lazy

    settings = get_settings()
    log.info("loading embedder: %s", settings.embedder_name)
    _model = SentenceTransformer(settings.embedder_name)
    return _model


def embed(texts: list[str], *, batch_size: int = 64) -> np.ndarray:
    """Return a (n, 384) float32 array of L2-normalised embeddings."""
    model = get_embedder()
    vecs = model.encode(
        texts,
        batch_size=batch_size,
        convert_to_numpy=True,
        normalize_embeddings=True,
        show_progress_bar=False,
    )
    return vecs.astype("float32")


def embed_one(text: str) -> np.ndarray:
    return embed([text])[0]
