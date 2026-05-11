"""B1 smoke tests — exercise stitch/split logic without hitting the network."""
from __future__ import annotations

from collections import Counter
from unittest.mock import patch

from backend.data import build as build_mod


def _fake_load_factory(category: str, n: int):
    def _load() -> list[dict]:
        return [{"text": f"sample {category} {i}", "category": category, "source": f"fake_{category}"} for i in range(n)]
    return _load


def test_stitch_caps_per_category():
    fake_loaders = [
        ("fake_urgent", _fake_load_factory("urgent", 5000)),       # over cap (800)
        ("fake_work", _fake_load_factory("work", 200)),            # under cap (1500)
        ("fake_family", _fake_load_factory("family_friends", 1500)),
        ("fake_promo", _fake_load_factory("promotional", 1000)),
        ("fake_spam", _fake_load_factory("spam", 750)),
        ("fake_other", _fake_load_factory("other", 700)),
    ]
    with patch.object(build_mod, "LOADERS", fake_loaders):
        rows = build_mod.stitch(seed=42)
    counts = Counter(r["category"] for r in rows)
    assert counts["urgent"] == 800   # capped
    assert counts["work"] == 200     # under cap → use all
    assert counts["family_friends"] == 1500
    assert counts["spam"] == 750
    assert counts["other"] == 700


def test_stitch_survives_one_loader_failing():
    def _boom():
        raise RuntimeError("network down")
    fake_loaders = [
        ("fake_urgent", _fake_load_factory("urgent", 50)),
        ("fake_disaster", _boom),
        ("fake_work", _fake_load_factory("work", 100)),
    ]
    with patch.object(build_mod, "LOADERS", fake_loaders):
        rows = build_mod.stitch(seed=42)
    cats = {r["category"] for r in rows}
    assert "urgent" in cats and "work" in cats
    assert len(rows) == 150


def test_stratified_split_preserves_categories():
    rows = []
    for cat in ("urgent", "work", "family_friends", "promotional", "spam", "other"):
        rows += [{"text": f"{cat}-{i}", "category": cat, "source": "fake"} for i in range(100)]
    train, val, test = build_mod.stratified_split(rows, val_frac=0.15, test_frac=0.15, seed=0)
    assert len(train) + len(val) + len(test) == 600
    # Each category should appear in each split — no category collapse.
    for split in (train, val, test):
        assert {r["category"] for r in split} == set(build_mod.ALL_CATEGORIES)


def test_each_loader_module_imports():
    """Each source module must import and expose load() — catches typos cheaply."""
    from backend.data.sources import (
        daily_dialog,
        disaster_tweets,
        enron,
        sms_spam,
        youtube_spam,
    )
    for mod in (sms_spam, youtube_spam, daily_dialog, disaster_tweets, enron):
        assert callable(getattr(mod, "load", None)), f"{mod.__name__}.load is missing"
        assert hasattr(mod, "NAME"), f"{mod.__name__}.NAME is missing"
