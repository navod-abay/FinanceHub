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
}