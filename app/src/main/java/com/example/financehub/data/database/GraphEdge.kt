package com.example.financehub.data.database

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "graph_edges",
    primaryKeys = ["fromTagID", "toTagID"],
    indices = [Index(value=["fromTagID"],unique = false)]
)
data class GraphEdge(
    val fromTagID: Int = 0,
    val toTagID: Int = 0,
    var weight: Int = 0

)