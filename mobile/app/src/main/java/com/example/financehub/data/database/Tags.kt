package com.example.financehub.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "tags",
    indices = [Index(value = ["tag"], unique = true)]
)
data class Tags(
    @PrimaryKey(autoGenerate = true) val tagID: Int = 0,
    val tag: String = "",
    val monthlyAmount: Int = 0,
    val currentMonth: Int = 0,
    val currentYear: Int = 0,
    val createdDay: Int = 0,
    val createdMonth: Int = 0,
    val createdYear: Int = 0,
    
    // Sync metadata
    val serverId: String? = null,                    // Server's UUID for this tag
    val lastSyncedAt: Long? = null,                 // When this item was last synced successfully
    val pendingSync: Boolean = false,               // True if local changes need to be pushed to server
    val syncOperation: String? = null,              // "CREATE", "UPDATE", "DELETE" - what operation is pending
    val syncCreatedAt: Long = System.currentTimeMillis(), // When created locally (renamed to avoid confusion with existing createdDay/Month/Year)
    val syncUpdatedAt: Long = System.currentTimeMillis()  // When last modified locally
)




data class TagWithAmount(val tag: String, val monthlyAmount: Int)