"""DailyDialog (Li et al., 2017).

13,118 dialogues from daily-life conversations. The original dataset has a
`topic` annotation, but the parquet-based HuggingFace mirrors we can load
with `datasets` 4.x do not expose it — they're flattened to dialog/act/emotion
only. We treat all utterances as family/friends since DailyDialog is by
definition daily/personal conversation (no work-topic dialogs in this corpus).

We filter by length and emotion to favour expressive utterances that read
like real-world informal text.
"""
from __future__ import annotations

import logging

from .._common import clean

NAME = "daily_dialog"
log = logging.getLogger("replymind.data.daily_dialog")

# DailyDialog emotion ids (per the dataset card)
EMO_NO = 0
EMO_ANGER = 1
EMO_DISGUST = 2
EMO_FEAR = 3
EMO_HAPPY = 4
EMO_SAD = 5
EMO_SURPRISE = 6


def load() -> list[dict]:
    try:
        from datasets import load_dataset
    except ImportError as e:
        raise RuntimeError("datasets library required for daily_dialog loader") from e

    ds = None
    last_err: Exception | None = None
    for path in ("OpenRL/daily_dialog", "yangwang825/daily_dialog_plus_plus"):
        try:
            ds = load_dataset(path, split="train")
            log.info("loaded daily_dialog from %s", path)
            break
        except Exception as e:
            log.warning("daily_dialog source %s failed: %s", path, e)
            last_err = e
            continue
    if ds is None:
        raise RuntimeError(f"could not load daily_dialog: {last_err}")

    rows: list[dict] = []
    for ex in ds:
        # OpenRL/daily_dialog: {"dialog": [str, ...], "act": [int, ...], "emotion": [int, ...]}
        # yangwang825/daily_dialog_plus_plus: {"context": str, "response": str, ...}
        if "dialog" in ex and isinstance(ex["dialog"], list):
            utterances = ex["dialog"]
            emotions = ex.get("emotion") or [EMO_NO] * len(utterances)
            for utt, emo in zip(utterances, emotions):
                text = clean(utt)
                if not (25 <= len(text) <= 300):
                    continue
                # Skip neutral utterances 60% of the time to favour expressive lines
                # (the corpus is heavily skewed to EMO_NO).
                if emo == EMO_NO and hash(text) % 5 != 0:
                    continue
                rows.append({"text": text, "category": "family_friends", "source": NAME})
        elif "response" in ex:
            text = clean(ex["response"])
            if 25 <= len(text) <= 300:
                rows.append({"text": text, "category": "family_friends", "source": NAME})
    return rows
