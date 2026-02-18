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
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val minPrice: Int,
    val maxPrice: Int,

    // Sync metadata
    val serverId: String? = null,
    val lastSyncedAt: Long? = null,
    val pendingSync: Boolean = false,
    val syncOperation: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
