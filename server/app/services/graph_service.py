from sqlalchemy.orm import Session
from sqlalchemy import and_, func
from typing import List, Dict
from itertools import combinations

from ..models import GraphEdge, ExpenseTagsCrossRef, Tag


class GraphService:
    def __init__(self, db: Session):
        self.db = db

    async def update_graph_edges(self, tag_ids: List[str]):
        """
        Update graph edges based on tag co-occurrences
        Similar to the Android implementation but optimized for server
        """
        if len(tag_ids) < 2:
            return
        
        # Generate all pairs of tags from this expense
        tag_pairs = list(combinations(tag_ids, 2))
        
        for tag1_id, tag2_id in tag_pairs:
            # Update edge from tag1 to tag2
            await self._update_edge_weight(tag1_id, tag2_id)
            # Update edge from tag2 to tag1 (bidirectional)
            await self._update_edge_weight(tag2_id, tag1_id)

    async def _update_edge_weight(self, from_tag_id: str, to_tag_id: str):
        """Update or create a graph edge with incremented weight"""
        existing_edge = self.db.query(GraphEdge).filter(
            and_(
                GraphEdge.from_tag_id == from_tag_id,
                GraphEdge.to_tag_id == to_tag_id
            )
        ).first()
        
        if existing_edge:
            existing_edge.weight += 1
        else:
            new_edge = GraphEdge(
                from_tag_id=from_tag_id,
                to_tag_id=to_tag_id,
                weight=1
            )
            self.db.add(new_edge)

    async def get_tag_recommendations(self, tag_id: str, max_recommendations: int = 10) -> List[Dict]:
        """
        Get tag recommendations using random walk algorithm
        Port of the Android recommendation algorithm
        """
        # Get all tags for mapping
        all_tags = self.db.query(Tag).filter(Tag.deleted_at.is_(None)).all()
        tag_map = {tag.id: tag for tag in all_tags}
        
        if tag_id not in tag_map:
            return []
        
        # Build adjacency matrix
        graph_edges = self.db.query(GraphEdge).all()
        graph = {}
        
        # Initialize graph
        for tag in all_tags:
            graph[tag.id] = {}
        
        # Populate graph with weights
        for edge in graph_edges:
            if edge.from_tag_id in graph:
                graph[edge.from_tag_id][edge.to_tag_id] = edge.weight
        
        # Normalize weights (convert to probabilities)
        for from_tag, connections in graph.items():
            total_weight = sum(connections.values())
            if total_weight > 0:
                for to_tag in connections:
                    graph[from_tag][to_tag] = connections[to_tag] / total_weight
        
        # Random walk parameters
        alpha = 0.8
        iterations = 20
        visited_counts = {}
        current_node = tag_id
        
        for _ in range(iterations):
            walk_steps = 0
            max_walk_steps = 10  # Prevent infinite loops
            
            while walk_steps < max_walk_steps:
                if current_node == tag_id and len(visited_counts) > 0:
                    # Random restart with probability (1 - alpha)
                    import random
                    if random.random() < (1 - alpha):
                        break
                
                # Get next node based on probabilities
                if current_node in graph and graph[current_node]:
                    edges = graph[current_node]
                    # Choose next node based on weights
                    import random
                    rand_val = random.random()
                    cumulative_prob = 0.0
                    
                    next_node = None
                    for to_tag, prob in edges.items():
                        cumulative_prob += prob
                        if rand_val <= cumulative_prob:
                            next_node = to_tag
                            break
                    
                    if next_node and next_node != tag_id:
                        current_node = next_node
                        visited_counts[current_node] = visited_counts.get(current_node, 0) + 1
                        walk_steps += 1
                    else:
                        break
                else:
                    break
            
            # Reset to starting node for next iteration
            current_node = tag_id
        
        # Sort by visit count and return top recommendations
        recommendations = []
        for recommended_tag_id, count in sorted(visited_counts.items(), key=lambda x: x[1], reverse=True):
            if recommended_tag_id in tag_map:
                tag = tag_map[recommended_tag_id]
                recommendations.append({
                    "tag_id": tag.id,
                    "tag_name": tag.tag,
                    "score": count / iterations  # Normalize score
                })
        
        return recommendations[:max_recommendations]

    async def rebuild_graph_from_scratch(self):
        """
        Rebuild the entire graph from expense-tag associations
        Useful for initial setup or data recovery
        """
        # Clear existing edges
        self.db.query(GraphEdge).delete()
        
        # Get all expense-tag associations grouped by expense
        expenses_with_tags = self.db.query(
            ExpenseTagsCrossRef.expense_id,
            func.array_agg(ExpenseTagsCrossRef.tag_id).label('tag_ids')
        ).group_by(ExpenseTagsCrossRef.expense_id).all()
        
        # Build graph edges from co-occurrences
        for expense_tags in expenses_with_tags:
            tag_ids = expense_tags.tag_ids
            if len(tag_ids) >= 2:
                await self.update_graph_edges(tag_ids)
        
        self.db.commit()