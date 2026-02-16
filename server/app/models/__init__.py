from sqlalchemy import Column, Integer, String, DateTime, Boolean, ForeignKey, Index
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func
import uuid
from ..database import Base


class Expense(Base):
    __tablename__ = "expenses"
    
    # Primary key - server uses UUID
    id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    
    # Original fields from Android app
    local_id = Column(Integer, nullable=True)  # Original Android auto-increment ID
    title = Column(String, nullable=False)
    amount = Column(Integer, nullable=False)
    year = Column(Integer, nullable=False)
    month = Column(Integer, nullable=False)
    date = Column(Integer, nullable=False)
    
    # Server-side metadata
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())
    deleted_at = Column(DateTime(timezone=True), nullable=True)
    
    # Relationships
    expense_tags = relationship("ExpenseTagsCrossRef", back_populates="expense", cascade="all, delete-orphan")
    
    def __repr__(self):
        return f"<Expense(id={self.id}, title={self.title}, amount={self.amount})>"


class Tag(Base):
    __tablename__ = "tags"
    
    # Primary key - server uses UUID
    id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    
    # Original fields from Android app
    local_id = Column(Integer, nullable=True)
    tag = Column(String, nullable=False, unique=True)
    monthly_amount = Column(Integer, default=0)
    current_month = Column(Integer, default=0)
    current_year = Column(Integer, default=0)
    created_day = Column(Integer, default=0)
    created_month = Column(Integer, default=0)
    created_year = Column(Integer, default=0)
    
    # Server-side metadata
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())
    deleted_at = Column(DateTime(timezone=True), nullable=True)
    
    # Relationships
    expense_tags = relationship("ExpenseTagsCrossRef", back_populates="tag", cascade="all, delete-orphan")
    targets = relationship("Target", back_populates="tag", cascade="all, delete-orphan")
    
    # Indexes
    __table_args__ = (Index('idx_tag_name', 'tag'),)
    
    def __repr__(self):
        return f"<Tag(id={self.id}, tag={self.tag})>"


class ExpenseTagsCrossRef(Base):
    __tablename__ = "expense_tags"
    
    # Primary key - server uses UUID
    id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    
    # Foreign keys
    expense_id = Column(String, ForeignKey("expenses.id"), nullable=False)
    tag_id = Column(String, ForeignKey("tags.id"), nullable=False)
    
    # Server-side metadata
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())
    
    # Relationships
    expense = relationship("Expense", back_populates="expense_tags")
    tag = relationship("Tag", back_populates="expense_tags")
    
    # Unique constraint to prevent duplicates
    __table_args__ = (Index('idx_expense_tag_unique', 'expense_id', 'tag_id', unique=True),)
    
    def __repr__(self):
        return f"<ExpenseTagsCrossRef(id={self.id}, expense_id={self.expense_id}, tag_id={self.tag_id})>"


class Target(Base):
    __tablename__ = "targets"
    
    # Primary key - server uses UUID
    id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    
    # Original composite key fields (now just regular columns)
    month = Column(Integer, nullable=False)
    year = Column(Integer, nullable=False)
    tag_id = Column(String, ForeignKey("tags.id"), nullable=False)
    
    # Original fields
    amount = Column(Integer, nullable=False)
    spent = Column(Integer, default=0)
    
    # Server-side metadata
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())
    deleted_at = Column(DateTime(timezone=True), nullable=True)
    
    # Relationships
    tag = relationship("Tag", back_populates="targets")
    
    # Indexes and unique constraint
    __table_args__ = (
        Index('idx_target_tag', 'tag_id'),
        Index('idx_target_unique', 'month', 'year', 'tag_id', unique=True)
    )
    
    def __repr__(self):
        return f"<Target(id={self.id}, month={self.month}, year={self.year}, tag_id={self.tag_id}, amount={self.amount})>"


class GraphEdge(Base):
    __tablename__ = "graph_edges"
    
    # Primary key - server uses UUID
    id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    
    # Foreign keys
    from_tag_id = Column(String, ForeignKey("tags.id"), nullable=False)
    to_tag_id = Column(String, ForeignKey("tags.id"), nullable=False)
    weight = Column(Integer, nullable=False)
    
    # Server-side metadata
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())
    
    # Indexes and unique constraint
    __table_args__ = (
        Index('idx_graph_from_tag', 'from_tag_id'),
        Index('idx_graph_unique', 'from_tag_id', 'to_tag_id', unique=True)
    )
    
    
    def __repr__(self):
        return f"<GraphEdge(id={self.id}, from_tag_id={self.from_tag_id}, to_tag_id={self.to_tag_id}, weight={self.weight})>"


class WishlistItem(Base):
    __tablename__ = "wishlist"
    
    # Primary key - server uses UUID
    id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    
    # Original fields
    name = Column(String, nullable=False)
    expected_price = Column(Integer, nullable=False)
    # tag_id removed for multi-tag support
    
    # Server-side metadata
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())
    deleted_at = Column(DateTime(timezone=True), nullable=True)
    
    # Relationships
    wishlist_tags = relationship("WishlistTagsCrossRef", back_populates="wishlist")
    
    def __repr__(self):
        return f"<WishlistItem(id={self.id}, name={self.name}, expected_price={self.expected_price})>"


class WishlistTagsCrossRef(Base):
    __tablename__ = "wishlist_tags"
    
    # Primary key - server uses UUID so we might want a surrogate key or composite PK?
    # For sync consistency with other cross refs (ExpenseTagsCrossRef doesn't have ID in my previous view but let's check)
    # ExpenseTagsCrossRef has id? No, it has composite PK in DB usually.
    # But for sync we need a server ID to track this relationship if we want to update/delete it specifically?
    # Or just composite key.
    # ExpenseTagsCrossRef in `__init__.py` has `id` (implied primarily by SQLAlchemy unless composite defined).
    # `batch_sync.py` uses `server_id` for deletion of relationships.
    # So we need a comprehensive ID or use composite.
    # `ExpenseTagsCrossRef` model I viewed earlier (in `__init__.py`) didn't show `id` field explicitly but `Base` might give it?
    # Wait, `Base = declarative_base()`. Standard.
    # `ExpenseTagsCrossRef` has `__table_args__` with unique index. 
    # Let's verify `ExpenseTagsCrossRef` again.
    # I'll create `WishlistTagsCrossRef` similar to `ExpenseTagsCrossRef` but ensuring `id` is present if needed for sync.
    # In `ExpenseRepository.kt`, `markForSync` uses "expenseID-tagID" as ID.
    # In `batch_sync.py`, `create_expense_tag` returns `server_id`?
    # `models.ExpenseTagsCrossRef` usually has a primary key.
    # Let's assume a surrogate `id` field is best for syncable entities.
    
    id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    
    wishlist_id = Column(String, ForeignKey("wishlist.id"), nullable=False)
    tag_id = Column(String, ForeignKey("tags.id"), nullable=False)
    
    # Server-side metadata
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())
    deleted_at = Column(DateTime(timezone=True), nullable=True)
    
    # Relationships
    wishlist = relationship("WishlistItem", back_populates="wishlist_tags")
    tag = relationship("Tag")

    # Unique constraint
    __table_args__ = (
        Index('idx_wishlist_tag_unique', 'wishlist_id', 'tag_id', unique=True),
    )

    def __repr__(self):
        return f"<WishlistTagsCrossRef(id={self.id}, wishlist_id={self.wishlist_id}, tag_id={self.tag_id})>"
