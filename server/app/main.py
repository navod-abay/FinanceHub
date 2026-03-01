from fastapi import FastAPI, Depends, HTTPException, Request, Response
from sqlalchemy.orm import Session
import time
import logging
import json
from typing import Callable

from .database import get_db, create_tables
from .config import settings
from .routes import operations, sync, query, batch_sync, atomic_sync

# Setup logger
logger = logging.getLogger(__name__)

# Create FastAPI app
app = FastAPI(
    title="FinanceHub API",
    description="Personal Finance Tracking API with sync capabilities",
    version="1.0.0",
    debug=settings.debug
)


@app.middleware("http")
async def log_requests(request: Request, call_next: Callable):
    """
    Log all incoming requests and outgoing responses for debugging.
    """
    start_time = time.time()
    
    # Log incoming request
    logger.info(
        f">>> REQUEST: {request.method} {request.url.path}\n"
        f"  Client: {request.client.host if request.client else 'unknown'}\n"
        f"  Query Params: {dict(request.query_params)}"
    )
    
    # For POST/PUT requests, log the body (if JSON)
    if request.method in ["POST", "PUT", "PATCH"]:
        try:
            body_bytes = await request.body()
            if body_bytes:
                # Try to parse as JSON for prettier logging
                try:
                    body_json = json.loads(body_bytes)
                    # Limit logged body size to avoid flooding logs
                    body_str = json.dumps(body_json, indent=2)
                    if len(body_str) > 1000:
                        body_str = body_str[:1000] + "... (truncated)"
                    logger.debug(f"  Request Body:\n{body_str}")
                except json.JSONDecodeError:
                    logger.debug(f"  Request Body: {body_bytes[:500]}... (non-JSON)")
        except Exception as e:
            logger.warning(f"  Could not read request body: {e}")
    
    # Process the request
    try:
        response = await call_next(request)
        
        # Calculate duration
        duration_ms = (time.time() - start_time) * 1000
        
        # Log response
        logger.info(
            f"<<< RESPONSE: {request.method} {request.url.path}\n"
            f"  Status: {response.status_code}\n"
            f"  Duration: {duration_ms:.2f}ms"
        )
        
        return response
        
    except Exception as e:
        duration_ms = (time.time() - start_time) * 1000
        logger.error(
            f"<<< ERROR: {request.method} {request.url.path}\n"
            f"  Exception: {str(e)}\n"
            f"  Duration: {duration_ms:.2f}ms",
            exc_info=True
        )
        raise


# Create database tables on startup
@app.on_event("startup")
async def startup_event():
    # Configure logging
    log_level = logging.DEBUG if settings.debug else logging.INFO
    logging.basicConfig(
        level=log_level,
        format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
        datefmt='%Y-%m-%d %H:%M:%S'
    )
    
    # Set specific log levels for noisy libraries
    logging.getLogger("uvicorn.access").setLevel(logging.WARNING)
    
    logger.info("🚀 FinanceHub API starting...")
    logger.info(f"📊 Debug Mode: {settings.debug}")
    logger.info(f"📝 Log Level: {logging.getLevelName(log_level)}")
    
    create_tables()
    logger.info("✅ Database tables created/verified")
    logger.info("🎯 API ready to accept requests")

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
app.include_router(batch_sync.router, prefix="/api/v1/sync", tags=["batch-sync"])
app.include_router(atomic_sync.router, prefix="/api/v1/sync", tags=["atomic-sync"])
app.include_router(query.router, prefix="/api/v1", tags=["query"])

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host=settings.host,
        port=settings.port,
        reload=settings.debug
    )