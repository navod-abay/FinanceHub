from fastapi import FastAPI, Depends, HTTPException
from sqlalchemy.orm import Session
import time

from .database import get_db, create_tables
from .config import settings
from .routes import operations, sync, query

# Create FastAPI app
app = FastAPI(
    title="FinanceHub API",
    description="Personal Finance Tracking API with sync capabilities",
    version="1.0.0",
    debug=settings.debug
)

# Create database tables on startup
@app.on_event("startup")
async def startup_event():
    create_tables()

# Health check endpoint
@app.get("/health")
async def health_check():
    return {
        "status": "healthy",
        "timestamp": int(time.time()),
        "version": "1.0.0"
    }

# Include routers
app.include_router(operations.router, prefix="/api/v1/operations", tags=["operations"])
app.include_router(sync.router, prefix="/api/v1/sync", tags=["sync"])
app.include_router(query.router, prefix="/api/v1", tags=["query"])

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host=settings.host,
        port=settings.port,
        reload=settings.debug
    )