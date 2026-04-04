from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from typing import List
import time
from datetime import datetime

from ..database import get_db
from ..models import Expense, Tag, Target, ExpenseTagsCrossRef, GraphEdge, WishlistItem, WishlistTagsCrossRef
from ..schemas import (
    BatchSyncExpensesRequest, BatchSyncTagsRequest, BatchSyncTargetsRequest,
    BatchSyncExpenseTagsRequest, BatchSyncGraphEdgesRequest, BatchSyncWishlistRequest,
    BatchSyncResponse, SyncResultType,
    CreateExpenseBatchRequest, UpdateExpenseBatchRequest, DeleteExpenseBatchRequest,
    ApiExpense, ApiTag, ApiTarget, ApiExpenseTag, ApiGraphEdge, ApiWishlistItem,
    UpdatedDataResponse, BatchSyncWishlistTagsRequest, ApiWishlistTag
)

router = APIRouter()



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
                name=tag.tag,
                monthlyAmount=tag.monthly_amount,
                currentMonth=tag.current_month,
                currentYear=tag.current_year,
                createdDay=tag.created_day,
                createdMonth=tag.created_month,
                createdYear=tag.created_year,
                createdAt=int(tag.created_at.timestamp() * 1000) if tag.created_at else 0,
                updatedAt=int(tag.updated_at.timestamp() * 1000) if tag.updated_at else 0
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
                tagId=str(target.tag_id),
                amount=target.amount,
                spent=target.spent,
                createdAt=int(target.created_at.timestamp() * 1000) if target.created_at else 0,
                updatedAt=int(target.updated_at.timestamp() * 1000) if target.updated_at else 0
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
                expenseId=str(et.expense_id),
                tagId=str(et.tag_id),
                createdAt=int(et.created_at.timestamp() * 1000) if et.created_at else 0,
                updatedAt=int(et.updated_at.timestamp() * 1000) if et.updated_at else 0
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
                fromTagId=str(edge.from_tag_id),
                toTagId=str(edge.to_tag_id),
                weight=edge.weight,
                createdAt=int(edge.created_at.timestamp() * 1000) if edge.created_at else 0,
                updatedAt=int(edge.updated_at.timestamp() * 1000) if edge.updated_at else 0
            )
            for edge in graph_edges
        ]
        
        # Query wishlist
        wishlist_items = db.query(WishlistItem).filter(
            WishlistItem.updated_at > since_datetime
        ).all()
        
        wishlist_responses = [
            ApiWishlistItem(
                id=str(item.id),
                name=item.name,
                minPrice=item.min_price,
                maxPrice=item.max_price,
                # tagId removed
                createdAt=int(item.created_at.timestamp() * 1000) if item.created_at else 0,
                updatedAt=int(item.updated_at.timestamp() * 1000) if item.updated_at else 0
            )
            for item in wishlist_items
        ]

        # Query wishlist tags
        wishlist_tags = db.query(WishlistTagsCrossRef).filter(
            WishlistTagsCrossRef.updated_at > since_datetime
        ).all()

        wishlist_tag_responses = [
            ApiWishlistTag(
                id=str(wt.id),
                wishlistId=str(wt.wishlist_id),
                tagId=str(wt.tag_id),
                createdAt=int(wt.created_at.timestamp() * 1000) if wt.created_at else 0,
                updatedAt=int(wt.updated_at.timestamp() * 1000) if wt.updated_at else 0
            )
            for wt in wishlist_tags
        ]
        
        return UpdatedDataResponse(
            expenses=expense_responses,
            tags=tag_responses,
            targets=target_responses,
            expense_tags=expense_tag_responses,
            graph_edges=graph_edge_responses,
            wishlist=wishlist_responses,
            wishlist_tags=wishlist_tag_responses
        )
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


