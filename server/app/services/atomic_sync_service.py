"""
Atomic Sync Service
Processes atomic sync groups with ACID guarantees.
Each group is processed in a savepoint (nested transaction).
"""
from typing import List, Optional, Dict, Any
from sqlalchemy.orm import Session
from sqlalchemy import exc
from datetime import datetime
import logging

from ..models import (
    Expense, Tag, Target, ExpenseTagsCrossRef,
    GraphEdge, WishlistItem, WishlistTagsCrossRef,
    EntityMapping as EntityMappingModel
)
from ..schemas import (
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
from ..schemas_atomic import (
    AtomicSyncGroup,
    AtomicGroupResult,
    EntityMapping,
    SyncOperation
)

logger = logging.getLogger(__name__)


class AtomicSyncService:
    """
    Processes atomic sync groups with ACID guarantees.
    Each group succeeds completely or fails completely with rollback.
    """
    
    def __init__(self, db: Session):
        self.db = db
        self.entity_mappings: Dict[str, str] = {}  # clientId -> serverId within current group
    
    # Idempotency helpers
    def _get_persistent_mapping(self, entity_type: str, client_id: str) -> Optional[str]:
        """
        Check if a persistent mapping exists for this client_id.
        Returns server_id if found, None otherwise.
        """
        mapping = self.db.query(EntityMappingModel).filter(
            EntityMappingModel.entity_type == entity_type,
            EntityMappingModel.client_id == client_id
        ).first()
        
        if mapping:
            logger.debug(f"Found persistent mapping: {entity_type}:{client_id} -> {mapping.server_id}")
            return mapping.server_id
        return None
    
    def _save_persistent_mapping(self, entity_type: str, client_id: str, server_id: str) -> None:
        """
        Save a persistent mapping. Handles race conditions gracefully.
        If mapping already exists (e.g., concurrent request), silently ignore.
        """
        try:
            mapping = EntityMappingModel(
                entity_type=entity_type,
                client_id=client_id,
                server_id=server_id
            )
            self.db.add(mapping)
            self.db.flush()
            logger.debug(f"Saved persistent mapping: {entity_type}:{client_id} -> {server_id}")
        except exc.IntegrityError:
            # Mapping already exists (race condition) - that's fine, the operation was idempotent
            logger.debug(f"Mapping already exists: {entity_type}:{client_id} (concurrent request)")
            self.db.rollback()
        
    def process_groups(
        self,
        groups: List[AtomicSyncGroup],
        client_timestamp: int
    ) -> List[AtomicGroupResult]:
        """Process all groups. Each group is independent transaction."""
        results = []
        
        for group in groups:
            # Reset mappings for each group
            self.entity_mappings.clear()
            result = self.process_single_group(group, client_timestamp)
            results.append(result)
            
        return results
    
    def process_single_group(
        self,
        group: AtomicSyncGroup,
        client_timestamp: int
    ) -> AtomicGroupResult:
        """
        Process one atomic group in a savepoint.
        All operations succeed or all rollback.
        """
        # Start savepoint for this group
        savepoint = self.db.begin_nested()
        
        try:
            entity_mappings = []
            
            logger.info(f"Processing group {group.group_id} with {len(group.operations)} operations")
            
            # Process operations in sequence
            for operation in group.operations:
                mapping = self._process_operation(operation, group.group_id)
                if mapping:
                    entity_mappings.append(mapping)
            
            # All succeeded - commit savepoint
            savepoint.commit()
            
            logger.info(f"Group {group.group_id} succeeded with {len(entity_mappings)} mappings")
            
            return AtomicGroupResult(
                group_id=group.group_id,
                success=True,
                entity_mappings=entity_mappings,
                rolled_back=False
            )
            
        except Exception as e:
            # Rollback this group's savepoint
            savepoint.rollback()
            
            logger.error(
                f"Group {group.group_id} failed: {str(e)}",
                exc_info=True
            )
            
            return AtomicGroupResult(
                group_id=group.group_id,
                success=False,
                error=str(e),
                entity_mappings=[],
                rolled_back=True
            )
    
    def _process_operation(
        self,
        operation: Any,
        group_id: str
    ) -> Optional[EntityMapping]:
        """Process a single operation within a group."""
        
        if isinstance(operation, CreateExpenseBatchRequest):
            return self._create_expense(operation)
        elif isinstance(operation, UpdateExpenseBatchRequest):
            return self._update_expense(operation)
        elif isinstance(operation, DeleteExpenseBatchRequest):
            return self._delete_expense(operation)
        elif isinstance(operation, CreateTagBatchRequest):
            return self._create_tag(operation)
        elif isinstance(operation, UpdateTagBatchRequest):
            return self._update_tag(operation)
        elif isinstance(operation, DeleteTagBatchRequest):
            return self._delete_tag(operation)
        elif isinstance(operation, CreateExpenseTagBatchRequest):
            return self._create_expense_tag(operation)
        elif isinstance(operation, DeleteExpenseTagBatchRequest):
            return self._delete_expense_tag(operation)
        elif isinstance(operation, CreateTargetBatchRequest):
            return self._create_target(operation)
        elif isinstance(operation, UpdateTargetBatchRequest):
            return self._update_target(operation)
        elif isinstance(operation, DeleteTargetBatchRequest):
            return self._delete_target(operation)
        elif isinstance(operation, CreateGraphEdgeBatchRequest):
            return self._create_graph_edge(operation)
        elif isinstance(operation, UpdateGraphEdgeBatchRequest):
            return self._update_graph_edge(operation)
        elif isinstance(operation, DeleteGraphEdgeBatchRequest):
            return self._delete_graph_edge(operation)
        elif isinstance(operation, CreateWishlistBatchRequest):
            return self._create_wishlist(operation)
        elif isinstance(operation, UpdateWishlistBatchRequest):
            return self._update_wishlist(operation)
        elif isinstance(operation, DeleteWishlistBatchRequest):
            return self._delete_wishlist(operation)
        elif isinstance(operation, CreateWishlistTagBatchRequest):
            return self._create_wishlist_tag(operation)
        elif isinstance(operation, DeleteWishlistTagBatchRequest):
            return self._delete_wishlist_tag(operation)
        else:
            raise ValueError(f"Unknown operation type: {type(operation)}")
    
    # Expense operations
    def _create_expense(self, operation: CreateExpenseBatchRequest) -> EntityMapping:
        # Check for existing mapping (idempotency)
        existing_id = self._get_persistent_mapping("expense", operation.client_id)
        if existing_id:
            logger.info(f"Expense already exists for client_id {operation.client_id}, returning existing server_id {existing_id}")
            self.entity_mappings[operation.client_id] = existing_id
            return EntityMapping(
                entity_type="expense",
                client_id=operation.client_id,
                server_id=existing_id
            )
        
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
        self.db.add(new_expense)
        self.db.flush()
        
        # Save persistent mapping
        self._save_persistent_mapping("expense", operation.client_id, str(new_expense.id))
        
        # Track mapping for use in same group
        self.entity_mappings[operation.client_id] = new_expense.id
        
        return EntityMapping(
            entity_type="expense",
            client_id=operation.client_id,
            server_id=str(new_expense.id)
        )
    
    def _update_expense(self, operation: UpdateExpenseBatchRequest) -> Optional[EntityMapping]:
        expense = self.db.query(Expense).filter(Expense.id == operation.server_id).first()
        if not expense:
            raise ValueError(f"Expense not found: {operation.server_id}")
        
        expense.title = operation.title
        expense.amount = operation.amount
        expense.year = operation.year
        expense.month = operation.month
        expense.date = operation.date
        expense.updated_at = datetime.utcnow()
        
        return None  # No new mapping needed for updates
    
    def _delete_expense(self, operation: DeleteExpenseBatchRequest) -> Optional[EntityMapping]:
        expense = self.db.query(Expense).filter(Expense.id == operation.server_id).first()
        if not expense:
            raise ValueError(f"Expense not found: {operation.server_id}")
        
        expense.deleted_at = datetime.utcnow()
        return None
    
    # Tag operations
    def _create_tag(self, operation: CreateTagBatchRequest) -> EntityMapping:
        # Check for existing mapping (idempotency)
        existing_id = self._get_persistent_mapping("tag", operation.client_id)
        if existing_id:
            logger.info(f"Tag already exists for client_id {operation.client_id}, returning existing server_id {existing_id}")
            self.entity_mappings[operation.client_id] = existing_id
            return EntityMapping(
                entity_type="tag",
                client_id=operation.client_id,
                server_id=existing_id
            )
        
        # Create new tag
        new_tag = Tag(
            tag=operation.name,  # Fixed: use 'tag' field, not 'name'
            monthly_amount=operation.monthly_amount,
            current_month=operation.current_month,
            current_year=operation.current_year,
            created_day=operation.created_day,
            created_month=operation.created_month,
            created_year=operation.created_year,
            created_at=datetime.utcnow(),
            updated_at=datetime.utcnow()
        )
        self.db.add(new_tag)
        self.db.flush()
        
        # Save persistent mapping
        self._save_persistent_mapping("tag", operation.client_id, str(new_tag.id))
        
        self.entity_mappings[operation.client_id] = new_tag.id
        
        return EntityMapping(
            entity_type="tag",
            client_id=operation.client_id,
            server_id=str(new_tag.id)
        )
    
    def _update_tag(self, operation: UpdateTagBatchRequest) -> Optional[EntityMapping]:
        tag = self.db.query(Tag).filter(Tag.id == operation.server_id).first()
        if not tag:
            raise ValueError(f"Tag not found: {operation.server_id}")
        
        tag.tag = operation.name  # Fixed: use 'tag' field, not 'name'
        tag.monthly_amount = operation.monthly_amount
        tag.current_month = operation.current_month
        tag.current_year = operation.current_year
        tag.updated_at = datetime.utcnow()
        
        return None
    
    def _delete_tag(self, operation: DeleteTagBatchRequest) -> Optional[EntityMapping]:
        tag = self.db.query(Tag).filter(Tag.id == operation.server_id).first()
        if not tag:
            raise ValueError(f"Tag not found: {operation.server_id}")
        
        tag.deleted_at = datetime.utcnow()
        return None
    
    # ExpenseTag operations
    def _create_expense_tag(self, operation: CreateExpenseTagBatchRequest) -> EntityMapping:
        # Check for existing mapping (idempotency)
        existing_id = self._get_persistent_mapping("expense_tag", operation.client_id)
        if existing_id:
            logger.info(f"ExpenseTag already exists for client_id {operation.client_id}, returning existing server_id {existing_id}")
            return EntityMapping(
                entity_type="expense_tag",
                client_id=operation.client_id,
                server_id=existing_id
            )
        
        # Resolve IDs (might be from earlier in this group)
        expense_id = self._resolve_id(operation.expense_id, "expense")
        tag_id = self._resolve_id(operation.tag_id, "tag")
        
        new_expense_tag = ExpenseTagsCrossRef(
            expense_id=expense_id,
            tag_id=tag_id,
            created_at=datetime.utcnow(),
            updated_at=datetime.utcnow()
        )
        self.db.add(new_expense_tag)
        self.db.flush()
        
        # Save persistent mapping
        self._save_persistent_mapping("expense_tag", operation.client_id, str(new_expense_tag.id))
        
        return EntityMapping(
            entity_type="expense_tag",
            client_id=operation.client_id,
            server_id=str(new_expense_tag.id)
        )
    
    def _delete_expense_tag(self, operation: DeleteExpenseTagBatchRequest) -> Optional[EntityMapping]:
        expense_tag = self.db.query(ExpenseTagsCrossRef).filter(
            ExpenseTagsCrossRef.id == operation.server_id
        ).first()
        if not expense_tag:
            raise ValueError(f"ExpenseTag not found: {operation.server_id}")
        
        self.db.delete(expense_tag)
        return None
    
    # Target operations
    def _create_target(self, operation: CreateTargetBatchRequest) -> EntityMapping:
        # Check for existing mapping (idempotency)
        existing_id = self._get_persistent_mapping("target", operation.client_id)
        if existing_id:
            logger.info(f"Target already exists for client_id {operation.client_id}, returning existing server_id {existing_id}")
            return EntityMapping(
                entity_type="target",
                client_id=operation.client_id,
                server_id=existing_id
            )
        
        # Resolve tag ID
        tag_id = self._resolve_id(operation.tag_id, "tag")
        
        new_target = Target(
            month=operation.month,
            year=operation.year,
            tag_id=tag_id,
            amount=operation.amount,
            spent=operation.spent,
            created_at=datetime.utcnow(),
            updated_at=datetime.utcnow()
        )
        self.db.add(new_target)
        self.db.flush()
        
        # Save persistent mapping
        self._save_persistent_mapping("target", operation.client_id, str(new_target.id))
        
        return EntityMapping(
            entity_type="target",
            client_id=operation.client_id,
            server_id=str(new_target.id)
        )
    
    def _update_target(self, operation: UpdateTargetBatchRequest) -> Optional[EntityMapping]:
        target = self.db.query(Target).filter(Target.id == operation.server_id).first()
        if not target:
            raise ValueError(f"Target not found: {operation.server_id}")
        
        target.amount = operation.amount
        target.spent = operation.spent
        target.updated_at = datetime.utcnow()
        
        return None
    
    def _delete_target(self, operation: DeleteTargetBatchRequest) -> Optional[EntityMapping]:
        target = self.db.query(Target).filter(Target.id == operation.server_id).first()
        if not target:
            raise ValueError(f"Target not found: {operation.server_id}")
        
        target.deleted_at = datetime.utcnow()
        return None
    
    # GraphEdge operations
    def _create_graph_edge(self, operation: CreateGraphEdgeBatchRequest) -> EntityMapping:
        # Check for existing mapping (idempotency)
        existing_id = self._get_persistent_mapping("graph_edge", operation.client_id)
        if existing_id:
            logger.info(f"GraphEdge already exists for client_id {operation.client_id}, returning existing server_id {existing_id}")
            return EntityMapping(
                entity_type="graph_edge",
                client_id=operation.client_id,
                server_id=existing_id
            )
        
        # Resolve tag IDs
        from_tag_id = self._resolve_id(operation.from_tag_id, "tag")
        to_tag_id = self._resolve_id(operation.to_tag_id, "tag")
        
        new_edge = GraphEdge(
            from_tag_id=from_tag_id,
            to_tag_id=to_tag_id,
            weight=operation.weight,
            created_at=datetime.utcnow(),
            updated_at=datetime.utcnow()
        )
        self.db.add(new_edge)
        self.db.flush()
        
        # Save persistent mapping
        self._save_persistent_mapping("graph_edge", operation.client_id, str(new_edge.id))
        
        return EntityMapping(
            entity_type="graph_edge",
            client_id=operation.client_id,
            server_id=str(new_edge.id)
        )
    
    def _update_graph_edge(self, operation: UpdateGraphEdgeBatchRequest) -> Optional[EntityMapping]:
        edge = self.db.query(GraphEdge).filter(GraphEdge.id == operation.server_id).first()
        if not edge:
            raise ValueError(f"GraphEdge not found: {operation.server_id}")
        
        edge.weight = operation.weight
        edge.updated_at = datetime.utcnow()
        
        return None
    
    def _delete_graph_edge(self, operation: DeleteGraphEdgeBatchRequest) -> Optional[EntityMapping]:
        edge = self.db.query(GraphEdge).filter(GraphEdge.id == operation.server_id).first()
        if not edge:
            raise ValueError(f"GraphEdge not found: {operation.server_id}")
        
        self.db.delete(edge)
        return None
    
    # Wishlist operations
    def _create_wishlist(self, operation: CreateWishlistBatchRequest) -> EntityMapping:
        # Check for existing mapping (idempotency)
        existing_id = self._get_persistent_mapping("wishlist", operation.client_id)
        if existing_id:
            logger.info(f"Wishlist already exists for client_id {operation.client_id}, returning existing server_id {existing_id}")
            self.entity_mappings[operation.client_id] = existing_id
            return EntityMapping(
                entity_type="wishlist",
                client_id=operation.client_id,
                server_id=existing_id
            )
        
        # Create new wishlist
        new_wishlist = WishlistItem(
            name=operation.name,
            expected_price=operation.expected_price,
            created_at=datetime.utcnow(),
            updated_at=datetime.utcnow()
        )
        self.db.add(new_wishlist)
        self.db.flush()
        
        # Save persistent mapping
        self._save_persistent_mapping("wishlist", operation.client_id, str(new_wishlist.id))
        
        self.entity_mappings[operation.client_id] = new_wishlist.id
        
        return EntityMapping(
            entity_type="wishlist",
            client_id=operation.client_id,
            server_id=str(new_wishlist.id)
        )
    
    def _update_wishlist(self, operation: UpdateWishlistBatchRequest) -> Optional[EntityMapping]:
        wishlist = self.db.query(WishlistItem).filter(WishlistItem.id == operation.server_id).first()
        if not wishlist:
            raise ValueError(f"Wishlist not found: {operation.server_id}")
        
        wishlist.name = operation.name
        wishlist.expected_price = operation.expected_price
        wishlist.updated_at = datetime.utcnow()
        
        return None
    
    def _delete_wishlist(self, operation: DeleteWishlistBatchRequest) -> Optional[EntityMapping]:
        wishlist = self.db.query(WishlistItem).filter(WishlistItem.id == operation.server_id).first()
        if not wishlist:
            raise ValueError(f"Wishlist not found: {operation.server_id}")
        
        wishlist.deleted_at = datetime.utcnow()
        return None
    
    # WishlistTag operations
    def _create_wishlist_tag(self, operation: CreateWishlistTagBatchRequest) -> EntityMapping:
        # Check for existing mapping (idempotency)
        existing_id = self._get_persistent_mapping("wishlist_tag", operation.client_id)
        if existing_id:
            logger.info(f"WishlistTag already exists for client_id {operation.client_id}, returning existing server_id {existing_id}")
            return EntityMapping(
                entity_type="wishlist_tag",
                client_id=operation.client_id,
                server_id=existing_id
            )
        
        # Resolve IDs
        wishlist_id = self._resolve_id(operation.wishlist_id, "wishlist")
        tag_id = self._resolve_id(operation.tag_id, "tag")
        
        new_wishlist_tag = WishlistTagsCrossRef(
            wishlist_id=wishlist_id,
            tag_id=tag_id,
            created_at=datetime.utcnow(),
            updated_at=datetime.utcnow()
        )
        self.db.add(new_wishlist_tag)
        self.db.flush()
        
        # Save persistent mapping
        self._save_persistent_mapping("wishlist_tag", operation.client_id, str(new_wishlist_tag.id))
        
        return EntityMapping(
            entity_type="wishlist_tag",
            client_id=operation.client_id,
            server_id=str(new_wishlist_tag.id)
        )
    
    def _delete_wishlist_tag(self, operation: DeleteWishlistTagBatchRequest) -> Optional[EntityMapping]:
        wishlist_tag = self.db.query(WishlistTagsCrossRef).filter(
            WishlistTagsCrossRef.id == operation.server_id
        ).first()
        if not wishlist_tag:
            raise ValueError(f"WishlistTag not found: {operation.server_id}")
        
        self.db.delete(wishlist_tag)
        return None
    
    def _resolve_id(self, client_id: str, entity_type: str) -> str:
        """
        Resolve client ID to server ID.
        1. Check current group mappings
        2. Check persistent mappings
        3. Assume it's already a server ID (from previous sync)
        """
        # Check if we created this in current group
        if client_id in self.entity_mappings:
            return self.entity_mappings[client_id]
        
        # Check persistent mappings
        persistent_id = self._get_persistent_mapping(entity_type, client_id)
        if persistent_id:
            return persistent_id
        
        # Assume it's already a server ID (from previous sync)
        return client_id
