package com.example.financehub.data.dao

import androidx.room.*
import com.example.financehub.data.database.GraphEdge

interface GraphEdgeDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEdge(edge: GraphEdge)
}