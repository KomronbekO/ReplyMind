"""UCI YouTube Spam Collection (Alberto et al., 2015, CC-BY 4.0).

~2000 YouTube comments labelled spam/ham across 5 popular videos. Most
"spam" here is promotional ("subscribe to my channel", "check out my page"),
so we map those into the Promotional bucket — distinct from outright Spam SMS.
"""
from __future__ import annotations

import csv

from .._common import clean, ensure_raw_dir, fetch_zip

URL = "https://archive.ics.uci.edu/static/public/380/youtube+spam+collection.zip"
NAME = "youtube_spam"


def load() -> list[dict]:
    raw = ensure_raw_dir(NAME)
    fetch_zip(URL, raw)

    rows: list[dict] = []
    for csv_path in raw.rglob("*.csv"):
        with csv_path.open(encoding="utf-8", errors="ignore") as fh:
            reader = csv.DictReader(fh)
            for r in reader:
                content = clean(r.get("CONTENT") or r.get("content") or "")
                cls = (r.get("CLASS") or r.get("class") or "").strip()
                if not content or cls not in ("0", "1"):
                    continue
                if cls == "1":
                    # promo: subscribe / check out / my channel etc.
                    rows.append({"text": content, "category": "promotional", "source": NAME})
                # ignore CLASS=0 (legit comments) — too noisy for our buckets
    return rows
