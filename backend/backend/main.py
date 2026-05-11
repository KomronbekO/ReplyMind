from __future__ import annotations

import logging
import time
import uuid
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import Depends, FastAPI, HTTPException, status

from . import db
from .auth import require_bearer
from .config import get_settings
from .schemas import (
    ClassifyRequest,
    ClassifyResponse,
    HealthResponse,
    HistorySyncRequest,
    ProfileRequest,
)

log = logging.getLogger("replymind.backend")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")

@asynccontextmanager
async def lifespan(app: FastAPI):
    db.init_engine()
    log.info("backend started")
    yield


app = FastAPI(title="ReplyMind backend", version="0.1.0", lifespan=lifespan)


@app.get("/healthz", response_model=HealthResponse)
def healthz() -> HealthResponse:
    settings = get_settings()
    model_loaded = Path(settings.model_path).exists() and Path(settings.label_map_path).exists()
    chroma_ready = Path(settings.chroma_dir).exists()
    return HealthResponse(ok=True, model_loaded=model_loaded, chroma_ready=chroma_ready)


@app.post("/profile", dependencies=[Depends(require_bearer)])
def upsert_profile(req: ProfileRequest) -> dict:
    db.upsert_profile(req.user_id, req.profile.model_dump())
    return {"ok": True}


@app.post("/history/sync", dependencies=[Depends(require_bearer)])
def history_sync(req: HistorySyncRequest) -> dict:
    inserted = db.insert_messages(req.user_id, [item.model_dump() for item in req.items])
    return {"ok": True, "inserted": inserted, "received": len(req.items)}


@app.post(
    "/classify",
    response_model=ClassifyResponse,
    dependencies=[Depends(require_bearer)],
)
def classify(req: ClassifyRequest) -> ClassifyResponse:
    t0 = time.perf_counter()
    try:
        from .classifier import classify_message  # lazy: model load is heavy
    except Exception as e:
        log.warning("classifier unavailable: %s", e)
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=f"classifier not ready: {e}",
        )

    result = classify_message(req)
    result.latency_ms = int((time.perf_counter() - t0) * 1000)
    # opportunistically persist a copy of the message for future RAG retrieval
    db.insert_messages(
        req.user_id,
        [
            {
                "message_id": str(uuid.uuid4()),
                "sender": req.sender,
                "package": req.package,
                "snippet": req.message[:280],
                "category": result.category_id,
                "timestamp_ms": int(time.time() * 1000),
            }
        ],
    )
    return result
