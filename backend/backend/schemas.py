from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class KeyRelationship(BaseModel):
    name: str
    role: str


class UserProfile(BaseModel):
    display_name: str = ""
    occupation: str = ""
    tone: Literal["CASUAL", "PROFESSIONAL", "BRIEF"] = "CASUAL"
    working_hours_start_min: int = 9 * 60
    working_hours_end_min: int = 18 * 60
    communication_style: str = ""
    key_relationships: list[KeyRelationship] = Field(default_factory=list)
    additional_context: str = ""


class CategoryDescriptor(BaseModel):
    id: str
    name: str
    description: str = ""


class ClassifyRequest(BaseModel):
    user_id: str = Field(min_length=1, max_length=128)
    sender: str = ""
    package: str = ""
    message: str
    profile: UserProfile = Field(default_factory=UserProfile)
    categories: list[CategoryDescriptor] = Field(default_factory=list)


class RagEvidenceItem(BaseModel):
    message_id: str
    sender: str
    snippet: str
    category: str | None = None
    score: float = 0.0


class ClassifyResponse(BaseModel):
    category_id: str
    confidence: float
    reasoning: str
    rag_evidence: list[RagEvidenceItem] = Field(default_factory=list)
    cold_start: bool = False
    latency_ms: int = 0
    # Backend-generated reply text. Empty when the chosen category's default
    # action is SUPPRESS / ESCALATE (no reply should be sent). Tone follows
    # profile.tone; falls back to a neutral template when no profile is given.
    suggested_reply: str = ""


class ProfileRequest(BaseModel):
    user_id: str = Field(min_length=1, max_length=128)
    profile: UserProfile


class HistoryItem(BaseModel):
    message_id: str
    sender: str = ""
    package: str = ""
    snippet: str = ""
    timestamp_ms: int = 0
    category: str | None = None


class HistorySyncRequest(BaseModel):
    user_id: str = Field(min_length=1, max_length=128)
    items: list[HistoryItem]


class HealthResponse(BaseModel):
    ok: bool = True
    model_loaded: bool = False
    chroma_ready: bool = False
