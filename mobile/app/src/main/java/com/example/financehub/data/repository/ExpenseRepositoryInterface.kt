package com.example.financehub.data.repository

import com.example.financehub.data.database.GraphEdge
import com.example.financehub.data.database.Tags
import com.example.financehub.data.database.models.TagRef
import com.example.financehub.sync.SyncProgress
import kotlinx.coroutines.flow.Flow

interface ExpenseRepositoryInterface {
    fun getAllTags(): Flow<List<Tags>>
    suspend fun getAllGraphEdges(): Flow<List<GraphEdge>>
    suspend fun getAllTagRefs(): Flow<List<TagRef>>
    
    // Sync-related methods
    fun getSyncState(): Flow<SyncRepositoryState>
    fun getSyncProgress(): Flow<SyncProgress>
    suspend fun triggerSync(): SyncRepositoryResult
    suspend fun hasPendingSync(): Boolean
}

/**
 * Repository-level sync states
 */
enum class SyncRepositoryState {
    IDLE,
    SYNCING,
    ERROR
}

/**
 * Repository-level sync results
 */
sealed class SyncRepositoryResult {
    data class Success(val message: String) : SyncRepositoryResult()
    data class Error(val error: String) : SyncRepositoryResult()
}