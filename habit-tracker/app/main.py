from pathlib import Path

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, FileResponse
from fastapi.staticfiles import StaticFiles

from app.database import engine, Base
from app.routers import habits_router, entries_router, stats_router

Base.metadata.create_all(bind=engine)

app = FastAPI(
    title="Habit Tracker API",
    description="A REST API for tracking habits and check-ins",
    version="1.0.0",
)

# Get the directory where main.py is located
BASE_DIR = Path(__file__).resolve().parent

# Mount static files
app.mount("/static", StaticFiles(directory=BASE_DIR / "static"), name="static")

app.include_router(habits_router)
app.include_router(entries_router)
app.include_router(stats_router)


@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    return JSONResponse(
        status_code=500,
        content={"detail": "Internal server error"},
    )


@app.get("/")
def root():
    return FileResponse(BASE_DIR / "templates" / "index.html")


@app.get("/api")
def api_info():
    return {"message": "Habit Tracker API", "docs": "/docs"}
