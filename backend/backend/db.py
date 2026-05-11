from __future__ import annotations

import json
from datetime import datetime, timezone

from sqlalchemy import (
    Column,
    DateTime,
    Integer,
    String,
    Text,
    create_engine,
    select,
)
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from .config import get_settings


class Base(DeclarativeBase):
    pass


class Profile(Base):
    __tablename__ = "profiles"
    user_id = Column(String, primary_key=True)
    profile_json = Column(Text, nullable=False, default="{}")
    updated_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))


class MessageRow(Base):
    __tablename__ = "messages"
    id = Column(Integer, primary_key=True, autoincrement=True)
    user_id = Column(String, index=True, nullable=False)
    message_id = Column(String, index=True, nullable=False)
    sender = Column(String, default="")
    package = Column(String, default="")
    snippet = Column(Text, default="")
    category = Column(String, nullable=True)
    timestamp_ms = Column(Integer, default=0)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))


_engine = None
_SessionLocal: sessionmaker | None = None


def init_engine():
    global _engine, _SessionLocal
    if _engine is not None:
        return
    settings = get_settings()
    _engine = create_engine(settings.database_url, future=True, connect_args={"check_same_thread": False})
    Base.metadata.create_all(_engine)
    _SessionLocal = sessionmaker(bind=_engine, autoflush=False, autocommit=False, future=True)


def session_scope() -> Session:
    init_engine()
    assert _SessionLocal is not None
    return _SessionLocal()


def upsert_profile(user_id: str, profile_dict: dict) -> None:
    with session_scope() as s:
        row = s.get(Profile, user_id)
        if row is None:
            row = Profile(user_id=user_id, profile_json=json.dumps(profile_dict))
            s.add(row)
        else:
            row.profile_json = json.dumps(profile_dict)
            row.updated_at = datetime.now(timezone.utc)
        s.commit()


def get_profile(user_id: str) -> dict | None:
    with session_scope() as s:
        row = s.get(Profile, user_id)
        if row is None:
            return None
        return json.loads(row.profile_json or "{}")


def insert_messages(user_id: str, items: list[dict]) -> int:
    if not items:
        return 0
    with session_scope() as s:
        existing = set(
            s.scalars(
                select(MessageRow.message_id).where(MessageRow.user_id == user_id)
            ).all()
        )
        new_rows: list[MessageRow] = []
        seen_in_batch: set[str] = set()
        for item in items:
            mid = item.get("message_id")
            if not mid or mid in existing or mid in seen_in_batch:
                continue
            seen_in_batch.add(mid)
            new_rows.append(
                MessageRow(
                    user_id=user_id,
                    message_id=mid,
                    sender=item.get("sender", ""),
                    package=item.get("package", ""),
                    snippet=item.get("snippet", ""),
                    category=item.get("category"),
                    timestamp_ms=item.get("timestamp_ms", 0),
                )
            )
        s.add_all(new_rows)
        s.commit()
        return len(new_rows)
