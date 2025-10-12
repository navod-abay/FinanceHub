package com.example.financehub.data.dao

import androidx.room.*
import com.example.financehub.data.database.GraphEdge
import kotlinx.coroutines.flow.Flow

@Dao
interface GraphEdgeDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEdge(edge: GraphEdge)

    @Query("SELECT * FROM graph_edges")
    fun getAllGraphEdges(): Flow<List<GraphEdge>>

    // Sync-related methods
    @Query("SELECT * FROM graph_edges WHERE pendingSync = 1")
    suspend fun getPendingSyncGraphEdges(): List<GraphEdge>

    @Query("SELECT * FROM graph_edges WHERE serverId = :serverId")
    suspend fun getGraphEdgeByServerId(serverId: String): GraphEdge?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGraphEdge(graphEdge: GraphEdge)

    @Query("""
        UPDATE graph_edges 
        SET serverId = :serverId, lastSyncedAt = :lastSyncedAt, pendingSync = :pendingSync, syncOperation = :syncOperation
        WHERE fromTagId = :fromTagId AND toTagId = :toTagId
    """)
    suspend fun updateSyncMetadata(
        fromTagId: Int,
        toTagId: Int,
        serverId: String?,
        lastSyncedAt: Long?,
        pendingSync: Boolean,
        syncOperation: String?
    )

    @Query("""
        UPDATE graph_edges 
        SET weight = :weight, updatedAt = :updatedAt, lastSyncedAt = :lastSyncedAt
        WHERE fromTagId = :fromTagId AND toTagId = :toTagId
    """)
    suspend fun updateFromServer(
        fromTagId: Int,
        toTagId: Int,
        weight: Int,
        updatedAt: Long,
        lastSyncedAt: Long
    )

    @Query("""
        UPDATE graph_edges 
        SET pendingSync = 1, syncOperation = :operation, updatedAt = :updatedAt
        WHERE fromTagId = :fromTagId AND toTagId = :toTagId
    """)
    suspend fun markForSync(fromTagId: Int, toTagId: Int, operation: String, updatedAt: Long)

    @Query("DELETE FROM graph_edges WHERE createdAt < :cutoffTime")
    suspend fun deleteOldGraphEdges(cutoffTime: Long)
}