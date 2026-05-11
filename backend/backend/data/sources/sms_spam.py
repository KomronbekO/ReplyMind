"""UCI SMS Spam Collection (Almeida & Hidalgo, 2011, CC-BY 4.0).

5,572 SMS messages labelled spam/ham. We pull all `spam` rows for the Spam
category and reserve some longer ham rows for the Other / Family-Friends pool.
"""
from __future__ import annotations

import csv

from .._common import clean, ensure_raw_dir, fetch_zip

URL = "https://archive.ics.uci.edu/static/public/228/sms+spam+collection.zip"
NAME = "sms_spam"


def load() -> list[dict]:
    raw = ensure_raw_dir(NAME)
    fetch_zip(URL, raw)

    tsv = raw / "SMSSpamCollection"
    if not tsv.exists():
        # zip may unpack into a nested folder
        candidates = list(raw.rglob("SMSSpamCollection"))
        if not candidates:
            raise FileNotFoundError(f"SMSSpamCollection not found under {raw}")
        tsv = candidates[0]

    rows: list[dict] = []
    with tsv.open(encoding="latin-1") as fh:
        reader = csv.reader(fh, delimiter="\t")
        for line in reader:
            if len(line) < 2:
                continue
            label, text = line[0], line[1]
            text = clean(text)
            if not text:
                continue
            if label == "spam":
                rows.append({"text": text, "category": "spam", "source": NAME})
            elif label == "ham" and 40 <= len(text) <= 200:
                # informal short texts -> "other" bucket (random conversational SMS)
                rows.append({"text": text, "category": "other", "source": NAME})
    return rows
