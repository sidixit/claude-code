from datetime import date
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from sqlalchemy.exc import IntegrityError

from app.database import get_db
from app.models import Habit, HabitEntry
from app.schemas import EntryCreate, EntryUpdate, EntryResponse

router = APIRouter(prefix="/entries", tags=["entries"])


@router.post("", response_model=EntryResponse, status_code=201)
def create_entry(entry: EntryCreate, db: Session = Depends(get_db)):
    habit = db.query(Habit).filter(Habit.id == entry.habit_id).first()
    if not habit:
        raise HTTPException(status_code=404, detail="Habit not found")

    db_entry = HabitEntry(
        habit_id=entry.habit_id,
        date=entry.date,
        note=entry.note,
    )
    try:
        db.add(db_entry)
        db.commit()
        db.refresh(db_entry)
    except IntegrityError:
        db.rollback()
        raise HTTPException(
            status_code=400,
            detail="Entry already exists for this habit on this date",
        )
    return db_entry


@router.get("", response_model=List[EntryResponse])
def list_entries(
    habit_id: Optional[int] = Query(None),
    start_date: Optional[date] = Query(None),
    end_date: Optional[date] = Query(None),
    db: Session = Depends(get_db),
):
    query = db.query(HabitEntry)

    if habit_id is not None:
        query = query.filter(HabitEntry.habit_id == habit_id)
    if start_date is not None:
        query = query.filter(HabitEntry.date >= start_date)
    if end_date is not None:
        query = query.filter(HabitEntry.date <= end_date)

    return query.order_by(HabitEntry.date.desc()).all()


@router.get("/{entry_id}", response_model=EntryResponse)
def get_entry(entry_id: int, db: Session = Depends(get_db)):
    entry = db.query(HabitEntry).filter(HabitEntry.id == entry_id).first()
    if not entry:
        raise HTTPException(status_code=404, detail="Entry not found")
    return entry


@router.put("/{entry_id}", response_model=EntryResponse)
def update_entry(entry_id: int, entry_update: EntryUpdate, db: Session = Depends(get_db)):
    entry = db.query(HabitEntry).filter(HabitEntry.id == entry_id).first()
    if not entry:
        raise HTTPException(status_code=404, detail="Entry not found")

    update_data = entry_update.model_dump(exclude_unset=True)
    for field, value in update_data.items():
        setattr(entry, field, value)

    try:
        db.commit()
        db.refresh(entry)
    except IntegrityError:
        db.rollback()
        raise HTTPException(
            status_code=400,
            detail="Entry already exists for this habit on this date",
        )
    return entry


@router.delete("/{entry_id}", status_code=204)
def delete_entry(entry_id: int, db: Session = Depends(get_db)):
    entry = db.query(HabitEntry).filter(HabitEntry.id == entry_id).first()
    if not entry:
        raise HTTPException(status_code=404, detail="Entry not found")

    db.delete(entry)
    db.commit()
    return None
