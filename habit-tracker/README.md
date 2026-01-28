# Habit Tracker API

A REST API for tracking habits and check-ins built with FastAPI, SQLAlchemy, and SQLite.

## Features

- Create and manage habits with different frequencies (daily, weekly, custom)
- Track check-ins (entries) for each habit
- Calculate streaks for daily and weekly habits
- Count completions within date ranges
- Automatic API documentation via Swagger UI

## Project Structure

```
habit-tracker/
├── app/
│   ├── __init__.py
│   ├── main.py              # FastAPI app entry point
│   ├── database.py          # SQLAlchemy engine, session, Base
│   ├── models/
│   │   ├── __init__.py
│   │   ├── habit.py         # Habit ORM model
│   │   └── entry.py         # HabitEntry ORM model
│   ├── schemas/
│   │   ├── __init__.py
│   │   ├── habit.py         # Pydantic schemas for habits
│   │   └── entry.py         # Pydantic schemas for entries
│   ├── routers/
│   │   ├── __init__.py
│   │   ├── habits.py        # /habits endpoints
│   │   ├── entries.py       # /entries endpoints
│   │   └── stats.py         # /stats endpoints
│   └── services/
│       ├── __init__.py
│       └── stats.py         # Stats calculation logic
├── requirements.txt
└── README.md
```

## Installation

```bash
cd habit-tracker
python3 -m pip install --user -r requirements.txt
```

## Running the Server

```bash
python3 -m uvicorn app.main:app --reload
```

The API will be available at `http://127.0.0.1:8000`

- Swagger UI: http://127.0.0.1:8000/docs
- OpenAPI schema: http://127.0.0.1:8000/openapi.json

## API Endpoints

### Habits (`/habits`)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/habits` | Create a habit |
| GET | `/habits` | List all habits (filter: `is_active`) |
| GET | `/habits/{id}` | Get single habit |
| PUT | `/habits/{id}` | Update habit |
| DELETE | `/habits/{id}` | Delete habit |

### Entries (`/entries`)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/entries` | Create check-in |
| GET | `/entries` | List entries (filter: `habit_id`, `start_date`, `end_date`) |
| GET | `/entries/{id}` | Get single entry |
| PUT | `/entries/{id}` | Update entry |
| DELETE | `/entries/{id}` | Delete entry |

### Stats (`/stats`)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/stats/streak/{habit_id}` | Current streak for habit |
| GET | `/stats/completions/{habit_id}` | Count in date range |

## Usage Examples

### Create a habit

```bash
curl -X POST http://127.0.0.1:8000/habits \
  -H "Content-Type: application/json" \
  -d '{"name": "Exercise", "frequency": "daily"}'
```

Response:
```json
{
  "name": "Exercise",
  "frequency": "daily",
  "id": 1,
  "is_active": true,
  "created_at": "2026-01-28T06:09:27.811316"
}
```

### Create a check-in

```bash
curl -X POST http://127.0.0.1:8000/entries \
  -H "Content-Type: application/json" \
  -d '{"habit_id": 1, "date": "2026-01-27", "note": "Morning run"}'
```

Response:
```json
{
  "habit_id": 1,
  "date": "2026-01-27",
  "note": "Morning run",
  "id": 1,
  "created_at": "2026-01-27T10:30:00.000000"
}
```

### Get streak

```bash
curl http://127.0.0.1:8000/stats/streak/1
```

Response:
```json
{
  "habit_id": 1,
  "streak": 1
}
```

### Get completions in date range

```bash
curl "http://127.0.0.1:8000/stats/completions/1?start_date=2026-01-01&end_date=2026-01-31"
```

Response:
```json
{
  "habit_id": 1,
  "count": 5,
  "start_date": "2026-01-01",
  "end_date": "2026-01-31"
}
```

## Data Models

### Habit

| Field | Type | Description |
|-------|------|-------------|
| id | int | Primary key, auto-increment |
| name | str | Required, max 100 chars |
| frequency | str | Enum: "daily", "weekly", "custom" |
| is_active | bool | Default true |
| created_at | datetime | Auto-set on creation |

### HabitEntry

| Field | Type | Description |
|-------|------|-------------|
| id | int | Primary key |
| habit_id | int | FK to habits.id |
| date | date | The check-in date |
| note | str | Optional, max 500 chars |
| created_at | datetime | Auto-set |

Note: There is a unique constraint on `(habit_id, date)` to prevent duplicate entries per habit per day.

## Streak Calculation

- **Daily habits**: Count consecutive days backwards from today (or yesterday if today not checked)
- **Weekly habits**: Count consecutive weeks with at least 1 entry

## Tech Stack

- **FastAPI** - Modern web framework for building APIs
- **SQLAlchemy** - SQL toolkit and ORM
- **SQLite** - Lightweight database
- **Pydantic** - Data validation using Python type annotations
- **Uvicorn** - ASGI server
