package com.example.financehub.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expense_tags",
    foreignKeys = [
        ForeignKey(entity = Expense::class, parentColumns = ["expenseID"], childColumns = ["expenseID"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Tags::class, parentColumns = ["tagID"], childColumns = ["tagID"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["expenseID", "tagID"], unique = true)
    ]
)
data class ExpenseTagsCrossRef(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val expenseID: Int,
    val tagID: Int,
    
    // Sync metadata
    val serverId: String? = null,                    // Server's UUID for this relationship
    val lastSyncedAt: Long? = null,                 // When this item was last synced successfully
    val pendingSync: Boolean = false,               // True if local changes need to be pushed to server
    val syncOperation: String? = null,              // "CREATE", "UPDATE", "DELETE" - what operation is pending
    val createdAt: Long = System.currentTimeMillis(), // When created locally
    val updatedAt: Long = System.currentTimeMillis()  // When last modified locally
)
