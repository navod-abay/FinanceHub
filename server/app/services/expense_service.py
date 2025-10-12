from sqlalchemy.orm import Session
from sqlalchemy import and_, func
from typing import List, Dict, Any
import uuid

from ..models import Expense, Tag, ExpenseTagsCrossRef, Target, GraphEdge
from ..models.schemas import ExpenseCreate, TagResponse
from .graph_service import GraphService


class ExpenseService:
    def __init__(self, db: Session):
        self.db = db
        self.graph_service = GraphService(db)

    async def add_expense_with_tags(
        self,
        expense_data: ExpenseCreate,
        existing_tag_ids: List[str],
        new_tag_names: List[str],
        device_timestamp: int
    ) -> Dict[str, Any]:
        """
        Add expense with all related operations in a single transaction
        """
        try:
            # Create the expense
            new_expense = Expense(
                local_id=expense_data.local_id,
                title=expense_data.title,
                amount=expense_data.amount,
                year=expense_data.year,
                month=expense_data.month,
                date=expense_data.date
            )
            
            self.db.add(new_expense)
            self.db.flush()  # Get the ID without committing
            
            created_tags = []
            all_tag_ids = []
            
            # Handle existing tags
            for tag_id in existing_tag_ids:
                tag = self.db.query(Tag).filter(Tag.id == tag_id).first()
                if tag:
                    # Update tag monthly amount
                    await self._update_tag_amount(tag, expense_data.amount, expense_data.month, expense_data.year)
                    
                    # Create expense-tag association
                    expense_tag = ExpenseTagsCrossRef(
                        expense_id=new_expense.id,
                        tag_id=tag.id
                    )
                    self.db.add(expense_tag)
                    all_tag_ids.append(tag.id)
                    
                    # Update target if exists
                    await self._update_target_spent(tag.id, expense_data.month, expense_data.year, expense_data.amount)
            
            # Handle new tags
            for tag_name in new_tag_names:
                # Check if tag already exists
                existing_tag = self.db.query(Tag).filter(Tag.tag == tag_name).first()
                if existing_tag:
                    # Use existing tag
                    await self._update_tag_amount(existing_tag, expense_data.amount, expense_data.month, expense_data.year)
                    expense_tag = ExpenseTagsCrossRef(
                        expense_id=new_expense.id,
                        tag_id=existing_tag.id
                    )
                    self.db.add(expense_tag)
                    all_tag_ids.append(existing_tag.id)
                    await self._update_target_spent(existing_tag.id, expense_data.month, expense_data.year, expense_data.amount)
                else:
                    # Create new tag
                    new_tag = Tag(
                        tag=tag_name,
                        monthly_amount=expense_data.amount,
                        current_month=expense_data.month,
                        current_year=expense_data.year,
                        created_day=expense_data.date,
                        created_month=expense_data.month,
                        created_year=expense_data.year
                    )
                    self.db.add(new_tag)
                    self.db.flush()
                    
                    # Create expense-tag association
                    expense_tag = ExpenseTagsCrossRef(
                        expense_id=new_expense.id,
                        tag_id=new_tag.id
                    )
                    self.db.add(expense_tag)
                    all_tag_ids.append(new_tag.id)
                    
                    created_tags.append(TagResponse.from_orm(new_tag))
            
            # Update graph edges for recommendations
            await self.graph_service.update_graph_edges(all_tag_ids)
            
            self.db.commit()
            
            return {
                "expense_id": new_expense.id,
                "created_tags": created_tags,
                "affected_entities": {
                    "expenses": [new_expense.id],
                    "tags": all_tag_ids,
                    "expense_tags": [f"{new_expense.id}-{tag_id}" for tag_id in all_tag_ids]
                }
            }
            
        except Exception as e:
            self.db.rollback()
            raise e

    async def update_expense_with_tags(
        self,
        expense_id: str,
        expense_data: ExpenseCreate,
        added_existing_tags: List[str],
        removed_tags: List[str],
        added_new_tags: List[str],
        device_timestamp: int
    ) -> Dict[str, Any]:
        """
        Update expense and its tag associations
        """
        try:
            # Get existing expense
            expense = self.db.query(Expense).filter(Expense.id == expense_id).first()
            if not expense:
                raise ValueError("Expense not found")
            
            # Store old amount for calculations
            old_amount = expense.amount
            
            # Update expense fields
            expense.title = expense_data.title
            expense.amount = expense_data.amount
            expense.year = expense_data.year
            expense.month = expense_data.month
            expense.date = expense_data.date
            
            affected_tag_ids = []
            
            # Handle removed tags
            for tag_id in removed_tags:
                await self._remove_tag_from_expense(expense.id, tag_id, old_amount)
                affected_tag_ids.append(tag_id)
            
            # Handle added existing tags
            for tag_id in added_existing_tags:
                tag = self.db.query(Tag).filter(Tag.id == tag_id).first()
                if tag:
                    await self._add_tag_to_expense(expense.id, tag, expense_data.amount, expense_data.month, expense_data.year)
                    affected_tag_ids.append(tag_id)
            
            # Handle added new tags
            for tag_name in added_new_tags:
                existing_tag = self.db.query(Tag).filter(Tag.tag == tag_name).first()
                if existing_tag:
                    await self._add_tag_to_expense(expense.id, existing_tag, expense_data.amount, expense_data.month, expense_data.year)
                    affected_tag_ids.append(existing_tag.id)
                else:
                    new_tag = Tag(
                        tag=tag_name,
                        monthly_amount=expense_data.amount,
                        current_month=expense_data.month,
                        current_year=expense_data.year,
                        created_day=expense_data.date,
                        created_month=expense_data.month,
                        created_year=expense_data.year
                    )
                    self.db.add(new_tag)
                    self.db.flush()
                    
                    await self._add_tag_to_expense(expense.id, new_tag, expense_data.amount, expense_data.month, expense_data.year)
                    affected_tag_ids.append(new_tag.id)
            
            # Update graph edges
            current_tag_ids = [assoc.tag_id for assoc in expense.expense_tags]
            await self.graph_service.update_graph_edges(current_tag_ids)
            
            self.db.commit()
            
            return {
                "affected_entities": {
                    "expenses": [expense_id],
                    "tags": affected_tag_ids
                }
            }
            
        except Exception as e:
            self.db.rollback()
            raise e

    async def delete_expense(self, expense_id: str, device_timestamp: int) -> Dict[str, Any]:
        """
        Delete expense and clean up all related data
        """
        try:
            expense = self.db.query(Expense).filter(Expense.id == expense_id).first()
            if not expense:
                raise ValueError("Expense not found")
            
            affected_tag_ids = []
            
            # Remove from all tag associations and update amounts
            for expense_tag in expense.expense_tags:
                tag = expense_tag.tag
                
                # Decrement tag amount
                tag.monthly_amount = max(0, tag.monthly_amount - expense.amount)
                
                # Update target if exists
                target = self.db.query(Target).filter(
                    and_(
                        Target.tag_id == tag.id,
                        Target.month == expense.month,
                        Target.year == expense.year
                    )
                ).first()
                
                if target:
                    target.spent = max(0, target.spent - expense.amount)
                
                affected_tag_ids.append(tag.id)
            
            # Soft delete the expense
            expense.deleted_at = func.now()
            
            self.db.commit()
            
            return {
                "affected_entities": {
                    "expenses": [expense_id],
                    "tags": affected_tag_ids
                }
            }
            
        except Exception as e:
            self.db.rollback()
            raise e

    async def _update_tag_amount(self, tag: Tag, amount: int, month: int, year: int):
        """Update tag monthly amount and current month/year"""
        tag.monthly_amount += amount
        tag.current_month = month
        tag.current_year = year

    async def _update_target_spent(self, tag_id: str, month: int, year: int, amount: int):
        """Update target spent amount if target exists"""
        target = self.db.query(Target).filter(
            and_(
                Target.tag_id == tag_id,
                Target.month == month,
                Target.year == year
            )
        ).first()
        
        if target:
            target.spent += amount

    async def _add_tag_to_expense(self, expense_id: str, tag: Tag, amount: int, month: int, year: int):
        """Add tag to expense with all updates"""
        # Create association
        expense_tag = ExpenseTagsCrossRef(
            expense_id=expense_id,
            tag_id=tag.id
        )
        self.db.add(expense_tag)
        
        # Update tag amount
        await self._update_tag_amount(tag, amount, month, year)
        
        # Update target
        await self._update_target_spent(tag.id, month, year, amount)

    async def _remove_tag_from_expense(self, expense_id: str, tag_id: str, amount: int):
        """Remove tag from expense with all updates"""
        # Remove association
        expense_tag = self.db.query(ExpenseTagsCrossRef).filter(
            and_(
                ExpenseTagsCrossRef.expense_id == expense_id,
                ExpenseTagsCrossRef.tag_id == tag_id
            )
        ).first()
        
        if expense_tag:
            self.db.delete(expense_tag)
            
            # Update tag amount
            tag = self.db.query(Tag).filter(Tag.id == tag_id).first()
            if tag:
                tag.monthly_amount = max(0, tag.monthly_amount - amount)