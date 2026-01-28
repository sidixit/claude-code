from __future__ import annotations

from datetime import datetime, date
from typing import TYPE_CHECKING, Optional

from sqlalchemy import String, DateTime, Date, ForeignKey, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base

if TYPE_CHECKING:
    from app.models.habit import Habit


class HabitEntry(Base):
    __tablename__ = "habit_entries"

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    habit_id: Mapped[int] = mapped_column(ForeignKey("habits.id"), nullable=False)
    date: Mapped[date] = mapped_column(Date, nullable=False)
    note: Mapped[Optional[str]] = mapped_column(String(500), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
    # Future: user_id: Mapped[int] = mapped_column(ForeignKey("users.id"))

    habit: Mapped["Habit"] = relationship("Habit", back_populates="entries")

    __table_args__ = (
        UniqueConstraint("habit_id", "date", name="uq_habit_date"),
    )
