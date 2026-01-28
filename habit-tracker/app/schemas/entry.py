from __future__ import annotations

from datetime import datetime, date
from typing import Optional

from pydantic import BaseModel, Field


class EntryBase(BaseModel):
    habit_id: int
    date: date
    note: Optional[str] = Field(None, max_length=500)


class EntryCreate(EntryBase):
    pass


class EntryUpdate(BaseModel):
    date: Optional[date] = None
    note: Optional[str] = Field(None, max_length=500)


class EntryResponse(EntryBase):
    id: int
    created_at: datetime

    model_config = {"from_attributes": True}
