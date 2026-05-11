"""Evaluate the trained MLP on the frozen test set.

Run:  python -m backend.eval

Prints per-class precision/recall/F1 + confusion matrix. Writes a JSON
report to models/eval_report.json so the project write-up can cite numbers.
"""
from __future__ import annotations

import json
import logging
from pathlib import Path

import numpy as np
import pandas as pd
import torch
from sklearn.metrics import (
    classification_report,
    confusion_matrix,
    f1_score,
    precision_recall_fscore_support,
)

from .config import get_settings
from .data._common import DATA_ROOT
from .models.embedder import embed
from .models.mlp import MLPClassifier
from .train import CATEGORIES, _embed_with_cache  # reuse cache helper

log = logging.getLogger("replymind.eval")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")


def main() -> int:
    settings = get_settings()
    label_map = json.loads(Path(settings.label_map_path).read_text())
    idx_to_label = {int(k): v for k, v in label_map["idx_to_label"].items()}
    labels_ordered = [idx_to_label[i] for i in range(len(idx_to_label))]
    assert labels_ordered == CATEGORIES, "label order drifted between train and eval"

    test_path = DATA_ROOT / "test.csv"
    df = pd.read_csv(test_path)
    texts = df["text"].tolist()
    y_true = np.array([CATEGORIES.index(c) for c in df["category"].tolist()], dtype=np.int64)

    cache_dir = DATA_ROOT / "_embed_cache"
    X_test = _embed_with_cache("test", texts, cache_dir)

    model = MLPClassifier(in_dim=X_test.shape[1], hidden=256, num_classes=len(CATEGORIES))
    state = torch.load(settings.model_path, map_location="cpu")
    model.load_state_dict(state)
    model.eval()

    with torch.no_grad():
        logits = model(torch.from_numpy(X_test))
        preds = logits.argmax(dim=1).numpy()

    macro = f1_score(y_true, preds, average="macro")
    micro = f1_score(y_true, preds, average="micro")
    p, r, f, sup = precision_recall_fscore_support(y_true, preds, average=None, labels=list(range(len(CATEGORIES))))
    cm = confusion_matrix(y_true, preds, labels=list(range(len(CATEGORIES))))

    print(f"\nTEST SET RESULTS  (n={len(y_true)})")
    print("-" * 60)
    print(f"  macro-F1: {macro:.4f}")
    print(f"  micro-F1: {micro:.4f}\n")
    print(classification_report(y_true, preds, target_names=CATEGORIES, digits=4, zero_division=0))
    print("\nConfusion matrix (rows=true, cols=pred):")
    print(f"  {'':16s} " + "  ".join(f"{c[:6]:>6s}" for c in CATEGORIES))
    for i, true_cat in enumerate(CATEGORIES):
        print(f"  {true_cat:16s} " + "  ".join(f"{cm[i][j]:>6d}" for j in range(len(CATEGORIES))))

    report = {
        "n_test": int(len(y_true)),
        "macro_f1": float(macro),
        "micro_f1": float(micro),
        "per_class": {
            CATEGORIES[i]: {
                "precision": float(p[i]),
                "recall": float(r[i]),
                "f1": float(f[i]),
                "support": int(sup[i]),
            }
            for i in range(len(CATEGORIES))
        },
        "confusion_matrix": cm.tolist(),
    }
    out = Path(settings.model_path).parent / "eval_report.json"
    out.write_text(json.dumps(report, indent=2))
    print(f"\nwrote {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
