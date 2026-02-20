"""
Atomic Sync Routes
Single unified endpoint for atomic batch synchronization.
"""
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
import time
import logging

from ..database import get_db
from ..schemas_atomic import (
    AtomicSyncRequest,
    AtomicSyncResponse
)
from ..services.atomic_sync_service import AtomicSyncService

router = APIRouter()
logger = logging.getLogger(__name__)


@router.post("/atomic", response_model=AtomicSyncResponse)
async def atomic_sync(
    request: AtomicSyncRequest,
    db: Session = Depends(get_db)
):
    """
    Atomic batch sync endpoint.
    Each group is processed as an atomic transaction (all-or-nothing).
    
    - All operations in a group succeed together
    - Or all operations in a group fail and rollback together
    - Groups are independent of each other
    """
    try:
        logger.info(f"Received atomic sync request with {len(request.groups)} groups")
        
        service = AtomicSyncService(db)
        
        # Process all groups
        group_results = service.process_groups(
            groups=request.groups,
            client_timestamp=request.client_timestamp
        )
        
        # Commit outer transaction (all savepoints already committed/rolled back)
        db.commit()
        
        # Count successes and failures
        success_count = sum(1 for r in group_results if r.success)
        failure_count = len(group_results) - success_count
        
        logger.info(
            f"Atomic sync completed: {success_count} succeeded, "
            f"{failure_count} failed"
        )
        
        return AtomicSyncResponse(
            group_results=group_results,
            server_timestamp=int(time.time() * 1000)
        )
        
    except Exception as e:
        db.rollback()
        logger.error(f"Atomic sync failed: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))
