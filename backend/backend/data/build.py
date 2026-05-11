"""Build the stitched training corpus.

Run:  python -m backend.data.build

Pulls from the five source loaders, caps each category to keep the class
distribution sane, stratified 70/15/15 split → data/{train,val,test}.csv
plus a combined data/corpus.csv. Frozen test set is never overwritten once
written (rerunning rotates train+val but preserves test).
"""
from __future__ import annotations

import argparse
import csv
import json
import logging
import random
from collections import Counter, defaultdict
from pathlib import Path

from . import (
    sources as src_pkg,
)
from ._common import DATA_ROOT
from .sources import daily_dialog, disaster_tweets, enron, sms_spam, youtube_spam

log = logging.getLogger("replymind.data.build")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")

ALL_CATEGORIES = ["urgent", "work", "family_friends", "promotional", "spam", "other"]

# Target counts per category. The orchestrator will cap incoming rows at these
# numbers (per source, summed). If a source returns fewer rows than we'd take,
# we just use what we have.
PER_CATEGORY_CAP = {
    "urgent": 800,
    "work": 1500,
    "family_friends": 1500,
    "promotional": 1000,
    "spam": 750,
    "other": 700,
}

LOADERS = [
    ("sms_spam", sms_spam.load),
    ("youtube_spam", youtube_spam.load),
    ("daily_dialog", daily_dialog.load),
    ("disaster_tweets", disaster_tweets.load),
    ("enron", enron.load),
]


def stitch(seed: int = 17) -> list[dict]:
    rng = random.Random(seed)
    pool: dict[str, list[dict]] = defaultdict(list)

    for name, loader in LOADERS:
        try:
            rows = loader()
            log.info("[%s] loaded %d raw rows", name, len(rows))
            rng.shuffle(rows)
            for r in rows:
                pool[r["category"]].append(r)
        except Exception as e:
            log.warning("[%s] FAILED: %s", name, e)

    out: list[dict] = []
    for cat in ALL_CATEGORIES:
        cap = PER_CATEGORY_CAP.get(cat, 1000)
        kept = pool.get(cat, [])[:cap]
        out.extend(kept)
        log.info("  -> %s: kept %d / available %d (cap %d)", cat, len(kept), len(pool.get(cat, [])), cap)
    rng.shuffle(out)
    return out


def stratified_split(rows: list[dict], *, val_frac: float = 0.15, test_frac: float = 0.15, seed: int = 17):
    rng = random.Random(seed)
    by_cat: dict[str, list[dict]] = defaultdict(list)
    for r in rows:
        by_cat[r["category"]].append(r)
    train, val, test = [], [], []
    for cat, items in by_cat.items():
        rng.shuffle(items)
        n = len(items)
        n_test = int(round(n * test_frac))
        n_val = int(round(n * val_frac))
        test += items[:n_test]
        val += items[n_test : n_test + n_val]
        train += items[n_test + n_val :]
    rng.shuffle(train)
    rng.shuffle(val)
    rng.shuffle(test)
    return train, val, test


def write_csv(rows: list[dict], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=["text", "category", "source"])
        w.writeheader()
        w.writerows(rows)


def freeze_test(test_rows: list[dict]) -> list[dict]:
    """Keep the test set stable across rebuilds — never overwrite if already present."""
    p = DATA_ROOT / "test.csv"
    if p.exists():
        with p.open(encoding="utf-8") as fh:
            r = list(csv.DictReader(fh))
        log.info("frozen test set already exists (%d rows) — preserving", len(r))
        return r
    write_csv(test_rows, p)
    return test_rows


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--seed", type=int, default=17)
    parser.add_argument("--reset-test", action="store_true", help="overwrite frozen test.csv")
    args = parser.parse_args()

    if args.reset_test:
        p = DATA_ROOT / "test.csv"
        if p.exists():
            p.unlink()
            log.info("removed existing test.csv")

    corpus = stitch(seed=args.seed)
    log.info("stitched corpus: %d rows", len(corpus))
    log.info("distribution: %s", dict(Counter(r["category"] for r in corpus)))

    train, val, test = stratified_split(corpus, seed=args.seed)
    test = freeze_test(test)

    write_csv(corpus, DATA_ROOT / "corpus.csv")
    write_csv(train, DATA_ROOT / "train.csv")
    write_csv(val, DATA_ROOT / "val.csv")

    summary = {
        "total": len(corpus),
        "train": len(train),
        "val": len(val),
        "test": len(test),
        "distribution": dict(Counter(r["category"] for r in corpus)),
        "seed": args.seed,
    }
    (DATA_ROOT / "summary.json").write_text(json.dumps(summary, indent=2))
    log.info("wrote summary: %s", summary)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
