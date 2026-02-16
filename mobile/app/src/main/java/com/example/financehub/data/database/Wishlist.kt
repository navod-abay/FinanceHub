package com.example.financehub.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "wishlist"
)
data class Wishlist(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val expectedPrice: Int,
    // tagID removed for multi-tag support
    
    // Sync metadata
    val serverId: String? = null,
    val lastSyncedAt: Long? = null,
    val pendingSync: Boolean = false,
    val syncOperation: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
