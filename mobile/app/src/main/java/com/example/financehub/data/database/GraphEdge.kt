package com.example.financehub.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "graph_edges",
    indices = [
        Index(value=["fromTagId"], unique = false),
        Index(value=["fromTagId", "toTagId"], unique = true)
    ]
)
data class GraphEdge(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fromTagId: Int = 0,
    val toTagId: Int = 0,
    var weight: Int = 0,
    
    // Sync metadata
    val serverId: String? = null,                    // Server's UUID for this edge
    val lastSyncedAt: Long? = null,                 // When this item was last synced successfully
    val pendingSync: Boolean = false,               // True if local changes need to be pushed to server
    val syncOperation: String? = null,              // "CREATE", "UPDATE", "DELETE" - what operation is pending
    val createdAt: Long = System.currentTimeMillis(), // When created locally
    val updatedAt: Long = System.currentTimeMillis()  // When last modified locally
)