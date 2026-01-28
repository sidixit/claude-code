from __future__ import annotations

from datetime import datetime
from enum import Enum
from typing import Optional

from pydantic import BaseModel, Field


class FrequencyEnum(str, Enum):
    daily = "daily"
    weekly = "weekly"
    custom = "custom"


class HabitBase(BaseModel):
    name: str = Field(..., max_length=100)
    frequency: FrequencyEnum


class HabitCreate(HabitBase):
    pass


class HabitUpdate(BaseModel):
    name: Optional[str] = Field(None, max_length=100)
    frequency: Optional[FrequencyEnum] = None
    is_active: Optional[bool] = None


class HabitResponse(HabitBase):
    id: int
    is_active: bool
    created_at: datetime

    model_config = {"from_attributes": True}
