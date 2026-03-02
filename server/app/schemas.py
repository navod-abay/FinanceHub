from pydantic import BaseModel, Field, validator
from typing import List, Optional, Union, Literal, Annotated
from pydantic import Discriminator

# Base models for database entities
class ExpenseBase(BaseModel):
    title: str
    amount: int
    year: int
    month: int
    date: int

class TagBase(BaseModel):
    name: str = Field(..., alias='tag')
    monthly_amount: int = 0
    current_month: int = 0
    current_year: int = 0
    created_day: int = 0
    created_month: int = 0
    created_year: int = 0

class TargetBase(BaseModel):
    month: int
    year: int
    tag_id: str
    amount: int
    spent: int = 0

class ExpenseTagBase(BaseModel):
    expense_id: str
    tag_id: str

class GraphEdgeBase(BaseModel):
    from_tag_id: str
    to_tag_id: str
    weight: int

# Create models with local_id for requests
class ExpenseCreateRequest(ExpenseBase):
    local_id: Optional[int] = None

class TargetCreateRequest(BaseModel):
    month: int
    year: int
    tag_id: str
    amount: int
    spent: int = 0

# Request models for operations
class AddExpenseRequest(BaseModel):
    expense: ExpenseCreateRequest
    existing_tags: List[str] = []
    new_tags: List[str] = []
    device_timestamp: int

class UpdateExpenseRequest(BaseModel):
    expense: ExpenseCreateRequest
    added_existing_tags: List[str] = []
    removed_tags: List[str] = []
    added_new_tags: List[str] = []
    device_timestamp: int

class DeleteExpenseRequest(BaseModel):
    local_id: Optional[int] = None
    server_id: Optional[str] = None
    device_timestamp: int

class AddTargetRequest(BaseModel):
    target: TargetCreateRequest
    device_timestamp: int

# Response models
class OperationResponse(BaseModel):
    success: bool
    message: str = ""
    server_timestamp: int
    affected_entities: Optional[dict[str, List[str]]] = None

class TagResponse(BaseModel):
    id: str
    local_id: Optional[int] = None
    tag: str
    monthly_amount: int = 0
    current_month: int = 0
    current_year: int = 0
    created_day: int = 0
    created_month: int = 0
    created_year: int = 0
    created_at: Optional[str] = None
    updated_at: Optional[str] = None

class AddExpenseResponse(BaseModel):
    success: bool
    message: str = ""
    server_timestamp: int
    expense_id: Optional[str] = None
    created_tags: List[TagResponse] = []
    affected_entities: Optional[dict[str, List[str]]] = None

# Batch Sync Models
# --- Expense ---
class CreateExpenseBatchRequest(BaseModel):
    type: Literal["create_expense"]
    title: str
    amount: int
    year: int
    month: int
    date: int
    client_id: str = Field(..., alias="clientId")
    
    class Config:
        populate_by_name = True

class UpdateExpenseBatchRequest(BaseModel):
    type: Literal["update_expense"]
    server_id: str = Field(..., alias="serverId")
    title: str
    amount: int
    year: int
    month: int
    date: int
    
    class Config:
        populate_by_name = True

class DeleteExpenseBatchRequest(BaseModel):
    type: Literal["delete_expense"]
    server_id: str = Field(..., alias="serverId")
    
    class Config:
        populate_by_name = True

ExpenseOperation = Annotated[
    Union[CreateExpenseBatchRequest, UpdateExpenseBatchRequest, DeleteExpenseBatchRequest],
    Field(discriminator='type')
]

class BatchSyncExpensesRequest(BaseModel):
    operations: List[ExpenseOperation]
    
    class Config:
        populate_by_name = True

# --- Tag ---
class CreateTagBatchRequest(BaseModel):
    type: Literal["create_tag"]
    name: str
    monthly_amount: int = Field(..., alias="monthlyAmount")
    current_month: int = Field(..., alias="currentMonth")
    current_year: int = Field(..., alias="currentYear")
    created_day: int = Field(..., alias="createdDay")
    created_month: int = Field(..., alias="createdMonth")
    created_year: int = Field(..., alias="createdYear")
    client_id: str = Field(..., alias="clientId")
    
    class Config:
        populate_by_name = True

class UpdateTagBatchRequest(BaseModel):
    type: Literal["update_tag"]
    server_id: str = Field(..., alias="serverId")
    name: str
    monthly_amount: int = Field(..., alias="monthlyAmount")
    current_month: int = Field(..., alias="currentMonth")
    current_year: int = Field(..., alias="currentYear")
    
    class Config:
        populate_by_name = True

class DeleteTagBatchRequest(BaseModel):
    type: Literal["delete_tag"]
    server_id: str = Field(..., alias="serverId")
    
    class Config:
        populate_by_name = True

