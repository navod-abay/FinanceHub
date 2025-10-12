package com.example.financehub.data.dao


import androidx.room.*
import com.example.financehub.data.database.ExpenseTagsCrossRef
import com.example.financehub.data.database.Tags

@Dao
interface ExpenseTagsCrossRefDao {
    @Insert
    suspend fun insertExpenseTagsCrossRef(ref: ExpenseTagsCrossRef)

    @Query("DELETE FROM expense_tags WHERE expenseID = :expenseId")
    suspend fun deleteExpenseTagCrossRefs(expenseId: Int)

    @Query("SELECT * FROM tags INNER JOIN expense_tags ON tags.tagID = expense_tags.tagID WHERE expense_tags.expenseID = :expenseID")
    suspend fun getTagsForExpense(expenseID: Int): List<Tags>

    @Delete
    suspend fun deleteExpenseTagsCrossRef(ref: ExpenseTagsCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(parents: List<ExpenseTagsCrossRef>)

    // Sync-related methods
    @Query("SELECT * FROM expense_tags WHERE pendingSync = 1")
    suspend fun getPendingSyncExpenseTags(): List<ExpenseTagsCrossRef>

    @Query("SELECT * FROM expense_tags WHERE serverId = :serverId")
    suspend fun getExpenseTagByServerId(serverId: String): ExpenseTagsCrossRef?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenseTag(expenseTag: ExpenseTagsCrossRef)

    @Query("""
        UPDATE expense_tags 
        SET serverId = :serverId, lastSyncedAt = :lastSyncedAt, pendingSync = :pendingSync, syncOperation = :syncOperation
        WHERE expenseID = :expenseId AND tagID = :tagId
    """)
    suspend fun updateSyncMetadata(
        expenseId: Int,
        tagId: Int,
        serverId: String?,
        lastSyncedAt: Long?,
        pendingSync: Boolean,
        syncOperation: String?
    )

    @Query("""
        UPDATE expense_tags 
        SET pendingSync = 1, syncOperation = :operation, updatedAt = :updatedAt
        WHERE expenseID = :expenseId AND tagID = :tagId
    """)
    suspend fun markForSync(expenseId: Int, tagId: Int, operation: String, updatedAt: Long)

    @Query("DELETE FROM expense_tags WHERE createdAt < :cutoffTime")
    suspend fun deleteOldExpenseTags(cutoffTime: Long)
}