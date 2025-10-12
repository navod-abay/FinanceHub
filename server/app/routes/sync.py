from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from sqlalchemy import and_, or_, func
import time
from typing import Optional

from ..database import get_db
from ..models import Expense, Tag, Target, GraphEdge
from ..models.schemas import SyncDeltaResponse, SyncPushRequest, SyncPushResponse, ExpenseResponse, TagResponse, TargetResponse

router = APIRouter()


@router.get("/delta", response_model=SyncDeltaResponse)
async def get_sync_delta(
    since: Optional[int] = Query(None, description="Unix timestamp of last sync"),
    db: Session = Depends(get_db)
):
    """
    Get all changes since the last sync timestamp
    """
    try:
        # Convert since timestamp to datetime if provided
        since_datetime = None
        if since:
            from datetime import datetime
            since_datetime = datetime.fromtimestamp(since)
        
        # Query for expenses
        expense_query = db.query(Expense).filter(Expense.deleted_at.is_(None))
        if since_datetime:
            expense_query = expense_query.filter(
                or_(
                    Expense.created_at > since_datetime,
                    Expense.updated_at > since_datetime
                )
            )
        
        expenses = expense_query.all()
        expense_responses = [ExpenseResponse.from_orm(exp) for exp in expenses]
        
        # Query for tags
        tag_query = db.query(Tag).filter(Tag.deleted_at.is_(None))
        if since_datetime:
            tag_query = tag_query.filter(
                or_(
                    Tag.created_at > since_datetime,
                    Tag.updated_at > since_datetime
                )
            )
        
        tags = tag_query.all()
        tag_responses = [TagResponse.from_orm(tag) for tag in tags]
        
        # Query for targets
        target_query = db.query(Target).filter(Target.deleted_at.is_(None))
        if since_datetime:
            target_query = target_query.filter(
                or_(
                    Target.created_at > since_datetime,
                    Target.updated_at > since_datetime
                )
            )
        
        targets = target_query.all()
        target_responses = [TargetResponse.from_orm(target) for target in targets]
        
        # Query for graph edges
        graph_edge_query = db.query(GraphEdge)
        if since_datetime:
            graph_edge_query = graph_edge_query.filter(
                or_(
                    GraphEdge.created_at > since_datetime,
                    GraphEdge.updated_at > since_datetime
                )
            )
        
        graph_edges = graph_edge_query.all()
        graph_edge_responses = [
            {
                "from_tag_id": edge.from_tag_id,
                "to_tag_id": edge.to_tag_id,
                "weight": edge.weight,
                "created_at": edge.created_at.isoformat() if edge.created_at else None,
                "updated_at": edge.updated_at.isoformat() if edge.updated_at else None
            }
            for edge in graph_edges
        ]
        
        return SyncDeltaResponse(
            expenses=expense_responses,
            tags=tag_responses,
            targets=target_responses,
            graph_edges=graph_edge_responses,
            last_sync_timestamp=int(time.time())
        )
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/push", response_model=SyncPushResponse)
async def push_sync_data(
    request: SyncPushRequest,
    db: Session = Depends(get_db)
):
    """
    Push local changes to server
    Handle conflict resolution with server-wins strategy
    """
    try:
        processed_count = 0
        failed_items = []
        
        # Process expenses
        for expense_data in request.expenses:
            try:
                operation = expense_data.get("operation", "CREATE")
                
                if operation == "CREATE":
                    # Create new expense on server
                    new_expense = Expense(
                        local_id=expense_data.get("local_id"),
                        title=expense_data["title"],
                        amount=expense_data["amount"],
                        year=expense_data["year"],
                        month=expense_data["month"],
                        date=expense_data["date"]
                    )
                    db.add(new_expense)
                    processed_count += 1
                    
                elif operation == "UPDATE":
                    # Update existing expense
                    expense_id = expense_data.get("server_id")
                    if expense_id:
                        expense = db.query(Expense).filter(Expense.id == expense_id).first()
                        if expense:
                            expense.title = expense_data["title"]
                            expense.amount = expense_data["amount"]
                            expense.year = expense_data["year"]
                            expense.month = expense_data["month"]
                            expense.date = expense_data["date"]
                            processed_count += 1
                        else:
                            failed_items.append({"item": expense_data, "error": "Expense not found"})
                    else:
                        failed_items.append({"item": expense_data, "error": "No server ID provided"})
                        
                elif operation == "DELETE":
                    # Soft delete expense
                    expense_id = expense_data.get("server_id")
                    if expense_id:
                        expense = db.query(Expense).filter(Expense.id == expense_id).first()
                        if expense:
                            expense.deleted_at = func.now()
                            processed_count += 1
                        else:
                            failed_items.append({"item": expense_data, "error": "Expense not found"})
                    else:
                        failed_items.append({"item": expense_data, "error": "No server ID provided"})
                        
            except Exception as e:
                failed_items.append({"item": expense_data, "error": str(e)})
        
        # Process tags
        for tag_data in request.tags:
            try:
                operation = tag_data.get("operation", "CREATE")
                
                if operation == "CREATE":
                    # Check if tag already exists by name
                    existing_tag = db.query(Tag).filter(Tag.tag == tag_data["tag"]).first()
                    if not existing_tag:
                        new_tag = Tag(
                            local_id=tag_data.get("local_id"),
                            tag=tag_data["tag"],
                            monthly_amount=tag_data.get("monthly_amount", 0),
                            current_month=tag_data.get("current_month", 0),
                            current_year=tag_data.get("current_year", 0),
                            created_day=tag_data.get("created_day", 0),
                            created_month=tag_data.get("created_month", 0),
                            created_year=tag_data.get("created_year", 0)
                        )
                        db.add(new_tag)
                        processed_count += 1
                    else:
                        # Tag exists, update it instead
                        existing_tag.monthly_amount = tag_data.get("monthly_amount", existing_tag.monthly_amount)
                        existing_tag.current_month = tag_data.get("current_month", existing_tag.current_month)
                        existing_tag.current_year = tag_data.get("current_year", existing_tag.current_year)
                        processed_count += 1
                        
                elif operation == "UPDATE":
                    tag_id = tag_data.get("server_id")
                    if tag_id:
                        tag = db.query(Tag).filter(Tag.id == tag_id).first()
                        if tag:
                            tag.monthly_amount = tag_data.get("monthly_amount", tag.monthly_amount)
                            tag.current_month = tag_data.get("current_month", tag.current_month)
                            tag.current_year = tag_data.get("current_year", tag.current_year)
                            processed_count += 1
                        else:
                            failed_items.append({"item": tag_data, "error": "Tag not found"})
                    else:
                        failed_items.append({"item": tag_data, "error": "No server ID provided"})
                        
            except Exception as e:
                failed_items.append({"item": tag_data, "error": str(e)})
        
        # Process targets
        for target_data in request.targets:
            try:
                operation = target_data.get("operation", "CREATE")
                
                if operation == "CREATE":
                    new_target = Target(
                        month=target_data["month"],
                        year=target_data["year"],
                        tag_id=target_data["tag_id"],
                        amount=target_data["amount"],
                        spent=target_data.get("spent", 0)
                    )
                    db.add(new_target)
                    processed_count += 1
                    
                elif operation == "UPDATE":
                    target = db.query(Target).filter(
                        and_(
                            Target.month == target_data["month"],
                            Target.year == target_data["year"],
                            Target.tag_id == target_data["tag_id"]
                        )
                    ).first()
                    
                    if target:
                        target.amount = target_data["amount"]
                        target.spent = target_data.get("spent", target.spent)
                        processed_count += 1
                    else:
                        failed_items.append({"item": target_data, "error": "Target not found"})
                        
                elif operation == "DELETE":
                    target = db.query(Target).filter(
                        and_(
                            Target.month == target_data["month"],
                            Target.year == target_data["year"],
                            Target.tag_id == target_data["tag_id"]
                        )
                    ).first()
                    
                    if target:
                        target.deleted_at = func.now()
                        processed_count += 1
                    else:
                        failed_items.append({"item": target_data, "error": "Target not found"})
                        
            except Exception as e:
                failed_items.append({"item": target_data, "error": str(e)})
        
        # Commit all changes
        db.commit()
        
        return SyncPushResponse(
            success=len(failed_items) == 0,
            processed_count=processed_count,
            failed_items=failed_items,
            server_timestamp=int(time.time())
        )
        
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/full", response_model=SyncDeltaResponse)
async def full_sync(db: Session = Depends(get_db)):
    """
    Get all data for initial sync or recovery
    """
    try:
        # Get all non-deleted data
        expenses = db.query(Expense).filter(Expense.deleted_at.is_(None)).all()
        tags = db.query(Tag).filter(Tag.deleted_at.is_(None)).all()
        targets = db.query(Target).filter(Target.deleted_at.is_(None)).all()
        graph_edges = db.query(GraphEdge).all()
        
        expense_responses = [ExpenseResponse.from_orm(exp) for exp in expenses]
        tag_responses = [TagResponse.from_orm(tag) for tag in tags]
        target_responses = [TargetResponse.from_orm(target) for target in targets]
        
        graph_edge_responses = [
            {
                "from_tag_id": edge.from_tag_id,
                "to_tag_id": edge.to_tag_id,
                "weight": edge.weight,
                "created_at": edge.created_at.isoformat() if edge.created_at else None,
                "updated_at": edge.updated_at.isoformat() if edge.updated_at else None
            }
            for edge in graph_edges
        ]
        
        return SyncDeltaResponse(
            expenses=expense_responses,
            tags=tag_responses,
            targets=target_responses,
            graph_edges=graph_edge_responses,
            last_sync_timestamp=int(time.time())
        )
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))