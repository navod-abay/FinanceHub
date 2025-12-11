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
        
        # Query tags
        tags = db.query(Tag).filter(
            Tag.deleted_at.is_(None),
            Tag.updated_at > since_datetime
        ).all()
        
        tag_responses = [
            ApiTag(
                id=str(tag.id),
                name=tag.name,
                monthly_amount=tag.monthly_amount,
                current_month=tag.current_month,
                current_year=tag.current_year,
                created_day=tag.created_day,
                created_month=tag.created_month,
                created_year=tag.created_year,
                created_at=int(tag.created_at.timestamp() * 1000) if tag.created_at else 0,
                updated_at=int(tag.updated_at.timestamp() * 1000) if tag.updated_at else 0
            )
            for tag in tags
        ]
        
        # Query targets
        targets = db.query(Target).filter(
            Target.updated_at > since_datetime
        ).all()
        
        target_responses = [
            ApiTarget(
                id=str(target.id),
                month=target.month,
                year=target.year,
                tag_id=str(target.tag_id),
                amount=target.amount,
                spent=target.spent,
                created_at=int(target.created_at.timestamp() * 1000) if target.created_at else 0,
                updated_at=int(target.updated_at.timestamp() * 1000) if target.updated_at else 0
            )
            for target in targets
        ]
        
        # Query expense tags
        expense_tags = db.query(ExpenseTagsCrossRef).filter(
            ExpenseTagsCrossRef.updated_at > since_datetime
        ).all()
        
        expense_tag_responses = [
            ApiExpenseTag(
                id=str(et.id),
                expense_id=str(et.expense_id),
                tag_id=str(et.tag_id),
                created_at=int(et.created_at.timestamp() * 1000) if et.created_at else 0,
                updated_at=int(et.updated_at.timestamp() * 1000) if et.updated_at else 0
            )
            for et in expense_tags
        ]
        
        # Query graph edges
        graph_edges = db.query(GraphEdge).filter(
            GraphEdge.updated_at > since_datetime
        ).all()
        
        graph_edge_responses = [
            ApiGraphEdge(
                id=str(edge.id),
                from_tag_id=str(edge.from_tag_id),
                to_tag_id=str(edge.to_tag_id),
                weight=edge.weight,
                created_at=int(edge.created_at.timestamp() * 1000) if edge.created_at else 0,
                updated_at=int(edge.updated_at.timestamp() * 1000) if edge.updated_at else 0
            )
            for edge in graph_edges
        ]
        
        return UpdatedDataResponse(
            expenses=expense_responses,
            tags=tag_responses,
            targets=target_responses,
            expense_tags=expense_tag_responses,
            graph_edges=graph_edge_responses
        )
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/batch/tags", response_model=BatchSyncResponse)
async def batch_sync_tags(
    request: BatchSyncTagsRequest,
    db: Session = Depends(get_db)
):
    """
    Batch sync tags with CREATE/UPDATE/DELETE operations
    """
    results: List[SyncResultType] = []
    
    try:
        for operation in request.operations:
            try:
                if operation.type == "create_tag":
                    new_tag = Tag(
                        name=operation.name,
                        monthly_amount=operation.monthly_amount,
                        current_month=operation.current_month,
                        current_year=operation.current_year,
                        created_day=operation.created_day,
                        created_month=operation.created_month,
                        created_year=operation.created_year,
                        created_at=datetime.utcnow(),
                        updated_at=datetime.utcnow()
                    )
                    db.add(new_tag)
                    db.flush()
                    
                    results.append(SyncResultType(
                        success=True,
                        client_id=operation.client_id,
                        server_id=str(new_tag.id)
                    ))
                    
                elif operation.type == "update_tag":
                    tag = db.query(Tag).filter(Tag.id == operation.server_id).first()
                    if tag:
                        tag.name = operation.name
                        tag.monthly_amount = operation.monthly_amount
                        tag.current_month = operation.current_month
                        tag.current_year = operation.current_year
                        tag.updated_at = datetime.utcnow()
                        
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
                            error="Tag not found"
                        ))
                        
                elif operation.type == "delete_tag":
                    tag = db.query(Tag).filter(Tag.id == operation.server_id).first()
                    if tag:
                        tag.deleted_at = datetime.utcnow()
                        
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
                            error="Tag not found"
                        ))
                        
            except Exception as e:
                results.append(SyncResultType(
                    success=False,
                    client_id=getattr(operation, 'client_id', None),
                    server_id=getattr(operation, 'server_id', None),
                    error=str(e)
                ))
        
        db.commit()
        return BatchSyncResponse(results=results)
        
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/batch/targets", response_model=BatchSyncResponse)
async def batch_sync_targets(
    request: BatchSyncTargetsRequest,
    db: Session = Depends(get_db)
):
    """
    Batch sync targets with CREATE/UPDATE/DELETE operations
    """
    results: List[SyncResultType] = []
    
    try:
        for operation in request.operations:
            try:
                if operation.type == "create_target":
                    new_target = Target(
                        month=operation.month,
                        year=operation.year,
                        tag_id=operation.tag_id,
                        amount=operation.amount,
                        spent=operation.spent,
                        created_at=datetime.utcnow(),
                        updated_at=datetime.utcnow()
                    )
                    db.add(new_target)
                    db.flush()
                    
                    results.append(SyncResultType(
                        success=True,
                        client_id=operation.client_id,
                        server_id=str(new_target.id)
                    ))
                    
                elif operation.type == "update_target":
                    target = db.query(Target).filter(Target.id == operation.server_id).first()
                    if target:
                        target.amount = operation.amount
                        target.spent = operation.spent
                        target.updated_at = datetime.utcnow()
                        
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
                            error="Target not found"
                        ))
                        
                elif operation.type == "delete_target":
                    target = db.query(Target).filter(Target.id == operation.server_id).first()
                    if target:
                        db.delete(target)
                        
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
                            error="Target not found"
                        ))
                        
            except Exception as e:
                results.append(SyncResultType(
                    success=False,
                    client_id=getattr(operation, 'client_id', None),
                    server_id=getattr(operation, 'server_id', None),
                    error=str(e)
                ))
        
        db.commit()
        return BatchSyncResponse(results=results)
        
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/batch/expense-tags", response_model=BatchSyncResponse)
async def batch_sync_expense_tags(
    request: BatchSyncExpenseTagsRequest,
    db: Session = Depends(get_db)
):
    """
    Batch sync expense-tag relationships with CREATE/DELETE operations
    """
    results: List[SyncResultType] = []
    
    try:
        for operation in request.operations:
            try:
                if operation.type == "create_expense_tag":
                    new_expense_tag = ExpenseTagsCrossRef(
                        expense_id=operation.expense_id,
                        tag_id=operation.tag_id,
                        created_at=datetime.utcnow(),
                        updated_at=datetime.utcnow()
                    )
                    db.add(new_expense_tag)
                    db.flush()
                    
                    results.append(SyncResultType(
                        success=True,
                        client_id=operation.client_id,
                        server_id=str(new_expense_tag.id)
                    ))
                    
                elif operation.type == "delete_expense_tag":
                    expense_tag = db.query(ExpenseTagsCrossRef).filter(
                        ExpenseTagsCrossRef.id == operation.server_id
                    ).first()
                    if expense_tag:
                        db.delete(expense_tag)
                        
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
                            error="ExpenseTag not found"
                        ))
                        
            except Exception as e:
                results.append(SyncResultType(
                    success=False,
                    client_id=getattr(operation, 'client_id', None),
                    server_id=getattr(operation, 'server_id', None),
                    error=str(e)
                ))
        
        db.commit()
        return BatchSyncResponse(results=results)
        
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/batch/graph-edges", response_model=BatchSyncResponse)
async def batch_sync_graph_edges(
    request: BatchSyncGraphEdgesRequest,
    db: Session = Depends(get_db)
):
    """
    Batch sync graph edges with CREATE/UPDATE/DELETE operations
    """
    results: List[SyncResultType] = []
    
    try:
        for operation in request.operations:
            try:
                if operation.type == "create_graph_edge":
                    new_edge = GraphEdge(
                        from_tag_id=operation.from_tag_id,
                        to_tag_id=operation.to_tag_id,
                        weight=operation.weight,
                        created_at=datetime.utcnow(),
                        updated_at=datetime.utcnow()
                    )
                    db.add(new_edge)
                    db.flush()
                    
                    results.append(SyncResultType(
                        success=True,
                        client_id=operation.client_id,
                        server_id=str(new_edge.id)
                    ))
                    
                elif operation.type == "update_graph_edge":
                    edge = db.query(GraphEdge).filter(GraphEdge.id == operation.server_id).first()
                    if edge:
                        edge.weight = operation.weight
                        edge.updated_at = datetime.utcnow()
                        
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
                            error="GraphEdge not found"
                        ))
                        
                elif operation.type == "delete_graph_edge":
                    edge = db.query(GraphEdge).filter(GraphEdge.id == operation.server_id).first()
                    if edge:
                        db.delete(edge)
                        
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
                            error="GraphEdge not found"
                        ))
                        
            except Exception as e:
                results.append(SyncResultType(
                    success=False,
                    client_id=getattr(operation, 'client_id', None),
                    server_id=getattr(operation, 'server_id', None),
                    error=str(e)
                ))
        
        db.commit()
        return BatchSyncResponse(results=results)
        
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))
