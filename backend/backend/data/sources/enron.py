"""Enron email corpus subset via HuggingFace.

The full Enron corpus is ~500k emails. We use a curated subset from
HuggingFace and apply length + keyword filters to extract short emails
that look like business communications.
"""
from __future__ import annotations

import logging
import re

from .._common import clean

NAME = "enron"
log = logging.getLogger("replymind.data.enron")

WORK_KEYWORDS = re.compile(
    r"\b(meeting|deadline|report|project|proposal|invoice|EOD|"
    r"contract|schedule|update|action item|status|review|"
    r"deliverable|q[1-4]|fy\d+)\b",
    re.IGNORECASE,
)


def load() -> list[dict]:
    try:
        from datasets import load_dataset
    except ImportError as e:
        raise RuntimeError("datasets library required for enron loader") from e

    ds = None
    last_err: Exception | None = None
    for path, split in (
        ("SetFit/enron_spam", "train"),
        ("snoop2head/enron_aeslc_emails", "train"),
    ):
        try:
            ds = load_dataset(path, split=split)
            log.info("loaded enron from %s", path)
            break
        except Exception as e:
            log.warning("enron source %s failed: %s", path, e)
            last_err = e
            continue
    if ds is None:
        raise RuntimeError(f"could not load enron: {last_err}")

    rows: list[dict] = []
    for ex in ds:
        # SetFit/enron_spam has fields: text, label, label_text, subject, message_id, date
        # snoop2head/enron_aeslc_emails has: text, summary
        text = ex.get("text") or ex.get("message") or ex.get("body") or ""
        subject = ex.get("subject") or ""
        label_text = (ex.get("label_text") or "").lower()

        text = clean(text)
        subject = clean(subject)
        # spam-labelled enron rows feed into Promotional/Spam pool
        if label_text == "spam":
            if 30 <= len(text) <= 400:
                rows.append({"text": text, "category": "promotional", "source": NAME})
            continue

        if not (30 <= len(text) <= 400):
            continue
        haystack = f"{subject} {text}"
        if WORK_KEYWORDS.search(haystack):
            rows.append({"text": text, "category": "work", "source": NAME})
        elif len(text) >= 60 and len(text) <= 250:
            # short personal-style emails fall into "other"
            rows.append({"text": text, "category": "other", "source": NAME})
    return rows
