"""Train the MLP head on the stitched corpus.

Run:  python -m backend.train

Outputs:
  models/classifier.pt   — torch state dict
  models/label_map.json  — idx -> category id + reverse
  models/embed_cache.npz — (optional) cached train+val embeddings
"""
from __future__ import annotations

import argparse
import json
import logging
import time
from pathlib import Path

import numpy as np
import pandas as pd
import torch
from sklearn.metrics import f1_score
from torch import nn, optim
from torch.utils.data import DataLoader, TensorDataset

from .config import get_settings
from .data._common import DATA_ROOT
from .models.embedder import embed
from .models.mlp import MLPClassifier

log = logging.getLogger("replymind.train")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")


CATEGORIES = ["urgent", "work", "family_friends", "promotional", "spam", "other"]


def _load_csv(path: Path) -> tuple[list[str], list[str]]:
    df = pd.read_csv(path)
    return df["text"].tolist(), df["category"].tolist()


def _embed_with_cache(name: str, texts: list[str], cache_dir: Path) -> np.ndarray:
    cache_dir.mkdir(parents=True, exist_ok=True)
    cache_path = cache_dir / f"{name}.npy"
    if cache_path.exists():
        vecs = np.load(cache_path)
        if vecs.shape[0] == len(texts):
            log.info("[%s] using cached embeddings: %s", name, vecs.shape)
            return vecs
    log.info("[%s] embedding %d texts", name, len(texts))
    vecs = embed(texts)
    np.save(cache_path, vecs)
    return vecs


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--epochs", type=int, default=30)
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--lr", type=float, default=3e-4)
    parser.add_argument("--patience", type=int, default=5, help="early-stopping patience on val macro-F1")
    parser.add_argument("--seed", type=int, default=17)
    args = parser.parse_args()

    torch.manual_seed(args.seed)
    np.random.seed(args.seed)

    settings = get_settings()
    label_to_idx = {c: i for i, c in enumerate(CATEGORIES)}

    train_texts, train_cats = _load_csv(DATA_ROOT / "train.csv")
    val_texts, val_cats = _load_csv(DATA_ROOT / "val.csv")
    log.info("loaded %d train / %d val rows", len(train_texts), len(val_texts))

    cache_dir = DATA_ROOT / "_embed_cache"
    X_train = _embed_with_cache("train", train_texts, cache_dir)
    X_val = _embed_with_cache("val", val_texts, cache_dir)

    y_train = np.array([label_to_idx[c] for c in train_cats], dtype=np.int64)
    y_val = np.array([label_to_idx[c] for c in val_cats], dtype=np.int64)

    # Inverse-frequency class weights for the cross-entropy loss.
    counts = np.bincount(y_train, minlength=len(CATEGORIES)).astype(np.float32)
    counts = np.where(counts == 0, 1.0, counts)
    weights = counts.sum() / (len(CATEGORIES) * counts)
    class_weights = torch.tensor(weights, dtype=torch.float32)
    log.info("class weights: %s", dict(zip(CATEGORIES, weights.round(3))))

    train_ds = TensorDataset(torch.from_numpy(X_train), torch.from_numpy(y_train))
    train_loader = DataLoader(train_ds, batch_size=args.batch_size, shuffle=True)

    X_val_t = torch.from_numpy(X_val)
    y_val_t = torch.from_numpy(y_val)

    model = MLPClassifier(in_dim=X_train.shape[1], hidden=256, num_classes=len(CATEGORIES))
    criterion = nn.CrossEntropyLoss(weight=class_weights)
    optimizer = optim.Adam(model.parameters(), lr=args.lr)

    best_f1 = -1.0
    best_state = None
    epochs_since_improve = 0
    t0 = time.perf_counter()

    for epoch in range(1, args.epochs + 1):
        model.train()
        running = 0.0
        for xb, yb in train_loader:
            optimizer.zero_grad()
            logits = model(xb)
            loss = criterion(logits, yb)
            loss.backward()
            optimizer.step()
            running += float(loss.item()) * xb.size(0)
        train_loss = running / len(train_ds)

        model.eval()
        with torch.no_grad():
            preds = model(X_val_t).argmax(dim=1).numpy()
        macro = f1_score(y_val, preds, average="macro")
        log.info("epoch %2d  train_loss=%.4f  val_macro_f1=%.4f", epoch, train_loss, macro)

        if macro > best_f1 + 1e-4:
            best_f1 = macro
            best_state = {k: v.detach().clone() for k, v in model.state_dict().items()}
            epochs_since_improve = 0
        else:
            epochs_since_improve += 1
            if epochs_since_improve >= args.patience:
                log.info("early stop at epoch %d (best val macro-F1=%.4f)", epoch, best_f1)
                break

    elapsed = time.perf_counter() - t0
    log.info("training done in %.1fs, best val macro-F1=%.4f", elapsed, best_f1)

    model.load_state_dict(best_state)
    Path(settings.model_path).parent.mkdir(parents=True, exist_ok=True)
    torch.save(model.state_dict(), settings.model_path)
    log.info("saved model to %s", settings.model_path)

    label_map = {
        "idx_to_label": {str(i): c for i, c in enumerate(CATEGORIES)},
        "label_to_idx": label_to_idx,
        "best_val_macro_f1": best_f1,
        "trained_seconds": elapsed,
    }
    Path(settings.label_map_path).write_text(json.dumps(label_map, indent=2))
    log.info("saved label map to %s", settings.label_map_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
