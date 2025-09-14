package com.example.financehub.data.database

import androidx.room.Entity
import androidx.room.Index
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "graph_edges",
    primaryKeys = ["fromTagId", "toTagId"],
    indices = [Index(value=["fromTagId"],unique = false)]
)
data class GraphEdge(
    val fromTagId: Int = 0,
    val toTagId: Int = 0,
    var weight: Int = 0

)