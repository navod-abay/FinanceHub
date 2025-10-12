package com.example.financehub.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.financehub.data.database.Target
import kotlinx.coroutines.flow.Flow

@Dao
interface TargetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTarget(target: Target)

    @Query("SELECT * FROM targets WHERE month = :month AND year = :year AND tagID = :tagID LIMIT 1")
    suspend fun getTarget(month: Int, year: Int, tagID: Int): Target?

    @Query("SELECT * FROM targets WHERE year = :year AND month = :month")
    suspend fun getTargetsForMonthYear(month: Int, year: Int): List<Target>

    @Query("SELECT * FROM targets")
    suspend fun getAllTargets(): List<Target>

    @Query("SELECT * FROM targets WHERE (year > :currentYear) OR (year = :currentYear AND month >= :currentMonth)")
    suspend fun getAllTargetsFromCurrentMonth(currentMonth: Int, currentYear: Int): List<Target>

    @Query("DELETE FROM targets WHERE month = :month AND year = :year AND tagID = :tagID")
    suspend fun deleteTarget(month: Int, year: Int, tagID: Int)

    @Query("UPDATE targets SET amount = :newAmount WHERE month = :month AND year = :year AND tagID = :tagID")
    suspend fun updateTargetAmount(month: Int, year: Int, tagID: Int, newAmount: Int)

    @Query("UPDATE targets SET spent = :newSpent WHERE month = :month AND year = :year AND tagID = :tagID")
    suspend fun updateTargetSpent(month: Int, year: Int, tagID: Int, newSpent: Int)

    @Query("UPDATE targets SET spent = spent + :amount WHERE month = :month AND year = :year AND tagID = :tagID")
    suspend fun incrementSpentAmount(month: Int, year: Int, tagID: Int, amount: Int)

    @Query("SELECT COUNT(*) FROM targets WHERE month = :month AND year = :year AND spent > amount")
    fun getMissedTargetsCount(month: Int, year: Int): Flow<Int>

    @Query("SELECT COUNT(*) FROM targets WHERE month = :month AND year = :year")
    fun getTotalTargetsCount(month: Int, year: Int): Flow<Int>

    // Sync-related methods
    @Query("SELECT * FROM targets WHERE pendingSync = 1")
    suspend fun getPendingSyncTargets(): List<Target>

    @Query("SELECT * FROM targets WHERE serverId = :serverId")
    suspend fun getTargetByServerId(serverId: String): Target?

    @Query("""
        UPDATE targets 
        SET serverId = :serverId, lastSyncedAt = :lastSyncedAt, pendingSync = :pendingSync, syncOperation = :syncOperation
        WHERE month = :month AND year = :year AND tagID = :tagId
    """)
    suspend fun updateSyncMetadata(
        month: Int,
        year: Int,
        tagId: Int,
        serverId: String?,
        lastSyncedAt: Long?,
        pendingSync: Boolean,
        syncOperation: String?
    )

    @Query("""
        UPDATE targets 
        SET amount = :amount, spent = :spent, updatedAt = :updatedAt, lastSyncedAt = :lastSyncedAt
        WHERE month = :month AND year = :year AND tagID = :tagId
    """)
    suspend fun updateFromServer(
        month: Int,
        year: Int,
        tagId: Int,
        amount: Int,
        spent: Int,
        updatedAt: Long,
        lastSyncedAt: Long
    )

    @Query("""
        UPDATE targets 
        SET pendingSync = 1, syncOperation = :operation, updatedAt = :updatedAt
        WHERE month = :month AND year = :year AND tagID = :tagId
    """)
    suspend fun markForSync(month: Int, year: Int, tagId: Int, operation: String, updatedAt: Long)

    @Query("DELETE FROM targets WHERE createdAt < :cutoffTime")
    suspend fun deleteOldTargets(cutoffTime: Long)
}
