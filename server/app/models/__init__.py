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