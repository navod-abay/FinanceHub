package com.example.financehub.data.repository

import com.example.financehub.data.database.GraphEdge
import com.example.financehub.data.database.Tags
import kotlinx.coroutines.flow.Flow

interface ExpenseRepositoryInterface {
    suspend fun getAllTags(): Flow<List<Tags>>;
    suspend fun getAllGraphEdges(): Flow<List<GraphEdge>>;
}