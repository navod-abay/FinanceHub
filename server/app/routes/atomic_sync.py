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
    logger.info("[ATOMIC_SYNC] ===== VALIDATION PASSED - ENTERED ROUTE HANDLER =====")
    start_time = time.time()
    
    try:
        # Log request summary
        total_operations = sum(len(group.operations) for group in request.groups)
        logger.info(
            f"========== ATOMIC SYNC REQUEST ==========\n"
            f"Groups: {len(request.groups)}\n"
            f"Total Operations: {total_operations}\n"
            f"Client Timestamp: {request.client_timestamp}"
        )
        
        # Log each group's details
        for idx, group in enumerate(request.groups):
            operation_types = {}
            for op in group.operations:
                op_type = type(op).__name__
                operation_types[op_type] = operation_types.get(op_type, 0) + 1
            
            logger.debug(
                f"Group {idx + 1} (id={group.group_id}):\n"
                f"  Operations: {len(group.operations)}\n"
                f"  Types: {operation_types}"
            )
        
        service = AtomicSyncService(db)
        
        logger.info("[DB] Starting group processing...")
        # Process all groups
        group_results = service.process_groups(
            groups=request.groups,
            client_timestamp=request.client_timestamp
        )
        
        logger.info(f"[OUTER COMMIT] ========== ALL GROUPS PROCESSED ==========")
        logger.info(f"[DB PRE-OUTER-COMMIT] Session state - dirty: {len(db.dirty)}, new: {len(db.new)}, deleted: {len(db.deleted)}")
        logger.info(f"[DB PRE-OUTER-COMMIT] Session in transaction: {db.in_transaction()}")
        logger.info(f"[DB PRE-OUTER-COMMIT] Session is active: {db.is_active}")
        
        # Commit outer transaction (all savepoints already committed/rolled back)
        logger.info("[OUTER COMMIT] Calling db.commit() on outer transaction...")
        db.commit()
        logger.info("[OUTER COMMIT] ✓✓✓ OUTER TRANSACTION COMMITTED ✓✓✓")
        
        logger.info(f"[DB POST-OUTER-COMMIT] Session state - dirty: {len(db.dirty)}, new: {len(db.new)}, deleted: {len(db.deleted)}")
        logger.info(f"[DB POST-OUTER-COMMIT] Session in transaction: {db.in_transaction()}")
        logger.info(f"[DB POST-OUTER-COMMIT] Session is active: {db.is_active}")
        
        # Count successes for verification
        success_count = sum(1 for r in group_results if r.success)
        
        # Verify data was written (for debugging)
        logger.info("[DB VERIFY] ========== VERIFYING DATA IN DATABASE ==========")
        if success_count > 0:
            try:
                from ..models import Expense, Tag, ExpenseTagsCrossRef
                logger.info("[DB VERIFY] Querying database for counts...")
                expense_count = db.query(Expense).count()
                logger.info(f"[DB VERIFY] Expense query returned: {expense_count}")
                tag_count = db.query(Tag).count()
                logger.info(f"[DB VERIFY] Tag query returned: {tag_count}")
                expense_tag_count = db.query(ExpenseTagsCrossRef).count()
                logger.info(f"[DB VERIFY] ExpenseTag query returned: {expense_tag_count}")
                
                logger.info(f"[DB VERIFY] ✓ TOTALS - Expenses: {expense_count}, Tags: {tag_count}, ExpenseTags: {expense_tag_count}")
                
                # Query specific items created in this sync
                if expense_count > 0:
                    recent = db.query(Expense).order_by(Expense.created_at.desc()).limit(3).all()
                    logger.info(f"[DB VERIFY] Recent expenses: {[f'{e.id}:{e.title}' for e in recent]}")
                if tag_count > 0:
                    recent_tags = db.query(Tag).order_by(Tag.created_at.desc()).limit(3).all()
                    logger.info(f"[DB VERIFY] Recent tags: {[f'{t.id}:{t.tag}' for t in recent_tags]}")
                
                # Commit the read transaction to avoid rollback warning on session close
                db.commit()
            except Exception as e:
                logger.error(f"[DB VERIFY] ERROR during verification: {e}", exc_info=True)
        
        # Count failures
        failure_count = len(group_results) - success_count
        
        # Log detailed results
        for result in group_results:
            if result.success:
                logger.info(
                    f"✅ Group {result.group_id}: SUCCESS\n"
                    f"  Entity Mappings: {len(result.entity_mappings)}\n"
                    f"  Rolled Back: {result.rolled_back}"
                )
                # Log individual mappings at debug level
                for mapping in result.entity_mappings:
                    logger.debug(
                        f"  Mapping: {mapping.entity_type} "
                        f"{mapping.client_id} -> {mapping.server_id}"
                    )
            else:
                logger.error(
                    f"❌ Group {result.group_id}: FAILED\n"
                    f"  Error: {result.error}\n"
                    f"  Rolled Back: {result.rolled_back}"
                )
        
        elapsed_ms = (time.time() - start_time) * 1000
        logger.info(
            f"========== ATOMIC SYNC COMPLETE ==========\n"
            f"Success: {success_count}/{len(group_results)} groups\n"
            f"Failed: {failure_count}/{len(group_results)} groups\n"
            f"Duration: {elapsed_ms:.2f}ms"
        )
        
        return AtomicSyncResponse(
            group_results=group_results,
            server_timestamp=int(time.time() * 1000)
        )
        
    except Exception as e:
        elapsed_ms = (time.time() - start_time) * 1000
        logger.error(f"[DB] Exception caught - performing ROLLBACK")
        db.rollback()
        logger.error(f"[DB] ✓ Rollback completed")
        logger.error(
            f"========== ATOMIC SYNC FATAL ERROR ==========\n"
            f"Error: {str(e)}\n"
            f"Duration: {elapsed_ms:.2f}ms",
            exc_info=True
        )
        raise HTTPException(status_code=500, detail=str(e))
