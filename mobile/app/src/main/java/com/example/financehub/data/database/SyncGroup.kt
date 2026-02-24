package com.example.financehub.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents an atomic sync group.
 * All entities within a group are synced atomically - they either all succeed or all fail.
 */
@Entity(tableName = "sync_groups")
data class SyncGroup(
    @PrimaryKey(autoGenerate = true) val groupId: Long = 0,
    val groupType: String = "GENERIC",           // "EXPENSE_WITH_TAGS", "TAG", "TARGET", "GRAPH_EDGE", "WISHLIST_WITH_TAGS", "GENERIC"
    val status: String = "PENDING",              // "PENDING", "SYNCING", "SUCCESS", "FAILED"
    val createdAt: Long = System.currentTimeMillis(),
    val syncedAt: Long? = null,
    val errorMessage: String? = null
)
