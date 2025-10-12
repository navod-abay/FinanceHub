from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from sqlalchemy import and_
import time
from typing import List

from ..database import get_db
from ..models import Expense, Tag, ExpenseTagsCrossRef, Target, GraphEdge
from ..models.schemas import (
    AddExpenseRequest, AddExpenseResponse, UpdateExpenseRequest, DeleteExpenseRequest,
    AddTargetRequest, OperationResponse, TagResponse
)
from ..services.expense_service import ExpenseService
from ..services.graph_service import GraphService

router = APIRouter()


@router.post("/add-expense", response_model=AddExpenseResponse)
async def add_expense(
    request: AddExpenseRequest,
    db: Session = Depends(get_db)
):
    """
    Add a new expense with tags - handles all related operations atomically
    """
    try:
        expense_service = ExpenseService(db)
        result = await expense_service.add_expense_with_tags(
            expense_data=request.expense,
            existing_tag_ids=request.existing_tags,
            new_tag_names=request.new_tags,
            device_timestamp=request.device_timestamp
        )
        
        return AddExpenseResponse(
            success=True,
            message="Expense added successfully",
            server_timestamp=int(time.time()),
            expense_id=result["expense_id"],
            created_tags=result.get("created_tags", []),
            affected_entities=result.get("affected_entities", {})
        )
    
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.put("/update-expense/{expense_id}", response_model=OperationResponse)
async def update_expense(
    expense_id: str,
    request: UpdateExpenseRequest,
    db: Session = Depends(get_db)
):
    """
    Update an expense and its tag associations
    """
    try:
        expense_service = ExpenseService(db)
        result = await expense_service.update_expense_with_tags(
            expense_id=expense_id,
            expense_data=request.expense,
            added_existing_tags=request.added_existing_tags,
            removed_tags=request.removed_tags,
            added_new_tags=request.added_new_tags,
            device_timestamp=request.device_timestamp
        )
        
        return OperationResponse(
            success=True,
            message="Expense updated successfully",
            server_timestamp=int(time.time()),
            affected_entities=result.get("affected_entities", {})
        )
    
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.delete("/delete-expense/{expense_id}", response_model=OperationResponse)
async def delete_expense(
    expense_id: str,
    request: DeleteExpenseRequest,
    db: Session = Depends(get_db)
):
    """
    Delete an expense and clean up all related data
    """
    try:
        expense_service = ExpenseService(db)
        result = await expense_service.delete_expense(
            expense_id=expense_id,
            device_timestamp=request.device_timestamp
        )
        
        return OperationResponse(
            success=True,
            message="Expense deleted successfully",
            server_timestamp=int(time.time()),
            affected_entities=result.get("affected_entities", {})
        )
    
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/add-target", response_model=OperationResponse)
async def add_target(
    request: AddTargetRequest,
    db: Session = Depends(get_db)
):
    """
    Add a new spending target for a tag
    """
    try:
        # Check if target already exists
        existing_target = db.query(Target).filter(
            and_(
                Target.month == request.target.month,
                Target.year == request.target.year,
                Target.tag_id == request.target.tag_id
            )
        ).first()
        
        if existing_target:
            raise HTTPException(status_code=400, detail="Target already exists for this tag and month")
        
        # Calculate current spent amount for this tag/month/year
        current_spent = db.query(
            db.func.sum(Expense.amount)
        ).join(
            ExpenseTagsCrossRef, Expense.id == ExpenseTagsCrossRef.expense_id
        ).filter(
            and_(
                ExpenseTagsCrossRef.tag_id == request.target.tag_id,
                Expense.month == request.target.month,
                Expense.year == request.target.year,
                Expense.deleted_at.is_(None)
            )
        ).scalar() or 0
        
        # Create new target
        new_target = Target(
            month=request.target.month,
            year=request.target.year,
            tag_id=request.target.tag_id,
            amount=request.target.amount,
            spent=current_spent
        )
        
        db.add(new_target)
        db.commit()
        
        return OperationResponse(
            success=True,
            message="Target added successfully",
            server_timestamp=int(time.time()),
            affected_entities={"targets": [f"{request.target.month}-{request.target.year}-{request.target.tag_id}"]}
        )
    
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=400, detail=str(e))