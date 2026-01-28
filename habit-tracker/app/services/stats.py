from __future__ import annotations

from datetime import date, timedelta
from typing import Optional, Set

from sqlalchemy.orm import Session

from app.models import Habit, HabitEntry


def calculate_streak(db: Session, habit_id: int) -> int:
    """
    Calculate the current streak for a habit.
    For daily habits: count consecutive days backwards from today.
    For weekly habits: count consecutive weeks with at least 1 entry.
    """
    habit = db.query(Habit).filter(Habit.id == habit_id).first()
    if not habit:
        return 0

    entries = (
        db.query(HabitEntry)
        .filter(HabitEntry.habit_id == habit_id)
        .order_by(HabitEntry.date.desc())
        .all()
    )

    if not entries:
        return 0

    entry_dates = {entry.date for entry in entries}
    today = date.today()

    if habit.frequency == "daily":
        return _calculate_daily_streak(entry_dates, today)
    elif habit.frequency == "weekly":
        return _calculate_weekly_streak(entry_dates, today)
    else:
        return _calculate_daily_streak(entry_dates, today)


def _calculate_daily_streak(entry_dates: Set[date], today: date) -> int:
    """Count consecutive days backwards from today (or yesterday if today not checked)."""
    streak = 0
    current_date = today

    if current_date not in entry_dates:
        current_date = today - timedelta(days=1)

    while current_date in entry_dates:
        streak += 1
        current_date -= timedelta(days=1)

    return streak


def _calculate_weekly_streak(entry_dates: Set[date], today: date) -> int:
    """Count consecutive weeks with at least 1 entry."""
    if not entry_dates:
        return 0

    streak = 0
    current_week_start = today - timedelta(days=today.weekday())

    def has_entry_in_week(week_start: date) -> bool:
        week_end = week_start + timedelta(days=6)
        return any(week_start <= d <= week_end for d in entry_dates)

    if not has_entry_in_week(current_week_start):
        current_week_start -= timedelta(weeks=1)

    while has_entry_in_week(current_week_start):
        streak += 1
        current_week_start -= timedelta(weeks=1)

    return streak


def count_completions(
    db: Session,
    habit_id: int,
    start_date: Optional[date] = None,
    end_date: Optional[date] = None,
) -> int:
    """Count the number of entries for a habit within a date range."""
    query = db.query(HabitEntry).filter(HabitEntry.habit_id == habit_id)

    if start_date is not None:
        query = query.filter(HabitEntry.date >= start_date)
    if end_date is not None:
        query = query.filter(HabitEntry.date <= end_date)

    return query.count()
