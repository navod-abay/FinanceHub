package com.example.financehub.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores mappings between local entity IDs and server UUIDs.
 * Used to track which entities have been synced and their server IDs.
 */
@Entity(
    tableName = "entity_mappings",
    indices = [
        Index(value = ["entityType", "localId"], unique = true),
        Index(value = ["serverId"])
    ]
)
data class EntityMapping(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,                      // "expense", "tag", "target", "wishlist", etc.
    val localId: Long,                           // Local database ID
    val serverId: String,                        // Server UUID
    val syncedAt: Long = System.currentTimeMillis()
)