TagOperation = Annotated[
    Union[CreateTagBatchRequest, UpdateTagBatchRequest, DeleteTagBatchRequest],
    Field(discriminator='type')
]

class BatchSyncTagsRequest(BaseModel):
    operations: List[TagOperation]
    
    class Config:
        populate_by_name = True

# --- Target ---
class CreateTargetBatchRequest(BaseModel):
    type: Literal["create_target"]
    month: int
    year: int
    tag_id: str = Field(..., alias="tagId")
    amount: int
    spent: int
    client_id: str = Field(..., alias="clientId")
    
    class Config:
        populate_by_name = True

class UpdateTargetBatchRequest(BaseModel):
    type: Literal["update_target"]
    server_id: str = Field(..., alias="serverId")
    amount: int
    spent: int
    
    class Config:
        populate_by_name = True

class DeleteTargetBatchRequest(BaseModel):
    type: Literal["delete_target"]
    server_id: str = Field(..., alias="serverId")
    
    class Config:
        populate_by_name = True

TargetOperation = Annotated[
    Union[CreateTargetBatchRequest, UpdateTargetBatchRequest, DeleteTargetBatchRequest],
    Field(discriminator='type')
]

class BatchSyncTargetsRequest(BaseModel):
    operations: List[TargetOperation]
    
    class Config:
        populate_by_name = True

# --- ExpenseTag ---
class CreateExpenseTagBatchRequest(BaseModel):
    type: Literal["create_expense_tag"]
    expense_id: str = Field(..., alias="expenseId")
    tag_id: str = Field(..., alias="tagId")
    client_id: str = Field(..., alias="clientId")
    
    class Config:
        populate_by_name = True

class DeleteExpenseTagBatchRequest(BaseModel):
    type: Literal["delete_expense_tag"]
    server_id: str = Field(..., alias="serverId")
    
    class Config:
        populate_by_name = True

ExpenseTagOperation = Annotated[
    Union[CreateExpenseTagBatchRequest, DeleteExpenseTagBatchRequest],
    Field(discriminator='type')
]

class BatchSyncExpenseTagsRequest(BaseModel):
    operations: List[ExpenseTagOperation]
    
    class Config:
        populate_by_name = True

# --- GraphEdge ---
class CreateGraphEdgeBatchRequest(BaseModel):
    type: Literal["create_graph_edge"]
    from_tag_id: str = Field(..., alias="fromTagId")
    to_tag_id: str = Field(..., alias="toTagId")
    weight: int
    client_id: str = Field(..., alias="clientId")
    
    class Config:
        populate_by_name = True

class UpdateGraphEdgeBatchRequest(BaseModel):
    type: Literal["update_graph_edge"]
    server_id: str = Field(..., alias="serverId")
    weight: int
    
    class Config:
        populate_by_name = True

class DeleteGraphEdgeBatchRequest(BaseModel):
    type: Literal["delete_graph_edge"]
    server_id: str = Field(..., alias="serverId")
    
    class Config:
        populate_by_name = True

GraphEdgeOperation = Annotated[
    Union[CreateGraphEdgeBatchRequest, UpdateGraphEdgeBatchRequest, DeleteGraphEdgeBatchRequest],
    Field(discriminator='type')
]

class BatchSyncGraphEdgesRequest(BaseModel):
    operations: List[GraphEdgeOperation]
    
    class Config:
        populate_by_name = True

# Batch sync result
class SyncResultType(BaseModel):
    success: bool
    client_id: Optional[str] = Field(None, alias="clientId")
    server_id: Optional[str] = Field(None, alias="serverId")
    error: Optional[str] = None
    
    class Config:
        populate_by_name = True

class BatchSyncResponse(BaseModel):
    results: List[SyncResultType]
    
    class Config:
        populate_by_name = True

# API entity models (what server returns)
class ApiExpense(BaseModel):
    id: str
    title: str
    amount: int
    year: int
    month: int
    date: int
    created_at: int = Field(..., alias="createdAt")
    updated_at: int = Field(..., alias="updatedAt")
    
    class Config:
        populate_by_name = True

class ApiTag(BaseModel):
    id: str
    name: str
    monthly_amount: int = Field(..., alias="monthlyAmount")
    current_month: int = Field(..., alias="currentMonth")
    current_year: int = Field(..., alias="currentYear")
    created_day: int = Field(..., alias="createdDay")
    created_month: int = Field(..., alias="createdMonth")
    created_year: int = Field(..., alias="createdYear")
    created_at: int = Field(..., alias="createdAt")
    updated_at: int = Field(..., alias="updatedAt")

class ApiTarget(BaseModel):
    id: str
    month: int
    year: int
    tag_id: str = Field(..., alias="tagId")
    amount: int
    spent: int
    created_at: int = Field(..., alias="createdAt")
    updated_at: int = Field(..., alias="updatedAt")

