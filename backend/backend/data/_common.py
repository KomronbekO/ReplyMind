from __future__ import annotations

import logging
import zipfile
from io import BytesIO
from pathlib import Path
from typing import Iterable

import requests

log = logging.getLogger("replymind.data")

DATA_ROOT = Path(__file__).resolve().parents[2] / "data"
RAW_ROOT = DATA_ROOT / "raw"


def ensure_raw_dir(name: str) -> Path:
    p = RAW_ROOT / name
    p.mkdir(parents=True, exist_ok=True)
    return p


def fetch_zip(url: str, dest_dir: Path, *, timeout: int = 90) -> None:
    """Download a zip and extract into dest_dir. Idempotent: skips if dest_dir is non-empty."""
    if any(dest_dir.iterdir()):
        return
    log.info("fetching %s", url)
    r = requests.get(url, timeout=timeout, headers={"User-Agent": "replymind-backend/0.1"})
    r.raise_for_status()
    with zipfile.ZipFile(BytesIO(r.content)) as z:
        z.extractall(dest_dir)


def fetch_file(url: str, dest: Path, *, timeout: int = 90) -> None:
    """Download a single file. Idempotent."""
    if dest.exists() and dest.stat().st_size > 0:
        return
    dest.parent.mkdir(parents=True, exist_ok=True)
    log.info("fetching %s", url)
    r = requests.get(url, timeout=timeout, headers={"User-Agent": "replymind-backend/0.1"})
    r.raise_for_status()
    dest.write_bytes(r.content)


def clean(text: str) -> str:
    """Whitespace-collapse + strip; drop CR/LF runs that hurt embedding quality."""
    if text is None:
        return ""
    parts = [p.strip() for p in str(text).splitlines() if p and p.strip()]
    return " ".join(parts)


def take_balanced(rows: Iterable[dict], n: int) -> list[dict]:
    """Take up to n rows, preserving order — used to cap a source's contribution."""
    out: list[dict] = []
    for i, r in enumerate(rows):
        if i >= n:
            break
        out.append(r)
    return out
