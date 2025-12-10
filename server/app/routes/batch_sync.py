from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import List
import time
from datetime import datetime

from ..database import get_db
from ..models import Expense, Tag, Target, ExpenseTagsCrossRef, GraphEdge
from ..schemas import (
    BatchSyncExpensesRequest, BatchSyncTagsRequest, BatchSyncTargetsRequest,
    BatchSyncExpenseTagsRequest, BatchSyncGraphEdgesRequest,
    BatchSyncResponse, SyncResultType,
    CreateExpenseBatchRequest, UpdateExpenseBatchRequest, DeleteExpenseBatchRequest,
    ApiExpense, ApiTag, ApiTarget, ApiExpenseTag, ApiGraphEdge,
    UpdatedDataResponse
)

router = APIRouter()


@router.post("/batch/expenses", response_model=BatchSyncResponse)
async def batch_sync_expenses(
    request: BatchSyncExpensesRequest,
    db: Session = Depends(get_db)
):
    """
    Batch sync expenses with CREATE/UPDATE/DELETE operations
    """
    results: List[SyncResultType] = []
    
    try:
        for operation in request.operations:
            try:
                if operation.type == "create_expense":
                    # Create new expense
                    new_expense = Expense(
                        title=operation.title,
                        amount=operation.amount,
                        year=operation.year,
                        month=operation.month,
                        date=operation.date,
                        created_at=datetime.utcnow(),
                        updated_at=datetime.utcnow()
                    )
                    db.add(new_expense)
                    db.flush()  # Get the ID without committing
                    
                    results.append(SyncResultType(
                        success=True,
                        client_id=operation.client_id,
                        server_id=str(new_expense.id)
                    ))
                    
                elif operation.type == "update_expense":
                    # Update existing expense
                    expense = db.query(Expense).filter(Expense.id == operation.server_id).first()
                    if expense:
                        expense.title = operation.title
                        expense.amount = operation.amount
                        expense.year = operation.year
                        expense.month = operation.month
                        expense.date = operation.date
                        expense.updated_at = datetime.utcnow()
                        
                        results.append(SyncResultType(
                            success=True,
                            client_id=None,
                            server_id=operation.server_id
                        ))
                    else:
                        results.append(SyncResultType(
                            success=False,
                            client_id=None,
                            server_id=operation.server_id,
                            error="Expense not found"
                        ))
                        
                elif operation.type == "delete_expense":
                    # Soft delete expense
                    expense = db.query(Expense).filter(Expense.id == operation.server_id).first()
                    if expense:
                        expense.deleted_at = datetime.utcnow()
                        
                        results.append(SyncResultType(
                            success=True,
                            client_id=None,
                            server_id=operation.server_id
                        ))
                    else:
                        results.append(SyncResultType(
                            success=False,
                            client_id=None,
                            server_id=operation.server_id,
                            error="Expense not found"
                        ))
                        
            except Exception as e:
                results.append(SyncResultType(
                    success=False,
                    client_id=getattr(operation, 'client_id', None),
                    server_id=getattr(operation, 'server_id', None),
                    error=str(e)
                ))
        
        # Commit all changes
        db.commit()
        
        return BatchSyncResponse(results=results)
        
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/updated-data", response_model=UpdatedDataResponse)
async def get_updated_data(
    since: int,
    db: Session = Depends(get_db)
):
    """
    Get all data updated since the given timestamp
    Returns data in ApiExpense, ApiTag, etc. format
    """
    try:
        # Convert timestamp (milliseconds) to datetime
        since_datetime = datetime.fromtimestamp(since / 1000.0)
        
        # Query expenses
        expenses = db.query(Expense).filter(
            Expense.deleted_at.is_(None),
            Expense.updated_at > since_datetime
        ).all()
        
        expense_responses = [
            ApiExpense(
                id=str(exp.id),
                title=exp.title,
                amount=exp.amount,
                year=exp.year,
                month=exp.month,
                date=exp.date,
                created_at=int(exp.created_at.timestamp() * 1000) if exp.created_at else 0,
                updated_at=int(exp.updated_at.timestamp() * 1000) if exp.updated_at else 0
            )
            for exp in expenses
        ]
        
        # For now, return empty lists for other entities (will implement later)
        return UpdatedDataResponse(
            expenses=expense_responses,
            tags=[],
            targets=[],
            expense_tags=[],
            graph_edges=[]
        )
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
