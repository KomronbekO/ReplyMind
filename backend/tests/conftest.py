from __future__ import annotations

import os
import tempfile
from pathlib import Path

# Per-pytest-session temp DB + chroma dir, so each run is hermetic. The
# environment variables must be set BEFORE any backend module is imported.
_tmp = Path(tempfile.mkdtemp(prefix="replymind-test-"))
os.environ["DEMO_TOKEN"] = "test-token"
os.environ["DATABASE_URL"] = f"sqlite:///{_tmp / 'app.db'}"
os.environ["CHROMA_DIR"] = str(_tmp / "chroma")
