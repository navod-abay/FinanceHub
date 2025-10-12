package com.example.financehub.data.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.financehub.data.database.models.TagRef

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val expenseID: Int = 0,
    val title: String,
    val amount: Int,
    val year: Int,
    val month: Int,
    val date: Int,
    
    // Sync metadata
    val serverId: String? = null,                    // Server's UUID for this expense
    val lastSyncedAt: Long? = null,                 // When this item was last synced successfully
    val pendingSync: Boolean = false,               // True if local changes need to be pushed to server
    val syncOperation: String? = null,              // "CREATE", "UPDATE", "DELETE" - what operation is pending
    val createdAt: Long = System.currentTimeMillis(), // When created locally
    val updatedAt: Long = System.currentTimeMillis()  // When last modified locally
)


data class ExpenseWithTags(
    @Embedded val expense: Expense,
    @Relation(
        parentColumn = "expenseID",
        entityColumn = "tagID",
        associateBy = Junction(
            value = ExpenseTagsCrossRef::class,
            parentColumn = "expenseID",
            entityColumn = "tagID"
        )
    )
    val tags: List<Tags>
)