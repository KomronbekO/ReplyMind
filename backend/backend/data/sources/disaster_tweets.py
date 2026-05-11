"""Kaggle "Real or Not? NLP with Disaster Tweets" via HuggingFace mirror.

~7600 tweets labelled `target=1` (about a real disaster) vs `target=0`. We
use `target=1` rows as Urgent — these are short, time-sensitive messages
about emergencies, fires, accidents, etc., which is the closest public-data
proxy for an "urgent" personal message.
"""
from __future__ import annotations

import logging

from .._common import clean

NAME = "disaster_tweets"
log = logging.getLogger("replymind.data.disaster_tweets")


def load() -> list[dict]:
    try:
        from datasets import load_dataset
    except ImportError as e:
        raise RuntimeError("datasets library required for disaster_tweets loader") from e

    ds = None
    last_err: Exception | None = None
    for path in (
        "venetis/disaster_tweets",
        "Sachinkelenjaguri/Disaster_Tweets",
        "VuduVations/disaster_tweets",
    ):
        try:
            ds = load_dataset(path, split="train")
            log.info("loaded disaster_tweets from %s", path)
            break
        except Exception as e:
            log.warning("disaster_tweets source %s failed: %s", path, e)
            last_err = e
            continue
    if ds is None:
        raise RuntimeError(f"could not load disaster_tweets: {last_err}")

    rows: list[dict] = []
    for ex in ds:
        # Two known schemas: {text, target} (Kaggle original) and {message, label_id} (response).
        text = ex.get("text") or ex.get("message") or ex.get("tweet") or ""
        target = ex.get("target")
        text = clean(text)
        if not text or len(text) < 20 or len(text) > 280:
            continue
        if target == 1 or target == "1":
            rows.append({"text": text, "category": "urgent", "source": NAME})
    return rows
