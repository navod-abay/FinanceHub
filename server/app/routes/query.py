from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from sqlalchemy import and_, desc
from typing import List, Optional

from ..database import get_db
from ..models import Expense, Tag, Target
from ..models.schemas import ExpenseResponse, TagResponse, TargetResponse, RecommendationRequest, RecommendationResponse, ExpenseQueryParams
from ..services.graph_service import GraphService

router = APIRouter()


@router.get("/expenses", response_model=List[ExpenseResponse])
async def get_expenses(
    limit: int = Query(50, ge=1, le=100),
    offset: int = Query(0, ge=0),
    since: Optional[int] = Query(None, description="Unix timestamp"),
    tag_ids: Optional[str] = Query(None, description="Comma-separated tag IDs"),
    db: Session = Depends(get_db)
):
    """
    Get expenses with pagination and filtering
    """
    try:
        query = db.query(Expense).filter(Expense.deleted_at.is_(None))
        
        # Filter by timestamp if provided
        if since:
            from datetime import datetime
            since_datetime = datetime.fromtimestamp(since)
            query = query.filter(Expense.created_at >= since_datetime)
        
        # Filter by tag IDs if provided
        if tag_ids:
            tag_id_list = tag_ids.split(',')
            from ..models import ExpenseTagsCrossRef
            query = query.join(ExpenseTagsCrossRef).filter(
                ExpenseTagsCrossRef.tag_id.in_(tag_id_list)
            )
        
        # Apply pagination and ordering
        expenses = query.order_by(desc(Expense.created_at)).offset(offset).limit(limit).all()
        
        return [ExpenseResponse.from_orm(exp) for exp in expenses]
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/tags", response_model=List[TagResponse])
async def get_tags(
    limit: int = Query(100, ge=1, le=200),
    search: Optional[str] = Query(None, description="Search tag names"),
    db: Session = Depends(get_db)
):
    """
    Get tags with optional search
    """
    try:
        query = db.query(Tag).filter(Tag.deleted_at.is_(None))
        
        # Filter by search term if provided
        if search:
            query = query.filter(Tag.tag.ilike(f"%{search}%"))
        
        tags = query.order_by(Tag.tag).limit(limit).all()
        
        return [TagResponse.from_orm(tag) for tag in tags]
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/targets", response_model=List[TargetResponse])
async def get_targets(
    month: Optional[int] = Query(None, ge=1, le=12),
    year: Optional[int] = Query(None, ge=2000, le=3000),
    tag_id: Optional[str] = Query(None),
    db: Session = Depends(get_db)
):
    """
    Get targets with optional filtering
    """
    try:
        query = db.query(Target).filter(Target.deleted_at.is_(None))
        
        if month:
            query = query.filter(Target.month == month)
        if year:
            query = query.filter(Target.year == year)
        if tag_id:
            query = query.filter(Target.tag_id == tag_id)
        
        targets = query.order_by(Target.year.desc(), Target.month.desc()).all()
        
        return [TargetResponse.from_orm(target) for target in targets]
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/recommendations", response_model=List[RecommendationResponse])
async def get_tag_recommendations(
    request: RecommendationRequest,
    db: Session = Depends(get_db)
):
    """
    Get tag recommendations based on the graph algorithm
    """
    try:
        graph_service = GraphService(db)
        recommendations = await graph_service.get_tag_recommendations(request.tag_id)
        
        return [
            RecommendationResponse(
                tag_id=rec["tag_id"],
                tag_name=rec["tag_name"],
                score=rec["score"]
            )
            for rec in recommendations
        ]
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/expenses/{expense_id}", response_model=ExpenseResponse)
async def get_expense(expense_id: str, db: Session = Depends(get_db)):
    """
    Get a specific expense by ID
    """
    try:
        expense = db.query(Expense).filter(
            and_(Expense.id == expense_id, Expense.deleted_at.is_(None))
        ).first()
        
        if not expense:
            raise HTTPException(status_code=404, detail="Expense not found")
        
        return ExpenseResponse.from_orm(expense)
        
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/tags/{tag_id}", response_model=TagResponse)
async def get_tag(tag_id: str, db: Session = Depends(get_db)):
    """
    Get a specific tag by ID
    """
    try:
        tag = db.query(Tag).filter(
            and_(Tag.id == tag_id, Tag.deleted_at.is_(None))
        ).first()
        
        if not tag:
            raise HTTPException(status_code=404, detail="Tag not found")
        
        return TagResponse.from_orm(tag)
        
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/stats/summary")
async def get_summary_stats(db: Session = Depends(get_db)):
    """
    Get summary statistics for the dashboard
    """
    try:
        from datetime import datetime, timedelta
        from sqlalchemy import func, extract
        
        # Current month stats
        now = datetime.now()
        current_month = now.month
        current_year = now.year
        
        # Total expenses this month
        current_month_total = db.query(func.sum(Expense.amount)).filter(
            and_(
                Expense.month == current_month,
                Expense.year == current_year,
                Expense.deleted_at.is_(None)
            )
        ).scalar() or 0
        
        # Total expenses last month
        last_month = current_month - 1 if current_month > 1 else 12
        last_month_year = current_year if current_month > 1 else current_year - 1
        
        last_month_total = db.query(func.sum(Expense.amount)).filter(
            and_(
                Expense.month == last_month,
                Expense.year == last_month_year,
                Expense.deleted_at.is_(None)
            )
        ).scalar() or 0
        
        # Total number of expenses
        total_expenses = db.query(func.count(Expense.id)).filter(
            Expense.deleted_at.is_(None)
        ).scalar() or 0
        
        # Total number of tags
        total_tags = db.query(func.count(Tag.id)).filter(
            Tag.deleted_at.is_(None)
        ).scalar() or 0
        
        # Active targets this month
        active_targets = db.query(func.count(Target.month)).filter(
            and_(
                Target.month == current_month,
                Target.year == current_year,
                Target.deleted_at.is_(None)
            )
        ).scalar() or 0
        
        return {
            "current_month_total": current_month_total,
            "last_month_total": last_month_total,
            "month_over_month_change": current_month_total - last_month_total,
            "total_expenses": total_expenses,
            "total_tags": total_tags,
            "active_targets": active_targets,
            "timestamp": int(datetime.now().timestamp())
        }
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))