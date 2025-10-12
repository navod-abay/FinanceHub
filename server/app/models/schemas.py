from pydantic import BaseModel, Field
from typing import List, Optional, Dict, Any
from datetime import datetime


# Base models for common fields
class TimestampMixin(BaseModel):
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None


# Expense models
class ExpenseBase(BaseModel):
    title: str = Field(..., min_length=1, max_length=255)
    amount: int = Field(..., gt=0)
    year: int = Field(..., ge=2000, le=3000)
    month: int = Field(..., ge=1, le=12)
    date: int = Field(..., ge=1, le=31)


class ExpenseCreate(ExpenseBase):
    local_id: Optional[int] = None  # For tracking local ID during sync


class ExpenseResponse(ExpenseBase, TimestampMixin):
    id: str
    local_id: Optional[int] = None
    
    class Config:
        from_attributes = True


# Tag models
class TagBase(BaseModel):
    tag: str = Field(..., min_length=1, max_length=100)
    monthly_amount: int = Field(default=0, ge=0)
    current_month: int = Field(default=0, ge=0, le=12)
    current_year: int = Field(default=0, ge=0)
    created_day: int = Field(default=0, ge=0, le=31)
    created_month: int = Field(default=0, ge=0, le=12)
    created_year: int = Field(default=0, ge=0)


class TagCreate(TagBase):
    local_id: Optional[int] = None


class TagResponse(TagBase, TimestampMixin):
    id: str
    local_id: Optional[int] = None
    
    class Config:
        from_attributes = True


# Target models
class TargetBase(BaseModel):
    month: int = Field(..., ge=1, le=12)
    year: int = Field(..., ge=2000, le=3000)
    amount: int = Field(..., gt=0)
    spent: int = Field(default=0, ge=0)


class TargetCreate(TargetBase):
    tag_id: str


class TargetResponse(TargetBase, TimestampMixin):
    tag_id: str
    
    class Config:
        from_attributes = True


# Operation request models
class AddExpenseRequest(BaseModel):
    expense: ExpenseCreate
    existing_tags: List[str] = Field(default_factory=list)  # List of tag IDs
    new_tags: List[str] = Field(default_factory=list)      # List of tag names
    device_timestamp: int  # Unix timestamp from device


class UpdateExpenseRequest(BaseModel):
    expense: ExpenseCreate
    added_existing_tags: List[str] = Field(default_factory=list)
    removed_tags: List[str] = Field(default_factory=list)
    added_new_tags: List[str] = Field(default_factory=list)
    device_timestamp: int


class DeleteExpenseRequest(BaseModel):
    local_id: Optional[int] = None
    server_id: Optional[str] = None
    device_timestamp: int


class AddTargetRequest(BaseModel):
    target: TargetCreate
    device_timestamp: int


# Response models
class OperationResponse(BaseModel):
    success: bool
    message: str = ""
    server_timestamp: int
    affected_entities: Optional[Dict[str, List[str]]] = None


class AddExpenseResponse(OperationResponse):
    expense_id: Optional[str] = None
    created_tags: Optional[List[TagResponse]] = None


# Sync models
class SyncDeltaResponse(BaseModel):
    expenses: List[ExpenseResponse] = Field(default_factory=list)
    tags: List[TagResponse] = Field(default_factory=list)
    targets: List[TargetResponse] = Field(default_factory=list)
    graph_edges: List[Dict[str, Any]] = Field(default_factory=list)
    last_sync_timestamp: int


class SyncPushRequest(BaseModel):
    expenses: List[Dict[str, Any]] = Field(default_factory=list)
    tags: List[Dict[str, Any]] = Field(default_factory=list)
    targets: List[Dict[str, Any]] = Field(default_factory=list)
    device_timestamp: int


class SyncPushResponse(BaseModel):
    success: bool
    processed_count: int
    failed_items: List[Dict[str, Any]] = Field(default_factory=list)
    server_timestamp: int


# Query models
class ExpenseQueryParams(BaseModel):
    limit: int = Field(default=50, ge=1, le=100)
    offset: int = Field(default=0, ge=0)
    since: Optional[int] = None  # Unix timestamp
    tag_ids: Optional[List[str]] = None


class RecommendationResponse(BaseModel):
    tag_id: str
    tag_name: str
    score: float
    
    
class RecommendationRequest(BaseModel):
    tag_id: str