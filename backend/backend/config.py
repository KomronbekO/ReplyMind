from __future__ import annotations

from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    demo_token: str = "change-me"
    database_url: str = "sqlite:///./data/app.db"
    chroma_dir: str = "./data/chroma"
    model_path: str = "./models/classifier.pt"
    label_map_path: str = "./models/label_map.json"
    embedder_name: str = "sentence-transformers/all-MiniLM-L6-v2"

    rag_k_sender: int = 3
    rag_k_global: int = 2
    rag_weight_current: float = 0.6
    rag_weight_sender: float = 0.3
    rag_weight_global: float = 0.1

    # Confidence-gated semantic backstop. When the MLP's top-1 confidence is
    # below `low_confidence_threshold` AND the gap to top-2 is below
    # `ambiguous_margin`, fall back to a cosine match against the user-supplied
    # category descriptions instead of trusting the argmax.
    low_confidence_threshold: float = 0.70
    ambiguous_margin: float = 0.40


@lru_cache
def get_settings() -> Settings:
    s = Settings()
    Path(s.chroma_dir).mkdir(parents=True, exist_ok=True)
    db_path = s.database_url.replace("sqlite:///", "", 1)
    Path(db_path).parent.mkdir(parents=True, exist_ok=True)
    return s
