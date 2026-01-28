from __future__ import annotations

from datetime import date
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.database import get_db
from app.models import Habit
from app.services.stats import calculate_streak, count_completions

router = APIRouter(prefix="/stats", tags=["stats"])


class StreakResponse(BaseModel):
    habit_id: int
    streak: int


class CompletionsResponse(BaseModel):
    habit_id: int
    count: int
    start_date: Optional[date]
    end_date: Optional[date]


@router.get("/streak/{habit_id}", response_model=StreakResponse)
def get_streak(habit_id: int, db: Session = Depends(get_db)):
    habit = db.query(Habit).filter(Habit.id == habit_id).first()
    if not habit:
        raise HTTPException(status_code=404, detail="Habit not found")

    streak = calculate_streak(db, habit_id)
    return StreakResponse(habit_id=habit_id, streak=streak)


@router.get("/completions/{habit_id}", response_model=CompletionsResponse)
def get_completions(
    habit_id: int,
    start_date: Optional[date] = Query(None),
    end_date: Optional[date] = Query(None),
    db: Session = Depends(get_db),
):
    habit = db.query(Habit).filter(Habit.id == habit_id).first()
    if not habit:
        raise HTTPException(status_code=404, detail="Habit not found")

    count = count_completions(db, habit_id, start_date, end_date)
    return CompletionsResponse(
        habit_id=habit_id,
        count=count,
        start_date=start_date,
        end_date=end_date,
    )
