from fastapi import FastAPI, Depends, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from sqlalchemy.orm import Session
import time
import logging
import json

from .database import get_db, create_tables
from .config import settings
from .routes import operations, sync, query, batch_sync, atomic_sync
from .logging_config import setup_logging

# Setup logging
setup_logging()
logger = logging.getLogger(__name__)

# Create FastAPI app
app = FastAPI(
    title="FinanceHub API",
    description="Personal Finance Tracking API with sync capabilities",
    version="1.0.0",
    debug=settings.debug
)

# Add CORS middleware for development
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # In production, specify exact origins
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Validation error handler
@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    logger.error(f"Validation error for {request.method} {request.url.path}")
    logger.error(f"Validation errors: {json.dumps(exc.errors(), indent=2)}")
    
    # Read body carefully to avoid consuming it
    try:
        body = await request.body()
        logger.error(f"Request body: {body.decode('utf-8')[:500]}")
    except:
        pass
    
    return JSONResponse(
        status_code=422,
        content={
            "detail": exc.errors(),
            "body": str(exc.body) if hasattr(exc, 'body') else None
        }
    )

# Request logging middleware
@app.middleware("http")
async def log_requests(request: Request, call_next):
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
        
        response.headers["X-Process-Time"] = str(duration_ms)
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
    logger.info("🚀 FinanceHub API starting...")
    logger.info(f"📊 Debug Mode: {settings.debug}")
    logger.info(f"🌐 Host: {settings.host}:{settings.port}")
    # Mask sensitive database credentials in logs
    db_url_masked = settings.database_url.split('@')[-1] if '@' in settings.database_url else settings.database_url.split('///')[0] + '///<masked>'
    logger.info(f"🗄️  Database: ...{db_url_masked}")
    
    create_tables()
    logger.info("✅ Database tables created/verified")
    logger.info("🎯 API ready to accept requests")

@app.on_event("shutdown")
async def shutdown_event():
    logger.info("👋 Shutting down FinanceHub API server...")

# Health check endpoint
@app.get("/health")
async def health_check():
    return {
        "status": "healthy",
        "timestamp": int(time.time()),
        "version": "1.0.0"
    }

# Debug info endpoint (only in debug mode)
@app.get("/debug/info")
async def debug_info():
    if not settings.debug:
        raise HTTPException(status_code=404, detail="Not found")
    
    return {
        "debug_mode": settings.debug,
        "host": settings.host,
        "port": settings.port,
        "database_type": "postgresql" if "postgresql" in settings.database_url else "sqlite",
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