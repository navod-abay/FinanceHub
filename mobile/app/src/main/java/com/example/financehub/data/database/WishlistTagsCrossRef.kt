package com.example.financehub.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.UUID

@Entity(
    tableName = "wishlist_tags",
    primaryKeys = ["wishlistId", "tagID"],
    foreignKeys = [
        ForeignKey(
            entity = Wishlist::class,
            parentColumns = ["id"],
            childColumns = ["wishlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Tags::class,
            parentColumns = ["tagID"],
            childColumns = ["tagID"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["wishlistId"]), Index(value = ["tagID"])]
)
data class WishlistTagsCrossRef(
    val wishlistId: String,
    val tagID: Int,
    
    // Sync metadata - Need ID for sync tracking
    val id: String = UUID.randomUUID().toString(), 
    val serverId: String? = null,
    val lastSyncedAt: Long? = null,
    val pendingSync: Boolean = false,
    val syncOperation: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
