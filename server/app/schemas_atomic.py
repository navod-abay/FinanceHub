"""
Schemas for atomic sync operations.
All related entities are synced together as atomic groups.
"""
from pydantic import BaseModel, Field
from typing import List, Optional, Union, Literal, Annotated

# Import existing operation types
from .schemas import (
    CreateExpenseBatchRequest,
    UpdateExpenseBatchRequest,
    DeleteExpenseBatchRequest,
    CreateTagBatchRequest,
    UpdateTagBatchRequest,
    DeleteTagBatchRequest,
    CreateTargetBatchRequest,
    UpdateTargetBatchRequest,
    DeleteTargetBatchRequest,
    CreateExpenseTagBatchRequest,
    DeleteExpenseTagBatchRequest,
    CreateGraphEdgeBatchRequest,
    UpdateGraphEdgeBatchRequest,
    DeleteGraphEdgeBatchRequest,
    CreateWishlistBatchRequest,
    UpdateWishlistBatchRequest,
    DeleteWishlistBatchRequest,
    CreateWishlistTagBatchRequest,
    DeleteWishlistTagBatchRequest
)


# Discriminated union of all operation types
SyncOperation = Annotated[
    Union[
        CreateExpenseBatchRequest,
        UpdateExpenseBatchRequest,
        DeleteExpenseBatchRequest,
        CreateTagBatchRequest,
        UpdateTagBatchRequest,
        DeleteTagBatchRequest,
        CreateTargetBatchRequest,
        UpdateTargetBatchRequest,
        DeleteTargetBatchRequest,
        CreateExpenseTagBatchRequest,
        DeleteExpenseTagBatchRequest,
        CreateGraphEdgeBatchRequest,
        UpdateGraphEdgeBatchRequest,
        DeleteGraphEdgeBatchRequest,
        CreateWishlistBatchRequest,
        UpdateWishlistBatchRequest,
        DeleteWishlistBatchRequest,
        CreateWishlistTagBatchRequest,
        DeleteWishlistTagBatchRequest
    ],
    Field(discriminator='type')
]


class AtomicSyncGroup(BaseModel):
    """
    A logical group of operations that must succeed or fail together.
    Represents one atomic transaction.
    """
    group_id: str = Field(..., alias="groupId")
    group_type: str = Field(..., alias="groupType")
    operations: List[SyncOperation]
    
    class Config:
        populate_by_name = True


class AtomicSyncRequest(BaseModel):
    """
    Request containing multiple atomic groups.
    Each group is processed independently in its own transaction.
    """
    groups: List[AtomicSyncGroup]
    client_timestamp: int = Field(..., alias="clientTimestamp")
    device_id: Optional[str] = Field(None, alias="deviceId")
    
    class Config:
        populate_by_name = True


class EntityMapping(BaseModel):
    """
    Maps a client entity ID to its server ID after successful sync.
    """
    entity_type: str = Field(..., alias="entityType")
    client_id: str = Field(..., alias="clientId")
    server_id: str = Field(..., alias="serverId")
    
    class Config:
        populate_by_name = True


class AtomicGroupResult(BaseModel):
    """
    Result for one atomic group.
    Either entire group succeeded or entire group failed and rolled back.
    """
    group_id: str = Field(..., alias="groupId")
    success: bool
    error: Optional[str] = None
    entity_mappings: List[EntityMapping] = Field(
        default_factory=list,
        alias="entityMappings"
    )
    rolled_back: bool = Field(default=False, alias="rolledBack")
    
    class Config:
        populate_by_name = True


class AtomicSyncResponse(BaseModel):
    """
    Response containing results for all atomic groups.
    """
    group_results: List[AtomicGroupResult] = Field(..., alias="groupResults")
    server_timestamp: int = Field(..., alias="serverTimestamp")
    
    class Config:
        populate_by_name = True