class ApiExpenseTag(BaseModel):
    id: str
    expense_id: str = Field(..., alias="expenseId")
    tag_id: str = Field(..., alias="tagId")
    created_at: int = Field(..., alias="createdAt")
    updated_at: int = Field(..., alias="updatedAt")

class ApiGraphEdge(BaseModel):
    id: str
    from_tag_id: str = Field(..., alias="fromTagId")
    to_tag_id: str = Field(..., alias="toTagId")
    weight: int
    created_at: int = Field(..., alias="createdAt")
    updated_at: int = Field(..., alias="updatedAt")

# Updated data response for delta sync
# Wishlist Models
# Wishlist Models
class WishlistItemBase(BaseModel):
    name: str
    min_price: int = Field(alias="minPrice")
    max_price: int = Field(alias="maxPrice")
    # tag_id removed

    class Config:
        populate_by_name = True

class WishlistItemCreate(WishlistItemBase):
    pass

class ApiWishlistItem(WishlistItemBase):
    id: str
    created_at: int = Field(alias="createdAt")
    updated_at: int = Field(alias="updatedAt")

    class Config:
        orm_mode = True
        populate_by_name = True
        
    @validator("created_at", "updated_at", pre=True)
    def convert_datetime(cls, v):
        from datetime import datetime
        if isinstance(v, datetime):
            return int(v.timestamp() * 1000)
        return v

# Wishlist Tag Models for Sync Response
class ApiWishlistTag(BaseModel):
    id: str
    wishlist_id: str = Field(alias="wishlistId")
    tag_id: str = Field(alias="tagId")
    created_at: int = Field(alias="createdAt")
    updated_at: int = Field(alias="updatedAt")

    class Config:
        orm_mode = True
        populate_by_name = True

    @validator("created_at", "updated_at", pre=True)
    def convert_datetime(cls, v):
        from datetime import datetime
        if isinstance(v, datetime):
            return int(v.timestamp() * 1000)
        return v

# Batch Sync Operations

class CreateWishlistBatchRequest(BaseModel):
    type: Literal["create_wishlist"]
    name: str
    min_price: int = Field(..., alias="minPrice")
    max_price: int = Field(..., alias="maxPrice")
    client_id: str = Field(..., alias="clientId")
    
    class Config:
        populate_by_name = True

class UpdateWishlistBatchRequest(BaseModel):
    type: Literal["update_wishlist"]
    server_id: str = Field(..., alias="serverId")
    name: str
    min_price: int = Field(..., alias="minPrice")
    max_price: int = Field(..., alias="maxPrice")
    
    class Config:
        populate_by_name = True

class DeleteWishlistBatchRequest(BaseModel):
    type: Literal["delete_wishlist"]
    server_id: str = Field(..., alias="serverId")
    
    class Config:
        populate_by_name = True

WishlistOperation = Annotated[
    Union[CreateWishlistBatchRequest, UpdateWishlistBatchRequest, DeleteWishlistBatchRequest],
    Field(discriminator='type')
]

class BatchSyncWishlistRequest(BaseModel):
    operations: List[WishlistOperation]
    
    class Config:
        populate_by_name = True

# Wishlist Tags Batch Operations

class CreateWishlistTagBatchRequest(BaseModel):
    type: Literal["create_wishlist_tag"]
    wishlist_id: str = Field(..., alias="wishlistId")
    tag_id: str = Field(..., alias="tagId")
    client_id: str = Field(..., alias="clientId")

    class Config:
        populate_by_name = True

class DeleteWishlistTagBatchRequest(BaseModel):
    type: Literal["delete_wishlist_tag"]
    server_id: Optional[str] = Field(None, alias="serverId")
    # Also support delete by composite key if needed, or enforce serverId?
    # Usually easier to delete by ID if tracked, or composite.
    # We'll support both in logic, but here schema just needs what we allow.
    wishlist_id: Optional[str] = Field(None, alias="wishlistId")
    tag_id: Optional[str] = Field(None, alias="tagId")

    class Config:
        populate_by_name = True

WishlistTagOperation = Annotated[
    Union[CreateWishlistTagBatchRequest, DeleteWishlistTagBatchRequest],
    Field(discriminator='type')
]

class BatchSyncWishlistTagsRequest(BaseModel):
    operations: List[WishlistTagOperation]
    
    class Config:
        populate_by_name = True

class UpdatedDataResponse(BaseModel):
    expenses: List[ApiExpense] = []
    tags: List[ApiTag] = []
    targets: List[ApiTarget] = []
    expense_tags: List[ApiExpenseTag] = Field([], alias="expenseTags")
    graph_edges: List[ApiGraphEdge] = Field([], alias="graphEdges")
    wishlist: List[ApiWishlistItem] = Field([], alias="wishlist")
    wishlist_tags: List[ApiWishlistTag] = Field([], alias="wishlistTags")

    class Config:
        orm_mode = True
        populate_by_name = True
