package com.example.financehub.data.repository

import com.example.financehub.data.database.GraphEdge
import com.example.financehub.data.database.Tags
import com.example.financehub.data.database.models.TagRef
import kotlinx.coroutines.flow.Flow

interface ExpenseRepositoryInterface {
    fun getAllTags(): Flow<List<Tags>>;
    suspend fun getAllGraphEdges(): Flow<List<GraphEdge>>;
    suspend fun getAllTagRefs(): Flow<List<TagRef>>;
}